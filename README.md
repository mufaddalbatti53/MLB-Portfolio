# MLB Portfolio — Production Repository

MLB is Mufaddal Battiwala's full-stack developer portfolio. The production topology is intentionally split: the static HTML/CSS/ES-module frontend deploys from `frontend/` to GitHub Pages, the Java 21 Spring Boot API deploys from `backend/` as a Docker service on Render, and MongoDB Atlas stores projects, blog metadata, contact messages, visitor feedback, throttling counters, and revocable admin sessions.

## Production URLs

- Frontend: `https://mufaddalbatti53.github.io/Mufaddal-Portfolio/`
- Backend: the exact `https://<service-name>.onrender.com` origin assigned by Render
- Health monitor: `https://<service-name>.onrender.com/actuator/health`
- Admin UI: `https://mufaddalbatti53.github.io/Mufaddal-Portfolio/admin.html`

Do not use `https://github.io` or `https://onrender.com` as application origins. CORS must contain only origins such as `https://mufaddalbatti53.github.io`; an origin never includes `/Mufaddal-Portfolio/`.

## Architecture

```text
GitHub Pages
  frontend/*.html + css + ES modules + assets
       |
       | HTTPS JSON + Authorization: Bearer <JWT> for admin calls
       v
Render Docker service
  Spring Boot 3.5.16 / Java 21
  Controller -> Service -> Repository
       |
       v
MongoDB Atlas
  projects
  experiences
  blog_posts
  contact_messages
  site_feedback
  rate_limit_counters
  admin_sessions
  portfolio_profile
  bootstrap_state
```

The public browser code never renders API content through HTML-string injection. Search highlighting, project cards, case studies, profile content, contact status, feedback status, and admin data are built with `textContent`, `createTextNode()`, `createElement()`, and `replaceChildren()`.

## Repository layout

```text
frontend/                         GitHub Pages application
  assets/                        logo, favicons, Open Graph image, portrait
  css/                           design tokens, public styles, admin styles
  js/                            public API/UI/theme/search/feedback/admin modules
  index.html                     portfolio home
  admin.html                     owner dashboard, noindex
  404.html                       custom error page
  thank-you.html                 contact receipt page
  privacy.html                   privacy notice
  terms.html                     terms
  robots.txt                     crawler hints for the project path
  sitemap.xml                    indexable project-site URLs
backend/                          Spring Boot API
  Dockerfile                     Render production image
  pom.xml                        Java 21 dependencies
  src/main/...                   application source
  src/test/...                   unit/security/controller tests
.github/workflows/
  ci.yml                         backend + frontend release gates
  deploy-pages.yml               GitHub Pages deployment
scripts/
  build_site.py                  static-site build validation
  validate_source.py             repository-wide release audit
deploy/                          optional same-host Docker/Nginx/Caddy deployment
compose*.yaml                    optional local/self-hosted stack
donebyme.md                      exact owner actions before launch
VALIDATION.md                    latest audit evidence and runtime limits
VERIFICATION_MATRIX.md           every shipping file enumerated
```

## Required production environment variables

These six values are mandatory on the Render backend:

| Variable | Required value |
|---|---|
| `MONGODB_URI` | Complete authenticated MongoDB Atlas connection URI for the portfolio database |
| `JWT_SECRET` | At least 32 random bytes represented as a strong string; `openssl rand -hex 48` is recommended |
| `ADMIN_USERNAME` | Private admin login name, 3–64 characters matching `[A-Za-z0-9_.-]` |
| `ADMIN_PASSWORD_HASH` | BCrypt hash with cost 12–16; never the plaintext password |
| `RATE_LIMIT_HMAC_SECRET` | Strong HMAC secret; `openssl rand -hex 32` is recommended |
| `CORS_ALLOWED_ORIGINS` | `https://mufaddalbatti53.github.io` |

Render should additionally set:

```text
SPRING_PROFILES_ACTIVE=production
SEED_INITIAL_DATA=true
MAIL_ENABLED=false
```

