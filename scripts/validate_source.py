#!/usr/bin/env python3
from __future__ import annotations

from html.parser import HTMLParser
import json
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
PAGES = ["index.html", "404.html", "thank-you.html", "privacy.html", "terms.html", "admin.html"]
PUBLIC_PAGES = PAGES[:-1]
SOURCE_SITE = "https://mufaddalbatti53.github.io/Mufaddal-Portfolio"
TEXT_SUFFIXES = {
    ".html", ".css", ".js", ".java", ".xml", ".yml", ".yaml", ".md", ".txt",
    ".py", ".svg", ".properties", ".example", ".gitignore", ""
}
BINARY_SUFFIXES = {".png", ".jpg", ".jpeg", ".ico"}


class PageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.in_title = False
        self.title_parts: list[str] = []
        self.metas: list[dict[str, str | None]] = []
        self.images: list[dict[str, str | None]] = []
        self.scripts: list[dict[str, str | None]] = []
        self.links: list[dict[str, str | None]] = []
        self.anchors: list[dict[str, str | None]] = []
        self.forms: list[dict[str, str | None]] = []
        self.main_count = 0
        self.inline_handlers: list[tuple[str, str]] = []

    def handle_starttag(self, tag: str, attrs) -> None:
        values = dict(attrs)
        if tag == "title":
            self.in_title = True
        elif tag == "meta":
            self.metas.append(values)
        elif tag == "img":
            self.images.append(values)
        elif tag == "script":
            self.scripts.append(values)
        elif tag == "link":
            self.links.append(values)
        elif tag == "a":
            self.anchors.append(values)
        elif tag == "form":
            self.forms.append(values)
        elif tag == "main":
            self.main_count += 1
        for key in values:
            if key and key.lower().startswith("on"):
                self.inline_handlers.append((tag, key))

    def handle_endtag(self, tag: str) -> None:
        if tag == "title":
            self.in_title = False

    def handle_data(self, data: str) -> None:
        if self.in_title:
            self.title_parts.append(data)

    @property
    def title(self) -> str:
        return "".join(self.title_parts).strip()

    def meta(self, *, name: str | None = None, prop: str | None = None) -> str:
        for item in self.metas:
            if name is not None and item.get("name") == name:
                return (item.get("content") or "").strip()
            if prop is not None and item.get("property") == prop:
                return (item.get("content") or "").strip()
        return ""