Optional mail settings are `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `MAIL_FROM`, and `CONTACT_NOTIFY_EMAIL`. Optional operational settings include `JWT_TTL_MINUTES`, `CONTACT_RETENTION_DAYS`, `CONTACT_MAX_PER_HOUR`, `FEEDBACK_RETENTION_DAYS`, `MAIL_POLL_INTERVAL_MS`, and `TRUSTED_PROXY_CIDRS`.

## Generate production secrets

Generate the two random secrets locally:

```bash
openssl rand -hex 48
openssl rand -hex 32
```

Use the first output for `JWT_SECRET` and the second for `RATE_LIMIT_HMAC_SECRET`.

Generate a BCrypt cost-12 password hash without placing the password in shell history:

```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 admin
```

The command prompts for a password and prints `admin:<hash>`. Copy only the value after the first colon into `ADMIN_PASSWORD_HASH`.

## MongoDB Atlas setup

1. Create an Atlas project and a MongoDB 7-compatible cluster.
2. Create a dedicated database user for this portfolio with a strong generated password. Do not reuse an Atlas account password.
3. In Atlas Network Access, add the outbound IP ranges used by the selected Render region. Avoid a world-open network rule for the final deployment when restricted ranges are available.
4. Open **Connect → Drivers**, choose Java, and copy the `mongodb+srv://` URI.
5. URL-encode reserved characters in the database password and choose a database name such as `mlb_portfolio` in the URI.
6. Save the complete result as Render's secret `MONGODB_URI`.
7. Do not commit the URI to Git, GitHub Actions, HTML, JavaScript, or this repository.

The application creates its required MongoDB indexes at startup through `MongoIndexConfig`; Spring Data automatic index creation is intentionally disabled. The `experiences` collection is seeded with the verified Web Graphics Studio internship only when the collection is empty.

## Render backend setup

1. Push this repository to GitHub.
2. In Render, create a **Web Service** connected to the repository.
3. Select the Docker runtime.
4. Set the Dockerfile path to `backend/Dockerfile` and the Docker build context to `backend` if Render asks for both fields.
5. Add the six required environment variables from the table above.
6. Add `SPRING_PROFILES_ACTIVE=production` and `SEED_INITIAL_DATA=true`.
7. Deploy the service.
8. Copy the exact Render service origin after the first successful deployment. It must look like `https://<service-name>.onrender.com` and must not contain an additional path.
9. Open `<render-origin>/actuator/health`. A healthy production response is intentionally minimal:

```json
{"status":"UP"}
```

The Docker image uses Maven 3.9.9 with Temurin 21 in the build stage and a Temurin 21 Alpine JRE in the runtime stage. The runtime process is non-root and starts Java with `-XX:+UseSerialGC -Xms128m -Xmx350m`; Spring binds to Render's `PORT` environment variable through `server.port=${PORT:8080}`.

## GitHub Pages setup

The project is designed for the repository path `/Mufaddal-Portfolio/`; navigational links, styles, scripts, manifest icons, SVG logo, and portrait use project-relative URLs.

1. Open the GitHub repository **Settings → Secrets and variables → Actions → Variables**.
2. Create the repository variable `MLB_API_ORIGIN` with the exact Render origin copied in the previous section.
3. Open **Settings → Pages** and choose **GitHub Actions** as the source.
4. Push a change under `frontend/**` to `main`, or manually run **Deploy frontend to GitHub Pages**.
5. The workflow copies `frontend/` into the Pages artifact, injects the exact Render API origin into the `mlb-api-origin` meta tag, replaces canonical project URLs using GitHub Pages metadata, and narrows `connect-src` to that exact Render origin.
6. Open `https://mufaddalbatti53.github.io/Mufaddal-Portfolio/` and verify the portrait, theme switch, search, feedback dialog, case-study dialog, contact form, and admin page.

`robots.txt` is shipped inside the project path. GitHub Pages project sites do not control the host-root `robots.txt`, so sensitive pages also use HTML `noindex`; crawler directives never replace authentication.

## Uptime monitoring

Create an HTTP/HTTPS monitor against:

```text
https://<service-name>.onrender.com/actuator/health
```

Use a normal `GET`. The API permits this exact Actuator endpoint anonymously and disables all other management endpoints through both Actuator configuration and Spring Security.

## Admin authentication

The admin dashboard uses a short-lived JWT returned by `POST /api/v1/auth/login`. The browser stores it only in `sessionStorage` and sends it explicitly through the `Authorization: Bearer <token>` header, which avoids cross-site cookie problems between GitHub Pages and Render.

Spring Security validates the JWT signature, issuer, expiration, role, and Mongo-backed session record. Logout revokes the session server-side, and login attempts are throttled by both IP and account-derived HMAC buckets.

## Public API

```text
GET  /api/v1/profile
GET  /api/v1/projects?limit=6
GET  /api/v1/projects/{slug}
GET  /api/v1/experiences
GET  /api/v1/blog?limit=3
GET  /api/v1/blog/{slug}
POST /api/v1/contact
POST /api/v1/feedback
GET  /actuator/health
```

## Authenticated admin API

```text
POST   /api/v1/auth/login
GET    /api/v1/auth/me
POST   /api/v1/auth/logout

GET    /api/v1/admin/dashboard
GET    /api/v1/admin/projects
GET    /api/v1/admin/projects/{id}
POST   /api/v1/admin/projects
PUT    /api/v1/admin/projects/{id}
PATCH  /api/v1/admin/projects/{id}
PATCH  /api/v1/admin/projects/order
DELETE /api/v1/admin/projects/{id}

GET    /api/v1/admin/experiences
GET    /api/v1/admin/experiences/{id}
POST   /api/v1/admin/experiences
PUT    /api/v1/admin/experiences/{id}
DELETE /api/v1/admin/experiences/{id}

GET    /api/v1/admin/blog
GET    /api/v1/admin/blog/{id}
POST   /api/v1/admin/blog
PUT    /api/v1/admin/blog/{id}
PATCH  /api/v1/admin/blog/{id}
DELETE /api/v1/admin/blog/{id}

GET    /api/v1/admin/messages
PATCH  /api/v1/admin/messages/{id}
POST   /api/v1/admin/messages/{id}/retry
DELETE /api/v1/admin/messages/{id}

GET    /api/v1/admin/feedback
DELETE /api/v1/admin/feedback/{id}

GET    /api/v1/admin/profile
PUT    /api/v1/admin/profile
```

## Local development

Prerequisites: Java 21, Maven 3.9+, Python 3.11+ and access to a MongoDB instance.

Start a local static server:

```bash
python3 -m http.server 5500 --directory frontend
```

In another shell, export secure development values and start Spring Boot:

```bash
export MONGODB_URI='mongodb://localhost:27017/mlb_portfolio'
export ADMIN_USERNAME='local-admin'
export ADMIN_PASSWORD_HASH='<your BCrypt hash>'
export JWT_SECRET='<your generated JWT secret>'
export RATE_LIMIT_HMAC_SECRET='<your generated HMAC secret>'
export CORS_ALLOWED_ORIGINS='http://localhost:5500,http://127.0.0.1:5500'
export SEED_INITIAL_DATA='true'
cd backend
./mvnw spring-boot:run
```

The repository `mvnw` intentionally delegates to an already installed Maven 3.9+ executable rather than downloading build tools at runtime.

## Tests and release checks

Run the deterministic repository audit:

```bash
python3 scripts/validate_source.py
```

Run the static builder tests:

```bash
python3 -m unittest tests/test_build_site.py -v
```

Run Java tests:

```bash
cd backend
./mvnw -B test
```

The `main` branch CI performs Java 21 tests against a MongoDB 7 service, HTML validation, the repository source audit, unsafe DOM API checks, inline event-handler checks, and crawler-file checks. The Pages workflow deploys only after its own production-origin validation succeeds.

## Security release properties

- Raw MongoDB documents are not public API responses.
- Public and admin mutations use validated Java record DTOs.
- Public strings are sanitized and URL fields are protocol-validated server-side.
- Frontend API data is rendered as text/DOM nodes, not executable HTML strings.
- CORS rejects wildcard origins and permits only configured exact origins.
- Admin authentication is stateless Bearer JWT authentication with server-side revocation.
- Contact, feedback, and login throttling use HMAC-derived identifiers stored in TTL-indexed MongoDB counters.
- `/actuator/health` is public and detail-free; other Actuator endpoints are disabled/denied.
- The Pages CSP allows only same-origin scripts/styles and restricts connections to the API; the deploy workflow narrows the Render connection source to the exact deployed service.
- The admin page uses `noindex,nofollow,noarchive`; authorization remains entirely server-side.

## Seeded portfolio content

Initial data includes the verified public profile for Mufaddal Battiwala, education at Netaji Subhash Engineering College in Kolkata, and recruiter-facing case studies for:

- MLB Full-Stack Portfolio & Custom CMS
- Complisca Prototype
- WGS Order Processing

A versioned bootstrap migration also replaces legacy generic owner GitHub, LinkedIn, and site URLs with the current verified profile URLs without overwriting unrelated later admin edits.

## Before announcing the site

Follow every step in [`donebyme.md`](./donebyme.md). Do not publish until Atlas, Render, Pages, health monitoring, contact/feedback submission, and admin login have all been exercised from the actual production origins.

> **Engineering note:** This README treats deployment identity, secrets, API origins, and runtime verification as explicit operator responsibilities rather than hiding them inside source code. The split-stack design keeps the static frontend independently deployable while preserving authenticated administrative control and server-side security boundaries.