def fail(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def local_attribute_values(parser: PageParser):
    for item in parser.links:
        if item.get("href"):
            yield "href", item["href"]
    for item in parser.scripts:
        if item.get("src"):
            yield "src", item["src"]
    for item in parser.images:
        if item.get("src"):
            yield "src", item["src"]
    for item in parser.anchors:
        if item.get("href"):
            yield "href", item["href"]
    for item in parser.forms:
        if item.get("action"):
            yield "action", item["action"]


def is_external(value: str) -> bool:
    return value.startswith(("https://", "http://", "mailto:", "tel:", "data:"))


def local_file_for(page: Path, value: str) -> Path | None:
    if value.startswith(("#", "mailto:", "tel:", "data:")) or is_external(value):
        return None
    clean = value.split("#", 1)[0].split("?", 1)[0]
    if not clean:
        return None
    return (page.parent / clean).resolve()


def check_html() -> dict[str, str]:
    seen_titles: set[str] = set()
    results: dict[str, str] = {}
    for name in PAGES:
        path = FRONTEND / name
        document = read(path)
        parser = PageParser()
        parser.feed(document)

        fail(parser.title and parser.title not in seen_titles, f"{name}: title missing or duplicated")
        seen_titles.add(parser.title)
        fail(bool(parser.meta(name="description")), f"{name}: meta description missing")
        fail(parser.main_count == 1, f"{name}: expected exactly one main landmark")
        fail(not parser.inline_handlers, f"{name}: inline event handler found")
        fail(all(img.get("alt") is not None for img in parser.images), f"{name}: image missing alt")
        fail(all(script.get("src") for script in parser.scripts), f"{name}: inline script found")
        fail(any((script.get("src") or "").endswith("./js/theme-init.js") for script in parser.scripts),
             f"{name}: theme-init.js missing")
        fail('data-theme-toggle' in document, f"{name}: theme toggle missing")
        fail('./assets/logo.svg' in document, f"{name}: standalone logo missing")
        csp = next(((meta.get("content") or "").strip() for meta in parser.metas
                    if (meta.get("http-equiv") or "").lower() == "content-security-policy"), "")
        fail(bool(csp), f"{name}: static CSP missing")
        for directive in ["script-src 'self'", "style-src 'self'", "img-src 'self'", "object-src 'none'", "connect-src"]:
            fail(directive in csp, f"{name}: CSP missing {directive}")
        for prop in ["og:title", "og:description", "og:url", "og:image"]:
            fail(bool(parser.meta(prop=prop)), f"{name}: {prop} missing")
        for key in ["twitter:card", "twitter:title", "twitter:description", "twitter:image"]:
            fail(bool(parser.meta(name=key)), f"{name}: {key} missing")

        for attr, value in local_attribute_values(parser):
            fail(not (value.startswith("/") and not value.startswith("//")),
                 f"{name}: root-leading {attr} breaks GitHub Pages project path: {value}")
            candidate = local_file_for(path, value)
            if candidate is not None:
                fail(candidate.exists(), f"{name}: broken local reference {value}")

        if name in PUBLIC_PAGES:
            fail('data-open-search' in document and 'id="site-search"' in document, f"{name}: search UI missing")
            fail('data-open-feedback' in document and 'id="site-feedback"' in document, f"{name}: feedback UI missing")
            fail('./js/main.js' in document, f"{name}: public initializer missing")
        else:
            robots = parser.meta(name="robots").lower()
            fail("noindex" in robots and "nofollow" in robots, "admin.html: noindex/nofollow missing")
            fail('./js/admin.js' in document, "admin.html: admin module missing")

        results[name] = "pass"

    index = read(FRONTEND / "index.html")
    required_identity = [
        "Mufaddal Battiwala",
        "Netaji Subhash Engineering College (NSEC), Kolkata",
        "mufaddal.techpro@gmail.com",
        "https://github.com/mufaddalbatti53",
        "https://www.linkedin.com/in/mufaddal-tech/",
    ]
    for value in required_identity:
        fail(value in index, f"index.html: missing verified identity value {value}")
    for marker in ['id="project-case-study"', './assets/hero-portrait.jpg', 'fetchpriority="high"',
                   'width="420"', 'height="420"', 'View Projects', 'Get In Touch',
                   'id="owner-tagline"', 'id="experience"', 'id="experience-list"']:
        fail(marker in index, f"index.html: hero/content marker missing: {marker}")
    for marker in ['name="name" type="text" autocomplete="name" maxlength="100"',
                   'name="email" type="email" autocomplete="email" maxlength="254"',
                   'name="subject" type="text" maxlength="150"',
                   'name="message" maxlength="5000"',
                   'name="website" type="text" maxlength="200"']:
        fail(marker in index, f"index.html: contact validation mismatch: {marker}")
    for name in PUBLIC_PAGES:
        document = read(FRONTEND / name)
        fail('name="comment" maxlength="500"' in document, f"{name}: feedback comment limit mismatch")
        fail('name="website" maxlength="200"' in document, f"{name}: feedback honeypot limit mismatch")
    return results


def check_frontend_scripts() -> None:
    banned = re.compile(r"\.innerHTML\b|\.outerHTML\b|insertAdjacentHTML\b|document\.write\b|(^|[^A-Za-z0-9_])eval\s*\(", re.M)
    for js in sorted((FRONTEND / "js").glob("*.js")):
        source = read(js)
        fail(not banned.search(source), f"{js.relative_to(ROOT)}: unsafe JavaScript API")
        for import_path in re.findall(r"\bfrom\s*['\"](\.[^'\"]+)['\"]", source):
            fail((js.parent / import_path).exists(), f"{js.relative_to(ROOT)}: missing import {import_path}")

    theme_init = read(FRONTEND / "js/theme-init.js")
    theme = read(FRONTEND / "js/theme.js")
    fail("mlb-theme" in theme_init and "prefers-color-scheme" in theme_init, "theme-init.js: persistence/system preference missing")
    fail("dusk" in theme_init and "dusk" in theme, "theme: dusk mode missing")
    search = read(FRONTEND / "js/search.js")
    fail("event.ctrlKey" in search and "event.metaKey" in search and "event.key.toLowerCase() === 'k'" in search,
         "search.js: Ctrl/Cmd+K binding missing")
    fail("showModal" in search, "search.js: accessible dialog integration missing")
    ui = read(FRONTEND / "js/ui.js")
    fail("project-case-study" in ui and "history.pushState" in ui and "document.createElement" in ui,
         "ui.js: deep-linkable safe case study dialog missing")
    fail("renderExperiences" in ui and "experience-timeline" in ui, "ui.js: experience renderer missing")
    fail("getExperiences" in search and "Experience" in search and "#experience" in search,
         "search.js: experience search integration missing")

    html_text = "\n".join(read(FRONTEND / name) for name in PAGES)
    selector_sources = [FRONTEND / "js/main.js", FRONTEND / "js/ui.js", FRONTEND / "js/feedback.js"]
    selector_ids = set()
    selector_attrs = set()
    for path in selector_sources:
        source = read(path)
        selector_ids.update(re.findall(r"getElementById\(['\"]([^'\"]+)['\"]\)", source))
        selector_attrs.update(re.findall(r"\[([^\]]*data-[a-z0-9-]+[^\]]*)\]", source, flags=re.I))
    for selector_id in sorted(selector_ids):
        if "${" in selector_id:
            continue
        fail(f'id="{selector_id}"' in html_text, f"frontend selectors: missing #{selector_id}")
    for selector in sorted(selector_attrs):
        attribute = re.search(r"data-[a-z0-9-]+", selector, flags=re.I)
        if attribute:
            fail(attribute.group(0) in html_text, f"frontend selectors: missing [{attribute.group(0)}]")

    all_js = "\n".join(read(path) for path in sorted((FRONTEND / "js").glob("*.js")))
    all_css = "\n".join(read(path) for path in sorted((FRONTEND / "css").glob("*.css")))
    dynamic_classes = set()
    for pattern in [
        r"(?:create|element)\([^,]+,\s*['\"]([^'\"]+)['\"]",
        r"className\s*=\s*['\"]([^'\"]+)['\"]",
        r"classList\.(?:add|remove|toggle)\(\s*['\"]([^'\"]+)['\"]",
    ]:
        for group in re.findall(pattern, all_js):
            dynamic_classes.update(value for value in group.split() if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_-]*", value))
    for class_name in sorted(dynamic_classes):
        fail(re.search(r"\." + re.escape(class_name) + r"(?![A-Za-z0-9_-])", all_css) is not None,
             f"frontend CSS: dynamic class .{class_name} has no selector")

    admin = read(FRONTEND / "js/admin.js")
    admin_html = read(FRONTEND / "admin.html")
    for selector_id in sorted(set(re.findall(r"byId\(['\"]([^'\"]+)['\"]\)", admin))):
        fail(f'id="{selector_id}"' in admin_html, f"admin selectors: missing #{selector_id}")
    for tab in ["dashboard", "projects", "experiences", "blog", "messages", "feedback", "profile"]:
        fail(f'data-tab="{tab}"' in admin_html, f"admin.html: missing tab {tab}")


def check_contracts() -> None:
    api = read(FRONTEND / "js/api.js")
    admin_api = read(FRONTEND / "js/admin-api.js")
    controllers = "\n".join(read(path) for path in sorted((ROOT / "backend/src/main/java/com/mlb/portfolio/controller").glob("*.java")))

    public_routes = [
        ("GET", "/api/v1/profile", "getPublicProfile"),
        ("GET", "/api/v1/projects", "getProjects"),
        ("GET", "/api/v1/projects/{slug}", "getProject"),
        ("GET", "/api/v1/experiences", "getExperiences"),
        ("GET", "/api/v1/blog", "getBlogPosts"),
        ("POST", "/api/v1/feedback", "sendFeedback"),
        ("POST", "/api/v1/contact", "sendContact"),
    ]
    for _, path, symbol in public_routes:
        fail(symbol in api, f"api.js: {symbol} missing")
        base = path.replace("/{slug}", "")
        fail(base in controllers, f"controllers: route base {base} missing")

    public_snippets = [
        "getPublicProfile = () => request('/profile')",
        "getProjects = (limit = 6) => request(`/projects?limit=${encodeURIComponent(limit)}`)",
        "getProject = (slug) => request(`/projects/${encodeURIComponent(slug)}`)",
        "getBlogPosts = (limit = 3) => request(`/blog?limit=${encodeURIComponent(limit)}`)",
        "getExperiences = () => request('/experiences')",
        "sendFeedback = (data) => request('/feedback', { method: 'POST'",
        "sendContact = (data) => request('/contact', { method: 'POST'",
    ]
    for snippet in public_snippets:
        fail(snippet in api, f"api.js: contract mismatch: {snippet}")

    admin_snippets = [
        "send('/auth/login',",
        "method: 'POST'",
        "currentUser = () => send('/auth/me')",
        "send('/auth/logout', { method: 'POST' })",
        "getDashboard = () => send('/admin/dashboard')",
        "send(`/admin/experiences?page=${encodeURIComponent(page)}&size=20`)",
        "send('/admin/experiences', { method: 'POST'",
        "send(`/admin/experiences/${encodeURIComponent(id)}`, { method: 'PUT'",
        "send(`/admin/experiences/${encodeURIComponent(id)}`, { method: 'DELETE'",
        "send(`/admin/${resource}?page=${encodeURIComponent(page)}&size=20`)",
        "send(`/admin/${resource}`, { method: 'POST'",
        "send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'PUT'",
        "send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'PATCH'",
        "send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'DELETE'",
        "send(`/admin/messages/${encodeURIComponent(id)}/retry`, { method: 'POST'",
        "getProfile = () => send('/admin/profile')",
        "saveProfile = (body) => send('/admin/profile', { method: 'PUT'",
    ]
    for snippet in admin_snippets:
        fail(snippet in admin_api, f"admin-api.js: contract mismatch: {snippet}")

    admin_symbols = [
        "login", "currentUser", "logout", "getDashboard", "getAdminList", "createAdminItem",
        "putAdminItem", "patchAdminItem", "deleteAdminItem", "retryNotification", "getProfile", "saveProfile",
        "getAdminExperiences", "createExperience", "updateExperience", "deleteExperience"
    ]
    for symbol in admin_symbols:
        fail(re.search(rf"\b{re.escape(symbol)}\b", admin_api) is not None, f"admin-api.js: {symbol} missing")

    required_mappings = [
        '@RequestMapping("/api/v1/auth")', '@PostMapping("/login")', '@GetMapping("/me")', '@PostMapping("/logout")',
        '@RequestMapping("/api/v1/admin")', '@GetMapping("/dashboard")', '@GetMapping("/projects")',
        '@PostMapping("/projects")', '@PutMapping("/projects/{id}")', '@PatchMapping("/projects/{id}")',
        '@DeleteMapping("/projects/{id}")', '@GetMapping("/blog")', '@PostMapping("/blog")',
        '@PutMapping("/blog/{id}")', '@PatchMapping("/blog/{id}")', '@DeleteMapping("/blog/{id}")',
        '@GetMapping("/messages")', '@PatchMapping("/messages/{id}")', '@PostMapping("/messages/{id}/retry")',
        '@DeleteMapping("/messages/{id}")', '@GetMapping("/profile")', '@PutMapping("/profile")',
        '@RequestMapping("/api/v1/admin/feedback")', '@DeleteMapping("/{id:[a-fA-F0-9]{24}}")',
        '@RequestMapping("/api/v1/experiences")',
        '@RequestMapping("/api/v1/admin/experiences")', '@GetMapping("/{id}")',
        '@PutMapping("/{id}")', '@DeleteMapping("/{id}")'
    ]
    for mapping in required_mappings:
        fail(mapping in controllers, f"controller mapping missing: {mapping}")

    dto_expectations = {
        "ContactRequest.java": ["name", "email", "subject", "message", "website", "privacyAccepted"],
        "FeedbackRequest.java": ["rating", "category", "comment", "website"],
        "LoginRequest.java": ["username", "password"],
        "ProjectResponse.java": ["slug", "title", "summary", "description", "problemStatement", "architectureOverview", "architectureFlow", "keyMetrics", "technologies", "githubUrl", "liveUrl", "imageUrl", "imageAlt", "featured", "createdAt"],
        "BlogPostResponse.java": ["slug", "title", "excerpt", "articleUrl", "tags", "coverImageUrl", "coverImageAlt", "publishedAt"],
        "AdminProjectRequest.java": ["slug", "title", "summary", "description", "problemStatement", "architectureOverview", "architectureFlow", "keyMetrics", "technologies", "githubUrl", "liveUrl", "imageUrl", "imageAlt", "featured", "published", "sortOrder"],
        "AdminBlogRequest.java": ["slug", "title", "excerpt", "articleUrl", "tags", "coverImageUrl", "coverImageAlt", "published", "publishedAt"],
        "ExperienceRequest.java": ["role", "company", "employmentType", "workMode", "location", "startDate", "endDate", "current", "summary", "highlights", "technologies", "sortOrder", "published"],
        "ExperienceResponse.java": ["role", "company", "employmentType", "workMode", "location", "startDate", "endDate", "current", "summary", "highlights", "technologies"],
        "AdminExperienceResponse.java": ["id", "version", "role", "company", "employmentType", "workMode", "location", "startDate", "endDate", "current", "summary", "highlights", "technologies", "sortOrder", "published", "updatedAt"],
    }
    dto_dir = ROOT / "backend/src/main/java/com/mlb/portfolio/dto"
    for filename, fields in dto_expectations.items():
        source = read(dto_dir / filename)
        for field in fields:
            fail(re.search(rf"\b{re.escape(field)}\b", source) is not None, f"{filename}: field {field} missing")

    experience_request = read(dto_dir / "ExperienceRequest.java")
    for marker in [
        "@NotBlank @Size(max = 120) String role",
        "@NotBlank @Size(max = 120) String company",
        "@NotBlank @Size(max = 80) String employmentType",
        "@NotBlank @Size(max = 80) String workMode",
        "@NotBlank @Size(max = 120) String location",
        "@NotNull LocalDate startDate",
        "@NotBlank @Size(max = 1500) String summary",
        "@NotNull @Size(max = 8) List<@NotBlank @Size(max = 300) String> highlights",
        "@NotNull @Size(max = 15) List<@NotBlank @Size(max = 60) String> technologies",
        "@Min(0) @Max(100000) int sortOrder",
    ]:
        fail(marker in experience_request, f"ExperienceRequest.java: validation mismatch: {marker}")

    main = read(FRONTEND / "js/main.js")
    feedback = read(FRONTEND / "js/feedback.js")
    admin = read(FRONTEND / "js/admin.js")
    for field in dto_expectations["ContactRequest.java"]:
        fail(field in main, f"main.js: contact field {field} missing")
    for field in dto_expectations["FeedbackRequest.java"]:
        fail(field in feedback, f"feedback.js: field {field} missing")
    for field in dto_expectations["AdminProjectRequest.java"]:
        fail(field in admin, f"admin.js: project field {field} missing")
    for field in dto_expectations["AdminBlogRequest.java"]:
        fail(field in admin, f"admin.js: blog field {field} missing")
    for field in dto_expectations["ExperienceRequest.java"]:
        fail(field in admin, f"admin.js: experience field {field} missing")
    fail("getExperiences" in main and "renderExperiences" in main, "main.js: experience API/UI contract missing")
    fail("inboxStatus" in admin, "admin.js: message patch field mismatch")

    experience_controller = read(ROOT / "backend/src/main/java/com/mlb/portfolio/controller/ExperienceController.java")
    admin_experience = read(ROOT / "backend/src/main/java/com/mlb/portfolio/controller/AdminExperienceController.java")
    for marker in ['@RequestMapping("/api/v1/experiences")', "@GetMapping"]:
        fail(marker in experience_controller, f"ExperienceController.java: missing {marker}")
    for marker in ['@RequestMapping("/api/v1/admin/experiences")', "@GetMapping", '@GetMapping("/{id}")',
                   "@PostMapping", '@PutMapping("/{id}")', '@DeleteMapping("/{id}")']:
        fail(marker in admin_experience, f"AdminExperienceController.java: missing {marker}")


def check_security() -> None:
    security = read(ROOT / "backend/src/main/java/com/mlb/portfolio/config/SecurityConfig.java")
    cors = read(ROOT / "backend/src/main/java/com/mlb/portfolio/config/CorsConfig.java")
    app = read(ROOT / "backend/src/main/resources/application.yml")
    jwt_filter = read(ROOT / "backend/src/main/java/com/mlb/portfolio/config/AdminJwtFilter.java")
    auth = read(ROOT / "backend/src/main/java/com/mlb/portfolio/service/AdminAuthService.java")
    contact = read(ROOT / "backend/src/main/java/com/mlb/portfolio/service/ContactService.java")
    feedback = read(ROOT / "backend/src/main/java/com/mlb/portfolio/service/FeedbackService.java")
    rate = read(ROOT / "backend/src/main/java/com/mlb/portfolio/model/RateLimitCounter.java")

    for value in ["/actuator/health", '"/api/v1/admin/**"', '"/api/v1/auth/me"', '"/api/v1/auth/logout"',
                  '"/api/v1/experiences"']:
        fail(value in security, f"SecurityConfig.java: missing {value}")
    fail('.hasRole("ADMIN")' in security, "SecurityConfig.java: admin role guard missing")
    fail('requestMatchers("/actuator/**").denyAll()' in security, "SecurityConfig.java: other actuator endpoints not denied")
    fail('.csrf(csrf -> csrf.disable())' in security and "SessionCreationPolicy.STATELESS" in security,
         "SecurityConfig.java: stateless Bearer policy incomplete")
    fail('Authorization' in cors and 'setAllowCredentials(false)' in cors and 'origin.contains("*")' in cors,
         "CorsConfig.java: Bearer header or exact-origin policy missing")
    fail('CORS_ALLOWED_ORIGINS' in app and 'https://mufaddalbatti53.github.io' in app,
         "application.yml: production CORS origin missing")
    fail('show-details: never' in app and 'include: health' in app and 'enabled-by-default: false' in app,
         "application.yml: actuator exposure is too broad")
    fail('Bearer ' in jwt_filter and 'HttpHeaders.AUTHORIZATION' in jwt_filter, "AdminJwtFilter.java: Bearer parsing missing")
    fail('abuse.check("admin-login-ip"' in auth and 'abuse.check("admin-login-account"' in auth,
         "AdminAuthService.java: login brute-force throttling missing")
    fail('rateLimit.check(clientIp)' in contact, "ContactService.java: contact throttle missing")
    fail('abuse.check("feedback",clientIp' in feedback.replace(" ", ""), "FeedbackService.java: feedback throttle missing")
    fail('@Indexed(expireAfter' in rate, "RateLimitCounter.java: TTL index missing")
    fail('RATE_LIMIT_HMAC_SECRET' in app, "application.yml: HMAC secret externalization missing")


def check_assets_and_deployment() -> None:
    assets = FRONTEND / "assets"
    ET.parse(assets / "logo.svg")
    logo = read(assets / "logo.svg")
    for color in ["#3B66F5", "#7A5AF8", "#C84B9E"]:
        fail(color in logo, f"logo.svg: brand gradient color {color} missing")
    ET.parse(assets / "favicon.svg")
    ET.parse(FRONTEND / "sitemap.xml")

    manifest = json.loads(read(FRONTEND / "site.webmanifest"))
    fail(manifest.get("start_url") == "./" and manifest.get("scope") == "./", "site.webmanifest: project-subpath scope invalid")
    for icon in manifest["icons"]:
        src = icon["src"]
        fail(not src.startswith("/"), f"site.webmanifest: root-leading icon {src}")
        fail((FRONTEND / src).resolve().exists(), f"site.webmanifest: missing icon {src}")

    for name, size in [("icon-192.png", (192, 192)), ("icon-512.png", (512, 512)),
                       ("apple-touch-icon.png", (180, 180)), ("og-preview.png", (1200, 630))]:
        header = (assets / name).read_bytes()[:24]
        fail(header[:8] == bytes.fromhex("89504e470d0a1a0a") and struct.unpack(">II", header[16:24]) == size,
             f"{name}: invalid PNG dimensions")
    fail((assets / "favicon.ico").read_bytes()[:4] == b"\x00\x00\x01\x00", "favicon.ico: invalid header")
    fail((assets / "hero-portrait.jpg").stat().st_size > 10_000, "hero-portrait.jpg: missing/empty")

    robots = read(FRONTEND / "robots.txt")
    fail("Disallow: /Mufaddal-Portfolio/admin.html" in robots, "robots.txt: admin path not disallowed")
    fail(f"Sitemap: {SOURCE_SITE}/sitemap.xml" in robots, "robots.txt: sitemap URL mismatch")
    sitemap = read(FRONTEND / "sitemap.xml")
    fail(f"{SOURCE_SITE}/" in sitemap and f"{SOURCE_SITE}/privacy.html" in sitemap, "sitemap.xml: project-site URL mismatch")

    docker = read(ROOT / "backend/Dockerfile")
    for marker in ["maven:3.9.9-eclipse-temurin-21-alpine", "eclipse-temurin:21-jre-alpine", "USER spring",
                   "-XX:+UseSerialGC", "-Xms128m", "-Xmx350m"]:
        fail(marker in docker, f"backend/Dockerfile: missing {marker}")
    fail('port: ${PORT:8080}' in read(ROOT / "backend/src/main/resources/application.yml"), "application.yml: Render PORT binding missing")

    try:
        import yaml  # type: ignore
    except ImportError as exc:
        raise AssertionError("PyYAML is required for workflow validation") from exc
    for workflow in [ROOT / ".github/workflows/ci.yml", ROOT / ".github/workflows/deploy-pages.yml"]:
        yaml.safe_load(read(workflow))
    pages = read(ROOT / ".github/workflows/deploy-pages.yml")
    fail("actions/deploy-pages@v5" in pages and "frontend" in pages and "MLB_API_ORIGIN" in pages,
         "deploy-pages.yml: Pages deployment contract incomplete")
    fail("text.replace('https://*.onrender.com', api_origin)" in pages,
         "deploy-pages.yml: deployed CSP does not narrow to the exact Render origin")


def check_content_and_markers() -> None:
    seed = read(ROOT / "backend/src/main/java/com/mlb/portfolio/config/DataSeeder.java")
    for value in ["Mufaddal Battiwala", "Netaji Subhash Engineering College", "mufaddal.techpro@gmail.com",
                  "mufaddalbatti53", "mufaddal-tech", "Complisca Prototype", "WGS Order Processing",
                  "Software Developer Intern", "Web Graphics Studio", "Remote (Work From Home)",
                  "LocalDate.parse(\"2026-01-01\")", "LocalDate.parse(\"2026-06-30\")"]:
        fail(value in seed or value in read(FRONTEND / "index.html"), f"verified content missing: {value}")

    marker_words = ["TO" + "DO", "FIX" + "ME", "place" + "holder"]
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or any(part in {".git", "__pycache__", "dist"} for part in path.parts):
            continue
        if path.suffix in BINARY_SUFFIXES or path.suffix in {".pyc", ".zip", ".class"}:
            continue
        text = read(path)
        lowered = text.lower()
        for word in marker_words:
            fail(word.lower() not in lowered, f"{path.relative_to(ROOT)}: unfinished marker word found")


def check_environment_sync() -> None:
    app = read(ROOT / "backend/src/main/resources/application.yml")
    compose = read(ROOT / "compose.yaml")
    env_example = read(ROOT / ".env.example")
    readme = read(ROOT / "README.md")
    env_names = sorted(set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)", app)))
    mandatory = {
        "MONGODB_URI", "JWT_SECRET", "ADMIN_USERNAME", "ADMIN_PASSWORD_HASH",
        "RATE_LIMIT_HMAC_SECRET", "CORS_ALLOWED_ORIGINS"
    }
    fail(mandatory.issubset(env_names), "application.yml: mandatory environment variables missing")
    for name in env_names:
        if name == "PORT":
            continue
        fail(name in env_example or name in compose or name in readme, f"environment sync: {name} undocumented")
    for name in ["SPRING_PROFILES_ACTIVE", "SEED_INITIAL_DATA", "MAIL_POLL_INTERVAL_MS"]:
        fail(name in compose, f"compose.yaml: {name} not forwarded")


def check_relative_source_paths() -> None:
    for css in sorted((FRONTEND / "css").glob("*.css")):
        source = read(css)
        for value in re.findall(r"url\(\s*['\"]?([^)'\"]+)", source):
            if value.startswith(("data:", "https://", "http://", "#")):
                continue
            fail(not value.startswith("/"), f"{css.relative_to(ROOT)}: root-leading CSS URL {value}")
    for js in sorted((FRONTEND / "js").glob("*.js")):
        source = read(js)
        fail(not re.search(r"(?:href|src)\s*=\s*['\"]/", source),
             f"{js.relative_to(ROOT)}: root-leading local path")


def check_comment_limits() -> None:
    roots = [ROOT / "backend", ROOT / "frontend", ROOT / ".github", ROOT / "scripts", ROOT / "deploy"]
    candidates = []
    for base in roots:
        if not base.exists():
            continue
        candidates.extend(path for path in base.rglob("*") if path.is_file())
    candidates.extend([ROOT / "compose.yaml", ROOT / ".env.example"])
    for path in sorted(set(candidates)):
        if path.suffix in BINARY_SUFFIXES or path.suffix in {".ico", ".zip", ".class", ".pyc", ".md"}:
            continue
        text = read(path)
        for line_number, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("#!"):
                continue
            bodies = []
            if stripped.startswith("//"):
                bodies.append(stripped[2:])
            if stripped.startswith("#") and path.suffix in {".py", ".yml", ".yaml", ".sh", ".conf", ".example", ""}:
                bodies.append(stripped[1:])
            if "<!--" in stripped and "-->" in stripped:
                bodies.append(stripped.split("<!--", 1)[1].split("-->", 1)[0])
            if stripped.startswith("/*") or stripped.startswith("* "):
                body = stripped.lstrip("/* ").rstrip("*/ ")
                if body:
                    bodies.append(body)
            for body in bodies:
                words = re.findall(r"[A-Za-z0-9_+-]+", body)
                fail(len(words) <= 5, f"{path.relative_to(ROOT)}:{line_number}: comment exceeds five words")


def enumerate_files() -> list[str]:
    records: list[str] = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or any(part in {".git", "__pycache__", "dist"} for part in path.parts):
            continue
        if path.suffix in {".pyc", ".zip", ".class"}:
            continue
        records.append(path.relative_to(ROOT).as_posix())
    return records


def write_matrix(records: list[str]) -> None:
    lines = [
        "# MLB — File-by-file verification matrix",
        "",
        "Generated by `python3 scripts/validate_source.py` after the final source audit. A checkmark means the file was included in the repository scan and all deterministic checks applicable to its type passed; runtime Java/MongoDB/browser behavior is reported separately in `VALIDATION.md`.",
        "",
        "| File | Included in audit | Paths / imports | Security/content checks |",
        "|---|---:|---:|---:|",
    ]
    for rel in records:
        path_check = "[✓]" if rel.startswith(("frontend/", ".github/", "deploy/")) else "[✓] scanned"
        security = "[✓]" if rel.startswith(("frontend/", "backend/", ".github/")) else "[✓] scanned"
        lines.append(f"| `{rel}` | [✓] | {path_check} | {security} |")
    lines.extend([
        "",
        "Runtime-only checks still required on the real deployment: Java/Maven test execution, Atlas connectivity, Render health response, browser console/CSP behavior, and authenticated admin flows from the exact GitHub Pages origin.",
        "",
        "Static checks do not replace deployed runtime verification.",
    ])
    (ROOT / "VERIFICATION_MATRIX.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    check_html()
    check_frontend_scripts()
    check_contracts()
    check_security()
    check_assets_and_deployment()
    check_content_and_markers()
    check_environment_sync()
    check_relative_source_paths()
    check_comment_limits()
    records = enumerate_files()
    write_matrix(records)
    print(f"PASS: audited {len(records)} shipping files across HTML, JS, DTO/controller contracts, security, assets and deployment configuration.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
