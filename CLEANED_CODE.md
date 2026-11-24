# Complete cleaned source

123 current text source/config files are included below. Binary assets are referenced by the repository and are not embedded here.

## `.env.example`

```text
SITE_BASE_URL=https://mufaddalbatti53.github.io/MLB-Portfolio
SITE_HOST=
SITE_DISPLAY_NAME=Mufaddal Battiwala
SITE_EMAIL=mufaddal.techpro@gmail.com
SITE_LOCATION=Kolkata, India
SITE_GITHUB=https://github.com/mufaddalbatti53
SITE_LINKEDIN=https://www.linkedin.com/in/mufaddal-tech/
BUILD_MODE=production
SPRING_PROFILES_ACTIVE=local
SEED_INITIAL_DATA=true

MONGO_ROOT_USER=
MONGO_ROOT_PASSWORD=
MONGODB_URI=

RATE_LIMIT_HMAC_SECRET=
TRUSTED_PROXY_CIDRS=172.29.77.11/32
CORS_ALLOWED_ORIGINS=https://mufaddalbatti53.github.io
CONTACT_RETENTION_DAYS=90
CONTACT_MAX_PER_HOUR=5
FEEDBACK_RETENTION_DAYS=90

ADMIN_USERNAME=
ADMIN_PASSWORD_HASH=
JWT_SECRET=
JWT_TTL_MINUTES=15

MAIL_ENABLED=false
MANAGEMENT_HEALTH_MAIL_ENABLED=false
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM=
CONTACT_NOTIFY_EMAIL=
MAIL_POLL_INTERVAL_MS=30000
WEB_PORT=8080
```

## `.github/workflows/ci.yml`

```yaml
name: MLB Portfolio CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: mlb-ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend-ci:
    name: Backend tests
    runs-on: ubuntu-latest
    services:
      mongo:
        image: mongo:7
        ports:
          - 27017:27017
        options: >-
          --health-cmd "mongosh --quiet --eval 'db.adminCommand({ ping: 1 }).ok' | grep -q 1"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 10
    defaults:
      run:
        working-directory: backend
    env:
      MONGODB_URI: mongodb://localhost:27017/mlb_portfolio_ci
      ADMIN_USERNAME: ci-admin
      ADMIN_PASSWORD_HASH: $2a$12$Wz7zqVHWcQ9CeP9aKyV23uMqmZzQW5P7pc6PZ4RqQzxB2A6iXr9vK
      JWT_SECRET: ci-only-jwt-signing-secret-with-at-least-32-characters
      RATE_LIMIT_HMAC_SECRET: ci-only-hmac-secret-with-at-least-32-characters
      CORS_ALLOWED_ORIGINS: https://mufaddalbatti53.github.io
      TRUSTED_PROXY_CIDRS: 127.0.0.1/32
      SEED_INITIAL_DATA: "false"
      MAIL_ENABLED: "false"
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Set up Temurin JDK 21
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Ensure Maven wrapper is executable
        run: chmod +x mvnw

      - name: Run backend unit, validation, rate-limit and security tests
        run: ./mvnw -B test

  frontend-audit:
    name: Frontend safety and HTML audit
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Set up Node.js
        uses: actions/setup-node@v7
        with:
          node-version: "24"

      - name: Validate HTML5 documents
        run: npx --yes html-validate@11.21.1 "frontend/*.html"

      - name: Enforce XSS-safe DOM policy
        shell: bash
        run: |
          set -euo pipefail
          if grep -R --line-number --include='*.js' -E '\.innerHTML\b|\.outerHTML\b|insertAdjacentHTML\b|document\.write\b|(^|[^A-Za-z0-9_])eval[[:space:]]*\(' frontend/js; then
            echo "Unsafe DOM HTML API detected in frontend JavaScript." >&2
            exit 1
          fi

      - name: Reject inline browser event handlers
        shell: bash
        run: |
          set -euo pipefail
          if grep -R --line-number --include='*.html' -E ' on[a-zA-Z]+[[:space:]]*=' frontend; then
            echo "Inline browser event handler detected in frontend HTML." >&2
            exit 1
          fi

      - name: Install source-audit YAML parser
        run: python3 -m pip install --disable-pip-version-check --no-input PyYAML==6.0.2

      - name: Run repository source audit
        run: python3 scripts/validate_source.py

      - name: Verify SEO discovery files
        shell: bash
        run: |
          set -euo pipefail
          test -s frontend/sitemap.xml
          test -s frontend/robots.txt

  deploy-hook:
    name: Deployment hook gate
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: [backend-ci, frontend-audit]
    runs-on: ubuntu-latest
    steps:
      - name: Deployment hook gate
        run: echo "CI passed. Render can deploy from its GitHub integration."
```

## `.github/workflows/deploy-pages.yml`

```yaml
name: Deploy frontend to GitHub Pages

on:
  push:
    branches: [main]
    paths:
      - 'frontend/**'
      - '.github/workflows/deploy-pages.yml'
  workflow_dispatch:

permissions:
  contents: read
  actions: read
  pages: write
  id-token: write

concurrency:
  group: github-pages
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Configure GitHub Pages
        id: pages
        uses: actions/configure-pages@v6

      - name: Prepare project-site artifact
        env:
          MLB_API_ORIGIN: ${{ vars.MLB_API_ORIGIN }}
          PAGES_BASE_URL: ${{ steps.pages.outputs.base_url }}
        run: |
          set -euo pipefail

          if [ -z "${MLB_API_ORIGIN}" ]; then
            echo "::error::Set the repository variable MLB_API_ORIGIN to the exact Render service origin."
            exit 1
          fi

          case "${MLB_API_ORIGIN}" in
            https://*.onrender.com) ;;
            *)
              echo "::error::MLB_API_ORIGIN must be an exact HTTPS Render origin ending in .onrender.com."
              exit 1
              ;;
          esac

          rm -rf _site
          cp -R frontend _site
          touch _site/.nojekyll

          python3 - <<'PY'
          import base64
          import hashlib
          import html
          import os
          import re
          from pathlib import Path

          site = Path('_site')
          api_origin = os.environ['MLB_API_ORIGIN'].rstrip('/')
          pages_url = os.environ['PAGES_BASE_URL'].rstrip('/')
          source_site = 'https://mufaddalbatti53.github.io/MLB-Portfolio'
          api_meta = '<meta name="mlb-api-origin" content="">'
          deployed_api_meta = f'<meta name="mlb-api-origin" content="{html.escape(api_origin, quote=True)}">'

          def refresh_jsonld_csp(text):
              match = re.search(r'<script type="application/ld\+json" id="seo-structured-data">(.*?)</script>', text, re.S)
              if not match:
                  return text
              digest = base64.b64encode(hashlib.sha256(match.group(1).encode('utf-8')).digest()).decode('ascii')
              token = f"'sha256-{digest}'"
              meta = re.search(r'<meta http-equiv="Content-Security-Policy" content="([^"]+)">', text)
              if not meta:
                  raise SystemExit('Structured data page is missing CSP')
              content = re.sub(r"\s+'sha256-[A-Za-z0-9+/=]+'", '', meta.group(1))
              content = content.replace("script-src 'self'", "script-src 'self' " + token, 1)
              return text[:meta.start(1)] + content + text[meta.end(1):]

          for path in site.glob('*.html'):
              text = path.read_text(encoding='utf-8')
              text = text.replace(source_site, pages_url)
              if api_meta not in text:
                  raise SystemExit(f'Missing API configuration meta tag in {path}')
              text = text.replace(api_meta, deployed_api_meta, 1)
              text = text.replace('https://*.onrender.com', api_origin)
              text = refresh_jsonld_csp(text)
              path.write_text(text, encoding='utf-8')

          for filename in ('robots.txt', 'sitemap.xml'):
              path = site / filename
              text = path.read_text(encoding='utf-8').replace(source_site, pages_url)
              path.write_text(text, encoding='utf-8')
          PY

      - name: Upload GitHub Pages artifact
        uses: actions/upload-pages-artifact@v5
        with:
          path: _site

  deploy:
    runs-on: ubuntu-latest
    needs: build
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v5
```

## `.gitignore`

```text
.env
.env.*
!.env.example
dist/
backend/target/
.idea/
.vscode/
*.log
.DS_Store
__pycache__/
.pytest_cache/
```

## `backend/Dockerfile`

```text
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S spring -G spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/*.jar /app/app.jar

USER spring
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xms128m", "-Xmx350m", "-jar", "/app/app.jar"]
```

## `backend/mvnw`

```text
#!/bin/sh
set -eu

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

printf '%s\n' 'Maven 3.9+ is required to run this repository wrapper.' >&2
exit 127
```

## `backend/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>
    <relativePath/>
  </parent>

  <groupId>com.mlb</groupId>
  <artifactId>mlb-portfolio</artifactId>
  <version>1.1.0</version>
  <name>MLB Portfolio API</name>
  <description>Secure public portfolio, owner-only admin API, and production health monitoring</description>

  <properties>
    <java.version>21</java.version>
    <jjwt.version>0.13.0</jjwt.version>
    <jsoup.version>1.21.2</jsoup.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>

    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.jsoup</groupId>
      <artifactId>jsoup</artifactId>
      <version>${jsoup.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <release>${java.version}</release>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

## `backend/src/main/java/com/mlb/portfolio/MlbPortfolioApplication.java`

```java
package com.mlb.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MlbPortfolioApplication {
    public static void main(String[] args) {
        SpringApplication.run(MlbPortfolioApplication.class, args);
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/config/AdminJwtFilter.java`

```java
package com.mlb.portfolio.config;

import com.mlb.portfolio.service.AdminAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminJwtFilter extends OncePerRequestFilter {
    private final AdminAuthService auth;

    public AdminJwtFilter(AdminAuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/admin/")
            || path.equals("/api/v1/auth/me")
            || path.equals("/api/v1/auth/logout"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
            ? header.substring(7).trim()
            : null;

        if (token != null && !token.isBlank() && auth.validate(token) != null) {
            var principal = new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(principal);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/config/ClientIpResolver.java`

```java
package com.mlb.portfolio.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final List<IpAddressMatcher> trusted;

    public ClientIpResolver(@Value("${mlb.security.trusted-proxy-cidrs:}") String cidrs) {
        this.trusted = Arrays.stream(cidrs.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean trustedProxy = trusted.stream().anyMatch(matcher -> matcher.matches(remote));
        if (trustedProxy) {
            // Trust Nginx real IP
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && realIp.length() <= 45 && !realIp.contains(",")) {
                try {
                    InetAddress addr = InetAddress.getByName(realIp);
                    if (realIp.contains(":") || realIp.matches("[0-9.]+")) return addr.getHostAddress();
                } catch (Exception ignored) { }
            }
        }
        return remote;
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/config/CorsConfig.java`

```java
package com.mlb.portfolio.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${mlb.security.allowed-origins}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .filter(this::isAllowedOriginSyntax)
            .distinct()
            .toList();

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowed);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Content-Type"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private boolean isAllowedOriginSyntax(String origin) {
        if (origin.contains("*")) return false;
        if (origin.startsWith("https://")) return true;
        return origin.startsWith("http://localhost:")
            || origin.startsWith("http://127.0.0.1:");
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/config/DataSeeder.java`

```java
package com.mlb.portfolio.config;

import com.mlb.portfolio.model.BootstrapState;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.model.PortfolioProfile;
import com.mlb.portfolio.model.Project;
import com.mlb.portfolio.repository.BootstrapStateRepository;
import com.mlb.portfolio.repository.ExperienceRepository;
import com.mlb.portfolio.repository.PortfolioProfileRepository;
import com.mlb.portfolio.repository.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public final class DataSeeder implements ApplicationRunner {
    private static final String BASE_SEED_VERSION = "mlb_initial_profile_projects_v1";
    private static final String CASE_STUDY_SEED_VERSION = "mlb_project_case_studies_v2";
    private static final String PROFILE_IDENTITY_SEED_VERSION = "mlb_profile_identity_v3";
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    private static final int MIN_SIGNING_KEY_BYTES = 32;

    private final MongoTemplate mongo;
    private final PortfolioProfileRepository profiles;
    private final ProjectRepository projects;
    private final ExperienceRepository experiences;
    private final BootstrapStateRepository states;
    private final boolean enabled;
    private final String adminUsername;
    private final String adminPasswordHash;
    private final String signingKey;
    private final int jwtTtlMinutes;
    private final Clock clock;

    @Autowired
    public DataSeeder(
            MongoTemplate mongo,
            PortfolioProfileRepository profiles,
            ProjectRepository projects,
            ExperienceRepository experiences,
            BootstrapStateRepository states,
            @Value("${mlb.seed.enabled:true}") boolean enabled,
            @Value("${mlb.security.admin-username}") String adminUsername,
            @Value("${mlb.security.admin-password-hash}") String adminPasswordHash,
            @Value("${mlb.security.jwt-secret}") String signingKey,
            @Value("${mlb.security.jwt-ttl-minutes:15}") int jwtTtlMinutes) {
        this(mongo, profiles, projects, experiences, states, enabled, adminUsername, adminPasswordHash,
            signingKey, jwtTtlMinutes, Clock.systemUTC());
    }

    DataSeeder(
            MongoTemplate mongo,
            PortfolioProfileRepository profiles,
            ProjectRepository projects,
            ExperienceRepository experiences,
            BootstrapStateRepository states,
            boolean enabled,
            String adminUsername,
            String adminPasswordHash,
            String signingKey,
            int jwtTtlMinutes,
            Clock clock) {
        this.mongo = mongo;
        this.profiles = profiles;
        this.projects = projects;
        this.experiences = experiences;
        this.states = states;
        this.enabled = enabled;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
        this.signingKey = signingKey;
        this.jwtTtlMinutes = jwtTtlMinutes;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateSecurityEnvironment();
        backfillLegacyVersions();
        if (!enabled) return;

        Instant now = clock.instant();
        if (!states.existsById(BASE_SEED_VERSION)) {
            if (!profiles.existsById("owner")) {
                profiles.save(initialProfile(now));
            }
            states.save(new BootstrapState(BASE_SEED_VERSION, now));
        }

        if (!states.existsById(CASE_STUDY_SEED_VERSION)) {
            seedCaseStudies(now);
            states.save(new BootstrapState(CASE_STUDY_SEED_VERSION, now));
        }

        if (!states.existsById(PROFILE_IDENTITY_SEED_VERSION)) {
            migrateKnownLegacyProfileIdentity(now);
            states.save(new BootstrapState(PROFILE_IDENTITY_SEED_VERSION, now));
        }

        if (experiences.count() == 0) {
            experiences.save(initialExperience(now));
        }
    }


    private void migrateKnownLegacyProfileIdentity(Instant now) {
        PortfolioProfile profile = profiles.findById("owner").orElseGet(() -> initialProfile(now));
        boolean changed = profile.getId() == null;

        if (profile.getGithubUrl() == null || profile.getGithubUrl().isBlank()
                || "https://github.com".equals(profile.getGithubUrl())) {
            profile.setGithubUrl("https://github.com/mufaddalbatti53");
            changed = true;
        }
        if (profile.getLinkedinUrl() == null || profile.getLinkedinUrl().isBlank()
                || "https://linkedin.com".equals(profile.getLinkedinUrl())
                || "https://www.linkedin.com".equals(profile.getLinkedinUrl())) {
            profile.setLinkedinUrl("https://www.linkedin.com/in/mufaddal-tech/");
            changed = true;
        }
        if (profile.getWebsiteUrl() == null || profile.getWebsiteUrl().isBlank()
                || "https://github.io".equals(profile.getWebsiteUrl())) {
            profile.setWebsiteUrl("https://mufaddalbatti53.github.io/MLB-Portfolio/");
            changed = true;
        }
        if (changed) {
            profile.setUpdatedAt(now);
            profiles.save(profile);
        }
    }

    private void backfillLegacyVersions() {
        Query missingVersion = Query.query(Criteria.where("version").exists(false));
        Update initialVersion = new Update().set("version", 0L);
        mongo.updateMulti(missingVersion, initialVersion, Project.class);
        mongo.updateMulti(missingVersion, initialVersion, com.mlb.portfolio.model.BlogPost.class);
        mongo.updateMulti(missingVersion, initialVersion, Experience.class);
    }

    private void seedCaseStudies(Instant now) {
        upsertCaseStudy(
            "mlb-full-stack-portfolio",
            "MLB Full-Stack Portfolio & Custom CMS",
            "A secure developer portfolio with a custom content-management workflow, recruiter-focused case studies, and production observability.",
            "A full-stack portfolio built without a frontend framework, backed by a protected Spring Boot admin API and MongoDB persistence.",
            "Static developer portfolios lack dynamic content management, real security hardening, and observability.",
            "Vanilla ES6+ frontend on GitHub Pages → Spring Boot 3 on Java 21 via Render → MongoDB Atlas, with HMAC/TTL rate limiting, short-lived Bearer JWT admin authentication, health monitoring, and CI/CD gates.",
            List.of(
                "Vanilla ES6+ browser UI",
                "GitHub Pages HTTPS and browser CSP",
                "Spring Boot 3 REST and admin API",
                "MongoDB Atlas documents and TTL indexes",
                "HMAC rate limiting and revocable Bearer JWT auth",
                "GitHub Actions CI and health monitoring"
            ),
            List.of(
                "Zero Frontend Frameworks",
                "HMAC + TTL Rate Limiting",
                "100% XSS-Safe DOM",
                "Automated CI/CD"
            ),
            List.of("Java 21", "Spring Boot", "MongoDB", "Vanilla JavaScript", "Nginx", "GitHub Actions"),
            "https://github.com/mufaddalbatti53",
            null,
            5,
            now
        );

        upsertCaseStudy(
            "complisca-prototype",
            "Complisca Prototype",
            "An end-to-end compliance workflow prototype that turns uploaded documents into OCR-driven checks, alerts, and an approval path.",
            "A JavaScript-based prototype that connects document upload, OCR extraction, compliance alerts, and officer verification in one workflow.",
            "Manual compliance review can scatter document checks, alerting, and approvals across disconnected steps, making it harder to track what still needs officer verification.",
            "Browser upload workflow → OCR extraction → rule-driven compliance checks → alert generation → approval workflow, with officer verification retained as the final human decision point.",
            List.of(
                "Document upload",
                "OCR text extraction",
                "Compliance rule checks",
                "Alert generation",
                "Approval workflow",
                "Officer verification"
            ),
            List.of(
                "End-to-End Upload → OCR Flow",
                "Automated Compliance Alerts",
                "Officer Verification Workflow"
            ),
            List.of("JavaScript", "Compliance Workflow", "OCR Integration", "Web App"),
            "https://github.com/prototype-complisca",
            null,
            10,
            now
        );

        upsertCaseStudy(
            "wgs-order-processing",
            "WGS Order Processing",
            "A responsive order-management workflow built with HTML, CSS, and JavaScript for a live production website.",
            "A lightweight browser application that organizes order intake and processing without introducing a frontend framework dependency.",
            "Order intake and status handling need a responsive workflow that keeps order information organized across desktop and mobile without relying on a heavy frontend framework.",
            "Responsive HTML/CSS/JavaScript client → order form and validation → order-processing workflow → production web deployment at webgraphicsstudio.in.",
            List.of(
                "Responsive order interface",
                "Client-side validation",
                "Order-processing workflow",
                "Production web deployment"
            ),
            List.of(
                "Live Production Deployment",
                "Responsive Mobile Order Flow",
                "Vanilla JavaScript UI"
            ),
            List.of("HTML", "CSS", "JavaScript", "Order Management System"),
            "https://github.com/WGS-Order-Processing",
            "https://webgraphicsstudio.in",
            20,
            now
        );
    }

    private void upsertCaseStudy(
            String slug,
            String title,
            String summary,
            String description,
            String problemStatement,
            String architectureOverview,
            List<String> architectureFlow,
            List<String> keyMetrics,
            List<String> technologies,
            String githubUrl,
            String liveUrl,
            int defaultOrder,
            Instant now) {
        Project project = projects.findBySlug(slug).orElseGet(Project::new);
        boolean isNew = project.getId() == null;

        project.setSlug(slug);
        project.setTitle(title);
        project.setSummary(summary);
        project.setDescription(description);
        project.setProblemStatement(problemStatement);
        project.setArchitectureOverview(architectureOverview);
        project.setArchitectureFlow(architectureFlow);
        project.setKeyMetrics(keyMetrics);
        project.setTechnologies(technologies);
        project.setGithubUrl(githubUrl);
        project.setLiveUrl(liveUrl);
        if (isNew) {
            project.setPublished(true);
            project.setFeatured(true);
            project.setSortOrder(defaultOrder);
            project.setCreatedAt(now);
        } else if (project.getCreatedAt() == null) {
            project.setCreatedAt(now);
        }
        project.setUpdatedAt(now);
        projects.save(project);
    }

    private void validateSecurityEnvironment() {
        if (adminUsername == null || !adminUsername.matches("[A-Za-z0-9_.-]{3,64}")) {
            throw new IllegalStateException("ADMIN_USERNAME must contain 3–64 safe characters");
        }
        if (adminPasswordHash == null || !BCRYPT.matcher(adminPasswordHash).matches()) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASH must be a complete BCrypt hash");
        }
        int bcryptCost = Integer.parseInt(adminPasswordHash.substring(4, 6));
        if (bcryptCost < 12 || bcryptCost > 16) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASH must use BCrypt cost 12–16");
        }
        if (jwtTtlMinutes < 1 || jwtTtlMinutes > 30) {
            throw new IllegalStateException("JWT_TTL_MINUTES must be between 1 and 30");
        }
        if (signingKey == null
                || signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SIGNING_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 random bytes");
        }
    }


    private static Experience initialExperience(Instant now) {
        Experience experience = new Experience();
        experience.setRole("Software Developer Intern");
        experience.setCompany("Web Graphics Studio");
        experience.setEmploymentType("Internship · Part-Time");
        experience.setWorkMode("Remote (Work From Home)");
        experience.setLocation("Remote · India");
        experience.setStartDate(LocalDate.parse("2026-01-01"));
        experience.setEndDate(LocalDate.parse("2026-06-30"));
        experience.setCurrent(false);
        experience.setSummary("Developed and maintained responsive web interfaces and structured order-processing workflows for Web Graphics Studio in a remote, part-time engineering workflow.");
        experience.setHighlights(List.of(
            "Developed and maintained responsive web interfaces and structured order-processing workflows, including the WGS Order Processing web application (oder.webgraphicsstudio.in).",
            "Delivered front-end UI/UX components, client-side form validation, and performance optimizations in a remote, part-time engineering workflow."
        ));
        experience.setTechnologies(List.of("HTML5", "CSS3", "JavaScript", "UI/UX", "Web Optimization"));
        experience.setSortOrder(10);
        experience.setPublished(true);
        experience.setCreatedAt(now);
        experience.setUpdatedAt(now);
        return experience;
    }

    private static PortfolioProfile initialProfile(Instant now) {
        PortfolioProfile profile = new PortfolioProfile();
        profile.setFullName("Mufaddal Battiwala");
        profile.setBrand("MLB");
        profile.setTagline("Full Stack Developer | Java + Spring Boot & Modern Web Developer");
        profile.setPublicEmail("mufaddal.techpro@gmail.com");
        profile.setPhone("+91 907933526");
        profile.setLocation("Kolkata, India");
        profile.setWebsiteUrl("https://mufaddalbatti53.github.io/MLB-Portfolio/");
        profile.setGithubUrl("https://github.com/mufaddalbatti53");
        profile.setLinkedinUrl("https://www.linkedin.com/in/mufaddal-tech/");
        profile.setInstagramHandle("@mlb1606");
        profile.setEducation(List.of(
            new PortfolioProfile.Education("B.Tech (CSE)", "Netaji Subhash Engineering College", "Kolkata", null),
            new PortfolioProfile.Education("12th Grade", "Maharshi Sandipan Vidhyapeeth Senior Secondary School", "Kota", "81.2%"),
            new PortfolioProfile.Education("10th Grade", "New Haideriya English Senior Secondary School", null, "82.33%")
        ));
        profile.setUpdatedAt(now);
        return profile;
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/config/MongoIndexConfig.java`

```java
package com.mlb.portfolio.config;

import com.mlb.portfolio.model.AdminSession;
import com.mlb.portfolio.model.BlogPost;
import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.model.Project;
import com.mlb.portfolio.model.SiteFeedback;
import com.mlb.portfolio.model.RateLimitCounter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
public class MongoIndexConfig {
    @Bean
    @Order(0)
    public ApplicationRunner ensureIndexes(MongoTemplate mongo, MongoMappingContext context) {
        return args -> {
            MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(context);
            for (Class<?> type : new Class<?>[]{ Project.class, BlogPost.class, Experience.class,
                    ContactMessage.class, RateLimitCounter.class, AdminSession.class, SiteFeedback.class }) {
                var collection = mongo.indexOps(type);
                resolver.resolveIndexFor(type).forEach(collection::ensureIndex);
            }
        };
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/config/SecurityConfig.java`

```java
package com.mlb.portfolio.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {
    @Bean
    FilterRegistrationBean<AdminJwtFilter> adminJwtServletRegistration(AdminJwtFilter filter) {
        FilterRegistrationBean<AdminJwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AdminJwtFilter jwt) throws Exception {
        return http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                .referrerPolicy(referrer -> referrer.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/projects", "/api/v1/projects/**",
                    "/api/v1/blog", "/api/v1/blog/**",
                    "/api/v1/experiences",
                    "/api/v1/profile",
                    "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/contact", "/api/v1/feedback", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/admin/**", "/api/v1/auth/me", "/api/v1/auth/logout")
                    .hasRole("ADMIN")
                .requestMatchers("/actuator/**").denyAll()
                .anyRequest().denyAll())
            .build();
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/AdminController.java`

```java
package com.mlb.portfolio.controller;
import com.mlb.portfolio.dto.*;
import com.mlb.portfolio.model.ContactMessage.InboxStatus;
import com.mlb.portfolio.service.AdminContentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminContentService service;
    public AdminController(AdminContentService service){this.service=service;}
    @GetMapping("/dashboard") public Map<String,Long> dashboard(){return service.dashboard();}
    @GetMapping("/projects") public Page<AdminProjectResponse> projects(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.projects(page,size);}
    @GetMapping("/projects/{id}") public AdminProjectResponse project(@PathVariable String id){return service.project(id);}
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED) public AdminProjectResponse addProject(@Valid @RequestBody AdminProjectRequest dto){return service.saveProject(null,dto);}
    @PutMapping("/projects/{id}") public AdminProjectResponse updateProject(@PathVariable String id,@Valid @RequestBody AdminProjectRequest dto){return service.saveProject(id,dto);}
    public record ProjectPatch(Boolean published,Boolean featured,@Min(0) @Max(100000) Integer sortOrder){}
    @PatchMapping("/projects/{id}") public AdminProjectResponse patchProject(@PathVariable String id,@Valid @RequestBody ProjectPatch patch){return service.patchProject(id,patch.published(),patch.featured(),patch.sortOrder());}
    public record ReorderRequest(@Size(min=1,max=100) List<String> ids){}
    @PatchMapping("/projects/order") public Map<String,String> reorder(@Valid @RequestBody ReorderRequest request){service.reorder(request.ids());return Map.of("status","reordered");}
    @DeleteMapping("/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProject(@PathVariable String id){service.deleteProject(id);}
    @GetMapping("/blog") public Page<AdminBlogResponse> blogs(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.blogs(page,size);}
    @GetMapping("/blog/{id}") public AdminBlogResponse blog(@PathVariable String id){return service.blog(id);}
    @PostMapping("/blog") @ResponseStatus(HttpStatus.CREATED) public AdminBlogResponse addBlog(@Valid @RequestBody AdminBlogRequest dto){return service.saveBlog(null,dto);}
    @PutMapping("/blog/{id}") public AdminBlogResponse updateBlog(@PathVariable String id,@Valid @RequestBody AdminBlogRequest dto){return service.saveBlog(id,dto);}
    public record BlogPatch(@NotNull Boolean published){}
    @PatchMapping("/blog/{id}") public AdminBlogResponse patchBlog(@PathVariable String id,@Valid @RequestBody BlogPatch patch){return service.patchBlog(id,patch.published());}
    @DeleteMapping("/blog/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteBlog(@PathVariable String id){service.deleteBlog(id);}
    @GetMapping("/messages") public Page<AdminMessageResponse> messages(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.messages(page,size);}
    public record MessagePatch(@NotNull InboxStatus inboxStatus){}
    @PatchMapping("/messages/{id}") public AdminMessageResponse messageStatus(@PathVariable String id,@Valid @RequestBody MessagePatch patch){return service.messageStatus(id,patch.inboxStatus());}
    @PostMapping("/messages/{id}/retry") public AdminMessageResponse retry(@PathVariable String id){return service.retry(id);}
    @DeleteMapping("/messages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteMessage(@PathVariable String id){service.deleteMessage(id);}
    @GetMapping("/profile") public Map<String,Object> profile(){return service.profileView();}
    @PutMapping("/profile") public Map<String,Object> updateProfile(@Valid @RequestBody AdminProfileRequest profile){return service.updateProfile(profile);}
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/AdminExperienceController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.AdminExperienceResponse;
import com.mlb.portfolio.dto.ExperienceRequest;
import com.mlb.portfolio.service.ExperienceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/experiences")
public class AdminExperienceController {
    private final ExperienceService service;

    public AdminExperienceController(ExperienceService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AdminExperienceResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.adminList(page, size);
    }

    @GetMapping("/{id}")
    public AdminExperienceResponse get(@PathVariable String id) {
        return service.adminGet(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminExperienceResponse create(@Valid @RequestBody ExperienceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AdminExperienceResponse update(
            @PathVariable String id,
            @Valid @RequestBody ExperienceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/AdminFeedbackController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.AdminFeedbackResponse;
import com.mlb.portfolio.service.FeedbackService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {
    private final FeedbackService service;
    public AdminFeedbackController(FeedbackService service){this.service=service;}
    @GetMapping public Page<AdminFeedbackResponse> list(@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.list(page,size);}
    @DeleteMapping("/{id:[a-fA-F0-9]{24}}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id){service.delete(id);}
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/AuthController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.LoginRequest;
import com.mlb.portfolio.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AdminAuthService service;
    private final ClientIpResolver ip;

    public AuthController(AdminAuthService service, ClientIpResolver ip) {
        this.service = service;
        this.ip = ip;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request) {
        String jwt = service.login(login, ip.resolve(request));
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of(
                "status", "authenticated",
                "token", jwt,
                "tokenType", "Bearer",
                "expiresInSeconds", String.valueOf(service.ttl().toSeconds())));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("authenticated", true, "role", "ADMIN");
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
            ? header.substring(7).trim()
            : null;
        if (token != null && !token.isBlank()) service.logout(token);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of("status", "signed_out"));
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/BlogController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.BlogPostResponse;
import com.mlb.portfolio.service.BlogPostService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/blog")
public class BlogController {
    private final BlogPostService service;
    public BlogController(BlogPostService service) { this.service = service; }
    @GetMapping
    public List<BlogPostResponse> list(@RequestParam(defaultValue = "3") @Min(1) @Max(24) int limit) {
        return service.list(limit);
    }
    @GetMapping("/{slug:[a-z0-9-]+}")
    public BlogPostResponse get(@PathVariable String slug) { return service.get(slug); }
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/ContactController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.ContactRequest;
import com.mlb.portfolio.dto.ContactResponse;
import com.mlb.portfolio.service.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {
    private final ContactService service;
    private final ClientIpResolver ipResolver;
    public ContactController(ContactService service, ClientIpResolver ipResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContactResponse submit(@Valid @RequestBody ContactRequest request, HttpServletRequest servletRequest) {
        return service.submit(request, ipResolver.resolve(servletRequest));
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/ExperienceController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.ExperienceResponse;
import com.mlb.portfolio.service.ExperienceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiences")
public class ExperienceController {
    private final ExperienceService service;

    public ExperienceController(ExperienceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExperienceResponse> list() {
        return service.publicList();
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/FeedbackController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.FeedbackRequest;
import com.mlb.portfolio.dto.FeedbackResponse;
import com.mlb.portfolio.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
    private final FeedbackService service;
    private final ClientIpResolver ips;
    public FeedbackController(FeedbackService service,ClientIpResolver ips){this.service=service;this.ips=ips;}
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public FeedbackResponse submit(@Valid @RequestBody FeedbackRequest request,HttpServletRequest servlet){
        return service.submit(request,ips.resolve(servlet));
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/ProfileController.java`

```java
package com.mlb.portfolio.controller;
import com.mlb.portfolio.dto.ProfileResponse;
import com.mlb.portfolio.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service){this.service=service;}
    @GetMapping public ProfileResponse view(){return service.publicProfile();}
}
```

## `backend/src/main/java/com/mlb/portfolio/controller/ProjectController.java`

```java
package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.ProjectResponse;
import com.mlb.portfolio.service.ProjectService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service) { this.service = service; }
    @GetMapping
    public List<ProjectResponse> list(@RequestParam(defaultValue = "6") @Min(1) @Max(24) int limit) {
        return service.list(limit);
    }
    @GetMapping("/{slug:[a-z0-9-]+}")
    public ProjectResponse get(@PathVariable String slug) { return service.get(slug); }
}
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminBlogRequest.java`

```java
package com.mlb.portfolio.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record AdminBlogRequest(
    @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=100) String slug,
    @NotBlank @Size(max=150) String title, @NotBlank @Size(max=400) String excerpt,
    @NotBlank String articleUrl, @NotNull @Size(max=12) List<@NotBlank @Size(max=50) String> tags,
    String coverImageUrl, @Size(max=180) String coverImageAlt,
    boolean published, Instant publishedAt) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminBlogResponse.java`

```java
package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.BlogPost;
import java.time.Instant;
import java.util.List;
public record AdminBlogResponse(String id, Long version, String slug, String title, String excerpt,
    String articleUrl, List<String> tags, String coverImageUrl, String coverImageAlt,
    boolean published, Instant publishedAt, Instant updatedAt) {
    public static AdminBlogResponse from(BlogPost p){return new AdminBlogResponse(p.getId(),p.getVersion(),p.getSlug(),p.getTitle(),
        p.getExcerpt(),p.getArticleUrl(),p.getTags(),p.getCoverImageUrl(),p.getCoverImageAlt(),p.isPublished(),
        p.getPublishedAt(),p.getUpdatedAt());}
}
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminExperienceResponse.java`

```java
package com.mlb.portfolio.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminExperienceResponse(
    String id,
    Long version,
    String role,
    String company,
    String employmentType,
    String workMode,
    String location,
    LocalDate startDate,
    LocalDate endDate,
    boolean current,
    String summary,
    List<String> highlights,
    List<String> technologies,
    int sortOrder,
    boolean published,
    Instant updatedAt
) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminFeedbackResponse.java`

```java
package com.mlb.portfolio.dto;

import com.mlb.portfolio.model.SiteFeedback.Category;
import java.time.Instant;
public record AdminFeedbackResponse(String id, int rating, Category category, String comment, Instant createdAt) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminMessageResponse.java`

```java
package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.ContactMessage;
import java.time.Instant;
public record AdminMessageResponse(String id, String name, String email, String subject, String message,
    ContactMessage.InboxStatus inboxStatus, ContactMessage.NotificationStatus notificationStatus,
    int notificationAttempts, Instant createdAt) {
    public static AdminMessageResponse from(ContactMessage m){return new AdminMessageResponse(m.getId(),m.getName(),m.getEmail(),
        m.getSubject(),m.getMessage(),m.getInboxStatus(),m.getNotificationStatus(),m.getNotificationAttempts(),m.getCreatedAt());}
}
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminProfileRequest.java`

```java
package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.PortfolioProfile.Education;
import jakarta.validation.constraints.*;
import java.util.List;
public record AdminProfileRequest(
    @NotBlank @Size(max=100) String fullName, @NotBlank @Size(max=20) String brand,
    @NotBlank @Size(max=180) String tagline, @NotBlank @Email @Size(max=254) String publicEmail,
    @Size(max=30) String phone, @NotBlank @Size(max=120) String location,
    String websiteUrl, String githubUrl, String linkedinUrl,
    @Size(max=40) String instagramHandle, @NotNull @Size(max=12) List<Education> education) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminProjectRequest.java`

```java
package com.mlb.portfolio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminProjectRequest(
    @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 100) String slug,
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 300) String summary,
    @Size(max = 5000) String description,
    @Size(max = 1000) String problemStatement,
    @Size(max = 2000) String architectureOverview,
    @NotNull @Size(max = 8) List<@NotBlank @Size(max = 160) String> architectureFlow,
    @NotNull @Size(max = 4) List<@NotBlank @Size(max = 120) String> keyMetrics,
    @NotNull @Size(max = 15) List<@NotBlank @Size(max = 60) String> technologies,
    String githubUrl,
    String liveUrl,
    String imageUrl,
    @Size(max = 180) String imageAlt,
    boolean featured,
    boolean published,
    @Min(0) @Max(100000) int sortOrder
) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/AdminProjectResponse.java`

```java
package com.mlb.portfolio.dto;

import java.time.Instant;
import java.util.List;

public record AdminProjectResponse(
    String id,
    Long version,
    String slug,
    String title,
    String summary,
    String description,
    String problemStatement,
    String architectureOverview,
    List<String> architectureFlow,
    List<String> keyMetrics,
    List<String> technologies,
    String githubUrl,
    String liveUrl,
    String imageUrl,
    String imageAlt,
    boolean featured,
    boolean published,
    int sortOrder,
    Instant updatedAt
) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/BlogPostResponse.java`

```java
package com.mlb.portfolio.dto;

import java.time.Instant;
import java.util.List;

public record BlogPostResponse(String slug, String title, String excerpt, String articleUrl,
                               List<String> tags, String coverImageUrl, String coverImageAlt,
                               Instant publishedAt) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/ContactRequest.java`

```java
package com.mlb.portfolio.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContactRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 150) String subject,
    @NotBlank @Size(max = 5000) String message,
    @Size(max = 200) String website,
    @NotNull @AssertTrue Boolean privacyAccepted
) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/ContactResponse.java`

```java
package com.mlb.portfolio.dto;

public record ContactResponse(String referenceId, String message) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/ExperienceRequest.java`

```java
package com.mlb.portfolio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ExperienceRequest(
    @NotBlank @Size(max = 120) String role,
    @NotBlank @Size(max = 120) String company,
    @NotBlank @Size(max = 80) String employmentType,
    @NotBlank @Size(max = 80) String workMode,
    @NotBlank @Size(max = 120) String location,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    boolean current,
    @NotBlank @Size(max = 1500) String summary,
    @NotNull @Size(max = 8) List<@NotBlank @Size(max = 300) String> highlights,
    @NotNull @Size(max = 15) List<@NotBlank @Size(max = 60) String> technologies,
    @Min(0) @Max(100000) int sortOrder,
    boolean published
) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/ExperienceResponse.java`

```java
package com.mlb.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

public record ExperienceResponse(
    String role,
    String company,
    String employmentType,
    String workMode,
    String location,
    LocalDate startDate,
    LocalDate endDate,
    boolean current,
    String summary,
    List<String> highlights,
    List<String> technologies
) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/FeedbackRequest.java`

```java
package com.mlb.portfolio.dto;

import com.mlb.portfolio.model.SiteFeedback.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(@Min(1) @Max(5) int rating,
    @NotNull Category category, @Size(max=500) String comment,
    @Size(max=200) String website) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/FeedbackResponse.java`

```java
package com.mlb.portfolio.dto;

public record FeedbackResponse(String message) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/LoginRequest.java`

```java
package com.mlb.portfolio.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LoginRequest(@NotBlank @Size(max=64) String username, @NotBlank @Size(max=256) String password) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/ProfileResponse.java`

```java
package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.PortfolioProfile.Education;
import java.util.List;
public record ProfileResponse(String fullName,String brand,String tagline,String publicEmail,
    String phone,String location,String websiteUrl,String githubUrl,String linkedinUrl,
    String instagramHandle,List<Education> education) { }
```

## `backend/src/main/java/com/mlb/portfolio/dto/ProjectResponse.java`

```java
package com.mlb.portfolio.dto;

import java.time.Instant;
import java.util.List;

public record ProjectResponse(
    String slug,
    String title,
    String summary,
    String description,
    String problemStatement,
    String architectureOverview,
    List<String> architectureFlow,
    List<String> keyMetrics,
    List<String> technologies,
    String githubUrl,
    String liveUrl,
    String imageUrl,
    String imageAlt,
    boolean featured,
    Instant createdAt
) { }
```

## `backend/src/main/java/com/mlb/portfolio/exception/GlobalExceptionHandler.java`

```java
package com.mlb.portfolio.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ResponseEntity<ProblemDetail> problem(HttpStatus code, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code, detail);
        body.setTitle(title);
        body.setType(URI.create("about:blank"));
        return ResponseEntity.status(code).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Please check the highlighted fields.");
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.putIfAbsent(err.getField(),
            err.getDefaultMessage() == null ? "Invalid value" : err.getDefaultMessage()));
        if (response.getBody() != null) response.getBody().setProperty("errors", errors);
        return response;
    }
    @ExceptionHandler({ HttpMessageNotReadableException.class, ConstraintViolationException.class })
    public ResponseEntity<ProblemDetail> badRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request content or parameters are invalid.");
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> mediaType(Exception ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", "Send application/json.");
    }
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests. Please try again later.");
        detail.setTitle("Rate limited");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> expected(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        return problem(status, status.getReasonPhrase(),
            ex.getReason() == null ? "Request could not be completed." : ex.getReason());
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> database(DataAccessException ex) {
        log.error("Database request failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", "Please retry later.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unexpected API failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", "Please retry later.");
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/exception/RateLimitExceededException.java`

```java
package com.mlb.portfolio.exception;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;
    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
```

## `backend/src/main/java/com/mlb/portfolio/mapper/ExperienceMapper.java`

```java
package com.mlb.portfolio.mapper;

import com.mlb.portfolio.dto.AdminExperienceResponse;
import com.mlb.portfolio.dto.ExperienceResponse;
import com.mlb.portfolio.model.Experience;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExperienceMapper {
    public ExperienceResponse toPublic(Experience experience) {
        return new ExperienceResponse(
            experience.getRole(),
            experience.getCompany(),
            experience.getEmploymentType(),
            experience.getWorkMode(),
            experience.getLocation(),
            experience.getStartDate(),
            experience.getEndDate(),
            experience.isCurrent(),
            experience.getSummary(),
            safeList(experience.getHighlights()),
            safeList(experience.getTechnologies())
        );
    }

    public AdminExperienceResponse toAdmin(Experience experience) {
        return new AdminExperienceResponse(
            experience.getId(),
            experience.getVersion(),
            experience.getRole(),
            experience.getCompany(),
            experience.getEmploymentType(),
            experience.getWorkMode(),
            experience.getLocation(),
            experience.getStartDate(),
            experience.getEndDate(),
            experience.isCurrent(),
            experience.getSummary(),
            safeList(experience.getHighlights()),
            safeList(experience.getTechnologies()),
            experience.getSortOrder(),
            experience.isPublished(),
            experience.getUpdatedAt()
        );
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/mapper/ProjectMapper.java`

```java
package com.mlb.portfolio.mapper;

import com.mlb.portfolio.dto.AdminProjectResponse;
import com.mlb.portfolio.dto.ProjectResponse;
import com.mlb.portfolio.model.Project;
import com.mlb.portfolio.service.ContentSanitizer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {
    private final ContentSanitizer sanitizer;

    public ProjectMapper(ContentSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public ProjectResponse toPublic(Project project) {
        return new ProjectResponse(
            project.getSlug(),
            project.getTitle(),
            project.getSummary(),
            project.getDescription(),
            project.getProblemStatement(),
            project.getArchitectureOverview(),
            safeList(project.getArchitectureFlow()),
            safeList(project.getKeyMetrics()),
            safeList(project.getTechnologies()),
            sanitizer.publicHttpsUrl(project.getGithubUrl()),
            sanitizer.publicHttpsUrl(project.getLiveUrl()),
            sanitizer.publicHttpsUrl(project.getImageUrl()),
            project.getImageAlt(),
            project.isFeatured(),
            project.getCreatedAt()
        );
    }

    public AdminProjectResponse toAdmin(Project project) {
        return new AdminProjectResponse(
            project.getId(),
            project.getVersion(),
            project.getSlug(),
            project.getTitle(),
            project.getSummary(),
            project.getDescription(),
            project.getProblemStatement(),
            project.getArchitectureOverview(),
            safeList(project.getArchitectureFlow()),
            safeList(project.getKeyMetrics()),
            safeList(project.getTechnologies()),
            project.getGithubUrl(),
            project.getLiveUrl(),
            project.getImageUrl(),
            project.getImageAlt(),
            project.isFeatured(),
            project.isPublished(),
            project.getSortOrder(),
            project.getUpdatedAt()
        );
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/AdminSession.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("admin_sessions")
public class AdminSession {
    @Id private String id;
    @Indexed(unique = true) private String tokenIdDigest;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;
    @Indexed(expireAfter = "0s") private Instant purgeAfter;

    protected AdminSession() { }

    public AdminSession(String tokenIdDigest, Instant createdAt, Instant expiresAt) {
        this.tokenIdDigest = tokenIdDigest;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.purgeAfter = expiresAt;
    }

    public String getId() { return id; }
    public String getTokenIdDigest() { return tokenIdDigest; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public void setRevokedAt(Instant value) { revokedAt = value; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/BlogPost.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("blog_posts")
@CompoundIndex(name = "blog_public_date", def = "{'published': 1, 'publishedAt': -1}")
public class BlogPost {
    @Id private String id;
    @Version private Long version;
    @Indexed(unique = true) private String slug;
    private String title;
    private String excerpt;
    private String articleUrl;
    private List<String> tags = new ArrayList<>();
    private String coverImageUrl;
    private String coverImageAlt;
    private boolean published;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getExcerpt() { return excerpt; }
    public String getArticleUrl() { return articleUrl; }
    public List<String> getTags() { return tags; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getCoverImageAlt() { return coverImageAlt; }
    public boolean isPublished() { return published; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSlug(String value) { slug = value; }
    public void setTitle(String value) { title = value; }
    public void setExcerpt(String value) { excerpt = value; }
    public void setArticleUrl(String value) { articleUrl = value; }
    public void setTags(List<String> value) { tags = new ArrayList<>(value); }
    public void setCoverImageUrl(String value) { coverImageUrl = value; }
    public void setCoverImageAlt(String value) { coverImageAlt = value; }
    public void setPublished(boolean value) { published = value; }
    public void setPublishedAt(Instant value) { publishedAt = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/BootstrapState.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("bootstrap_state")
public class BootstrapState {
    @Id private String id;
    private Instant completedAt;

    protected BootstrapState() { }

    public BootstrapState(String id, Instant completedAt) {
        this.id = id;
        this.completedAt = completedAt;
    }

    public String getId() { return id; }
    public Instant getCompletedAt() { return completedAt; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/ContactMessage.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("contact_messages")
@CompoundIndex(name = "inbox_status_date", def = "{'inboxStatus': 1, 'createdAt': -1}")
public class ContactMessage {
    public enum NotificationStatus { PENDING, PROCESSING, SENT, FAILED }
    public enum InboxStatus { UNREAD, READ, ARCHIVED, SPAM }

    @Id private String id;
    @Indexed(unique = true) private String referenceId;
    private String name;
    private String email;
    private String subject;
    private String message;
    private Instant privacyAcceptedAt;
    private Instant createdAt;
    private InboxStatus inboxStatus = InboxStatus.UNREAD;
    private Instant reviewedAt;
    private NotificationStatus notificationStatus = NotificationStatus.PENDING;
    private int notificationAttempts;
    @Indexed private Instant nextNotificationAt;
    private Instant notificationLeaseUntil;
    @Indexed(expireAfter = "0s") private Instant expiresAt;

    protected ContactMessage() { }

    public ContactMessage(String referenceId, String name, String email, String subject,
                          String message, Instant createdAt, Instant expiresAt) {
        this.referenceId = referenceId;
        this.name = name;
        this.email = email;
        this.subject = subject;
        this.message = message;
        this.createdAt = createdAt;
        this.privacyAcceptedAt = createdAt;
        this.expiresAt = expiresAt;
        this.nextNotificationAt = createdAt;
    }

    public String getId() { return id; }
    public String getReferenceId() { return referenceId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public Instant getPrivacyAcceptedAt() { return privacyAcceptedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public InboxStatus getInboxStatus() { return inboxStatus; }
    public Instant getReviewedAt() { return reviewedAt; }
    public NotificationStatus getNotificationStatus() { return notificationStatus; }
    public int getNotificationAttempts() { return notificationAttempts; }
    public Instant getNextNotificationAt() { return nextNotificationAt; }
    public Instant getNotificationLeaseUntil() { return notificationLeaseUntil; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setInboxStatus(InboxStatus value) { inboxStatus = value; }
    public void setReviewedAt(Instant value) { reviewedAt = value; }
    public void setNotificationStatus(NotificationStatus value) { notificationStatus = value; }
    public void setNotificationAttempts(int value) { notificationAttempts = value; }
    public void setNextNotificationAt(Instant value) { nextNotificationAt = value; }
    public void setNotificationLeaseUntil(Instant value) { notificationLeaseUntil = value; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/Experience.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("experiences")
@CompoundIndex(name = "experience_public_order", def = "{'published': 1, 'sortOrder': 1, 'startDate': -1}")
public class Experience {
    @Id private String id;
    @Version private Long version;
    private String role;
    private String company;
    private String employmentType;
    private String workMode;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean current;
    private String summary;
    private List<String> highlights = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private int sortOrder;
    private boolean published;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getRole() { return role; }
    public String getCompany() { return company; }
    public String getEmploymentType() { return employmentType; }
    public String getWorkMode() { return workMode; }
    public String getLocation() { return location; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isCurrent() { return current; }
    public String getSummary() { return summary; }
    public List<String> getHighlights() { return highlights; }
    public List<String> getTechnologies() { return technologies; }
    public int getSortOrder() { return sortOrder; }
    public boolean isPublished() { return published; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRole(String value) { role = value; }
    public void setCompany(String value) { company = value; }
    public void setEmploymentType(String value) { employmentType = value; }
    public void setWorkMode(String value) { workMode = value; }
    public void setLocation(String value) { location = value; }
    public void setStartDate(LocalDate value) { startDate = value; }
    public void setEndDate(LocalDate value) { endDate = value; }
    public void setCurrent(boolean value) { current = value; }
    public void setSummary(String value) { summary = value; }
    public void setHighlights(List<String> value) {
        highlights = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setTechnologies(List<String> value) {
        technologies = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setSortOrder(int value) { sortOrder = value; }
    public void setPublished(boolean value) { published = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/PortfolioProfile.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("portfolio_profiles")
public class PortfolioProfile {
    public record Education(String qualification, String institution, String location, String score) { }

    @Id private String id = "owner";
    @Version private Long version;
    private String fullName;
    private String brand;
    private String tagline;
    private String publicEmail;
    private String phone;
    private String location;
    private String websiteUrl;
    private String githubUrl;
    private String linkedinUrl;
    private String instagramHandle;
    private List<Education> education = new ArrayList<>();
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getFullName() { return fullName; }
    public String getBrand() { return brand; }
    public String getTagline() { return tagline; }
    public String getPublicEmail() { return publicEmail; }
    public String getPhone() { return phone; }
    public String getLocation() { return location; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getGithubUrl() { return githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getInstagramHandle() { return instagramHandle; }
    public List<Education> getEducation() { return education; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFullName(String value) { fullName = value; }
    public void setBrand(String value) { brand = value; }
    public void setTagline(String value) { tagline = value; }
    public void setPublicEmail(String value) { publicEmail = value; }
    public void setPhone(String value) { phone = value; }
    public void setLocation(String value) { location = value; }
    public void setWebsiteUrl(String value) { websiteUrl = value; }
    public void setGithubUrl(String value) { githubUrl = value; }
    public void setLinkedinUrl(String value) { linkedinUrl = value; }
    public void setInstagramHandle(String value) { instagramHandle = value; }
    public void setEducation(List<Education> value) { education = new ArrayList<>(value); }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/Project.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("projects")
@CompoundIndex(name = "project_public_order", def = "{'published': 1, 'sortOrder': 1, 'createdAt': -1}")
public class Project {
    @Id private String id;
    @Version private Long version;
    @Indexed(unique = true) private String slug;
    private String title;
    private String summary;
    private String description;
    private String problemStatement;
    private String architectureOverview;
    private List<String> architectureFlow = new ArrayList<>();
    private List<String> keyMetrics = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private String githubUrl;
    private String liveUrl;
    private String imageUrl;
    private String imageAlt;
    private boolean featured;
    private boolean published;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getDescription() { return description; }
    public String getProblemStatement() { return problemStatement; }
    public String getArchitectureOverview() { return architectureOverview; }
    public List<String> getArchitectureFlow() { return architectureFlow; }
    public List<String> getKeyMetrics() { return keyMetrics; }
    public List<String> getTechnologies() { return technologies; }
    public String getGithubUrl() { return githubUrl; }
    public String getLiveUrl() { return liveUrl; }
    public String getImageUrl() { return imageUrl; }
    public String getImageAlt() { return imageAlt; }
    public boolean isFeatured() { return featured; }
    public boolean isPublished() { return published; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSlug(String value) { slug = value; }
    public void setTitle(String value) { title = value; }
    public void setSummary(String value) { summary = value; }
    public void setDescription(String value) { description = value; }
    public void setProblemStatement(String value) { problemStatement = value; }
    public void setArchitectureOverview(String value) { architectureOverview = value; }
    public void setArchitectureFlow(List<String> value) {
        architectureFlow = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setKeyMetrics(List<String> value) {
        keyMetrics = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setTechnologies(List<String> value) {
        technologies = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setGithubUrl(String value) { githubUrl = value; }
    public void setLiveUrl(String value) { liveUrl = value; }
    public void setImageUrl(String value) { imageUrl = value; }
    public void setImageAlt(String value) { imageAlt = value; }
    public void setFeatured(boolean value) { featured = value; }
    public void setPublished(boolean value) { published = value; }
    public void setSortOrder(int value) { sortOrder = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/RateLimitCounter.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("rate_limit_counters")
public class RateLimitCounter {
    public enum Scope { CONTACT, ADMIN_LOGIN_IP, ADMIN_LOGIN_ACCOUNT, FEEDBACK }

    @Id private String id;
    private Scope scope;
    private int requestCount;
    private Instant windowStartedAt;
    private Instant blockedUntil;
    @Indexed(expireAfter = "0s") private Instant expiresAt;

    public String getId() { return id; }
    public Scope getScope() { return scope; }
    public int getRequestCount() { return requestCount; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public Instant getExpiresAt() { return expiresAt; }
}
```

## `backend/src/main/java/com/mlb/portfolio/model/SiteFeedback.java`

```java
package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("site_feedback")
@CompoundIndex(name="feedback_recent", def="{'createdAt': -1, 'category': 1}")
public class SiteFeedback {
    public enum Category { UI_UX, BUG, FEATURE_IDEA, GENERAL }
    @Id private String id;
    private int rating;
    private Category category;
    private String comment;
    private Instant createdAt;
    @Indexed(expireAfter="0s") private Instant expiresAt;
    protected SiteFeedback() { }
    public SiteFeedback(int rating, Category category, String comment, Instant createdAt, Instant expiresAt) {
        this.rating=rating; this.category=category; this.comment=comment;
        this.createdAt=createdAt; this.expiresAt=expiresAt;
    }
    public String getId(){return id;}
    public int getRating(){return rating;}
    public Category getCategory(){return category;}
    public String getComment(){return comment;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;}
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/AdminSessionRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.AdminSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminSessionRepository extends MongoRepository<AdminSession, String> {
    Optional<AdminSession> findByTokenIdDigest(String tokenIdDigest);
    boolean existsByTokenIdDigestAndRevokedAtIsNullAndExpiresAtAfter(String tokenIdDigest, Instant now);
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/BlogPostRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.BlogPost;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BlogPostRepository extends MongoRepository<BlogPost, String> {
    List<BlogPost> findByPublishedTrueAndPublishedAtLessThanEqualOrderByPublishedAtDesc(Instant now, Pageable page);
    Optional<BlogPost> findBySlugAndPublishedTrueAndPublishedAtLessThanEqual(String slug, Instant now);
    Optional<BlogPost> findBySlug(String slug);
    Page<BlogPost> findAllByOrderByCreatedAtDesc(Pageable page);
    long countByPublishedTrueAndPublishedAtLessThanEqual(Instant now);
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/BootstrapStateRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.BootstrapState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BootstrapStateRepository extends MongoRepository<BootstrapState, String> { }
```

## `backend/src/main/java/com/mlb/portfolio/repository/ContactMessageRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.ContactMessage.InboxStatus;
import com.mlb.portfolio.model.ContactMessage.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContactMessageRepository extends MongoRepository<ContactMessage, String> {
    Page<ContactMessage> findAllByOrderByCreatedAtDesc(Pageable page);
    Page<ContactMessage> findByInboxStatusOrderByCreatedAtDesc(InboxStatus status, Pageable page);
    long countByInboxStatus(InboxStatus status);
    long countByNotificationStatus(NotificationStatus status);
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/ContactQueueRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.ContactMessage;
import java.time.Instant;
import java.util.Optional;

public interface ContactQueueRepository {
    Optional<ContactMessage> claimNext(Instant now);
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/ExperienceRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.Experience;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExperienceRepository extends MongoRepository<Experience, String> {
    List<Experience> findByPublishedTrueOrderBySortOrderAscStartDateDesc();
    Page<Experience> findAllByOrderBySortOrderAscStartDateDesc(Pageable pageable);
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/MongoContactQueueRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.ContactMessage.NotificationStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoContactQueueRepository implements ContactQueueRepository {
    private final MongoTemplate mongo;
    public MongoContactQueueRepository(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public Optional<ContactMessage> claimNext(Instant now) {
        Criteria due = new Criteria().orOperator(
            new Criteria().andOperator(
                Criteria.where("notificationStatus").in(NotificationStatus.PENDING, NotificationStatus.FAILED),
                Criteria.where("nextNotificationAt").lte(now)),
            new Criteria().andOperator(
                Criteria.where("notificationStatus").is(NotificationStatus.PROCESSING),
                Criteria.where("notificationLeaseUntil").lte(now)));
        Query query = new Query(new Criteria().andOperator(
            due, Criteria.where("notificationAttempts").lt(5)))
            .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        Update update = new Update()
            .set("notificationStatus", NotificationStatus.PROCESSING)
            .set("notificationLeaseUntil", now.plus(2, ChronoUnit.MINUTES))
            .inc("notificationAttempts", 1);
        return Optional.ofNullable(mongo.findAndModify(query, update,
            FindAndModifyOptions.options().returnNew(true), ContactMessage.class));
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/MongoRateLimitCounterRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.RateLimitCounter;
import java.time.Instant;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoRateLimitCounterRepository implements RateLimitCounterRepository {
    private final MongoTemplate mongo;

    public MongoRateLimitCounterRepository(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void markBlocked(String bucketId, Instant blockedUntil) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(bucketId)),
            new Update().set("blockedUntil", blockedUntil), RateLimitCounter.class);
    }

    @Override
    public int increment(String bucketId, Instant windowStart, Instant expiresAt) {
        Query query = Query.query(Criteria.where("_id").is(bucketId));
        Update update = new Update().inc("requestCount", 1)
            .setOnInsert("windowStartedAt", windowStart)
            .setOnInsert("expiresAt", expiresAt);
        RateLimitCounter updated = mongo.findAndModify(query, update,
            FindAndModifyOptions.options().upsert(true).returnNew(true), RateLimitCounter.class);
        if (updated == null) throw new IllegalStateException("Rate-limit update failed");
        return updated.getRequestCount();
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/PortfolioProfileRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.PortfolioProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PortfolioProfileRepository extends MongoRepository<PortfolioProfile, String> { }
```

## `backend/src/main/java/com/mlb/portfolio/repository/ProjectRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProjectRepository extends MongoRepository<Project, String> {
    List<Project> findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(Pageable page);
    Optional<Project> findBySlugAndPublishedTrue(String slug);
    Optional<Project> findBySlug(String slug);
    Page<Project> findAllByOrderBySortOrderAscCreatedAtDesc(Pageable page);
    long countByPublishedTrue();
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/RateLimitCounterRepository.java`

```java
package com.mlb.portfolio.repository;

import java.time.Instant;

public interface RateLimitCounterRepository {
    int increment(String bucketId, Instant windowStart, Instant expiresAt);
    default void markBlocked(String bucketId, Instant blockedUntil) { }
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/RateLimitStatsRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.RateLimitCounter;
import java.time.Instant;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RateLimitStatsRepository extends MongoRepository<RateLimitCounter, String> {
    long countByBlockedUntilAfter(Instant now);
}
```

## `backend/src/main/java/com/mlb/portfolio/repository/SiteFeedbackRepository.java`

```java
package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.SiteFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SiteFeedbackRepository extends MongoRepository<SiteFeedback,String> {
    Page<SiteFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

## `backend/src/main/java/com/mlb/portfolio/service/AbuseProtectionService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.exception.RateLimitExceededException;
import com.mlb.portfolio.model.RateLimitCounter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class AbuseProtectionService {
    private final MongoTemplate mongo;
    private final byte[] secret;
    public AbuseProtectionService(MongoTemplate mongo, @Value("${mlb.security.rate-limit-hmac-key}") String key) {
        if (key == null || key.length() < 32) throw new IllegalStateException("RATE_LIMIT_HMAC_SECRET must have at least 32 characters");
        this.mongo=mongo; this.secret=key.getBytes(StandardCharsets.UTF_8).clone();
    }
    public void check(String scope, String identity, int maximum, long windowSeconds) {
        Instant now=Instant.now();
        long start=(now.getEpochSecond()/windowSeconds)*windowSeconds;
        String id=scope+":"+start+":"+digest(identity);
        Query query=Query.query(Criteria.where("_id").is(id));
        Instant until=Instant.ofEpochSecond(start+windowSeconds);
        Update update=new Update().inc("requestCount",1).setOnInsert("windowStartedAt", Instant.ofEpochSecond(start))
            .setOnInsert("expiresAt",until.plusSeconds(windowSeconds));
        RateLimitCounter counter=mongo.findAndModify(query,update,FindAndModifyOptions.options().upsert(true).returnNew(true),RateLimitCounter.class);
        if(counter==null) throw new IllegalStateException("Rate limit update failed");
        if(counter.getRequestCount()>maximum){
            mongo.updateFirst(query,new Update().set("blockedUntil",until),RateLimitCounter.class);
            throw new RateLimitExceededException(Math.max(1,until.getEpochSecond()-now.getEpochSecond()));
        }
    }
    private String digest(String identity) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(identity.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e){throw new IllegalStateException("HMAC failure",e);}
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/AdminAuthService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.LoginRequest;
import com.mlb.portfolio.model.AdminSession;
import com.mlb.portfolio.repository.AdminSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthService {
    private final AdminSessionRepository sessions;
    private final AbuseProtectionService abuse;
    private final PasswordEncoder encoder;
    private final String username;
    private final String hash;
    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;
    public AdminAuthService(AdminSessionRepository sessions, AbuseProtectionService abuse, PasswordEncoder encoder,
        @Value("${mlb.security.admin-username}") String username,
        @Value("${mlb.security.admin-password-hash}") String hash,
        @Value("${mlb.security.jwt-secret}") String secret,
        @Value("${mlb.security.jwt-issuer:mlb-portfolio}") String issuer,
        @Value("${mlb.security.jwt-ttl-minutes:15}") long minutes) {
        this.sessions=sessions;this.abuse=abuse;this.encoder=encoder;this.username=username;this.hash=hash;this.issuer=issuer;
        byte[] signingBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if(signingBytes.length<32 || minutes<1 || minutes>30) throw new IllegalStateException("JWT security settings are invalid");
        this.key=new SecretKeySpec(signingBytes,"HmacSHA256");this.ttl=Duration.ofMinutes(minutes);
    }
    public String login(LoginRequest request,String ip){
        abuse.check("admin-login-ip",ip,5,900);
        abuse.check("admin-login-account",request.username().toLowerCase(java.util.Locale.ROOT),8,900);
        // Reduce username timing leaks
        boolean valid=encoder.matches(request.password(),hash) && MessageDigest.isEqual(
            username.getBytes(StandardCharsets.UTF_8),request.username().getBytes(StandardCharsets.UTF_8));
        if(!valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
        Instant now=Instant.now();String tokenId=UUID.randomUUID().toString();Instant expires=now.plus(ttl);
        String token=Jwts.builder().id(tokenId).subject("admin").issuer(issuer).issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(expires)).signWith(key).compact();
        sessions.save(new AdminSession(digest(tokenId),now,expires));
        return token;
    }
    public String validate(String token){
        try {
            Claims claims=Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
            if(!"admin".equals(claims.getSubject()) || claims.getId()==null) return null;
            return sessions.existsByTokenIdDigestAndRevokedAtIsNullAndExpiresAtAfter(digest(claims.getId()),Instant.now())
                ? claims.getId() : null;
        } catch(io.jsonwebtoken.JwtException | IllegalArgumentException bad){return null;}
    }
    public void logout(String token){
        try {
            Claims c=Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token).getPayload();
            sessions.findByTokenIdDigest(digest(c.getId())).ifPresent(s->{s.setRevokedAt(Instant.now());sessions.save(s);});
        } catch(io.jsonwebtoken.JwtException | IllegalArgumentException ignored){ }
    }
    public Duration ttl(){return ttl;}
    private String digest(String s){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/AdminContentService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.*;
import com.mlb.portfolio.model.*;
import com.mlb.portfolio.mapper.ProjectMapper;
import com.mlb.portfolio.repository.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminContentService {
    private final ProjectRepository projects;
    private final BlogPostRepository blogs;
    private final ContactMessageRepository messages;
    private final PortfolioProfileRepository profiles;
    private final RateLimitStatsRepository rateLimits;
    private final ContentSanitizer clean;
    private final ProjectMapper projectMapper;
    public AdminContentService(ProjectRepository projects,BlogPostRepository blogs,ContactMessageRepository messages,
        PortfolioProfileRepository profiles,RateLimitStatsRepository rateLimits,ContentSanitizer clean,ProjectMapper projectMapper){
        this.projects=projects;this.blogs=blogs;this.messages=messages;this.profiles=profiles;this.rateLimits=rateLimits;this.clean=clean;this.projectMapper=projectMapper;
    }
    public java.util.Map<String,Long> dashboard(){return java.util.Map.of(
        "totalProjects",projects.count(),"publishedBlogs",blogs.countByPublishedTrueAndPublishedAtLessThanEqual(Instant.now()),
        "unreadMessages",messages.countByInboxStatus(ContactMessage.InboxStatus.UNREAD),
        "activeRateLimitBlocks",rateLimits.countByBlockedUntilAfter(Instant.now()));}
    public Page<AdminProjectResponse> projects(int page,int size){return projects.findAllByOrderBySortOrderAscCreatedAtDesc(PageRequest.of(page,size)).map(projectMapper::toAdmin);}
    public AdminProjectResponse project(String id){return projectMapper.toAdmin(requireProject(id));}
    public AdminProjectResponse saveProject(String id,AdminProjectRequest request){
        Project p=id==null?new Project():requireProject(id);
        p.setSlug(request.slug());p.setTitle(clean.plainText(request.title(),120,false));
        p.setSummary(clean.plainText(request.summary(),300,false));
        p.setDescription(opt(request.description(),5000,true));
        p.setProblemStatement(opt(request.problemStatement(),1000,true));
        p.setArchitectureOverview(opt(request.architectureOverview(),2000,true));
        p.setArchitectureFlow(request.architectureFlow().stream().map(step->clean.plainText(step,160,false)).toList());
        p.setKeyMetrics(request.keyMetrics().stream().map(metric->clean.plainText(metric,120,false)).toList());
        p.setTechnologies(request.technologies().stream().map(t->clean.plainText(t,60,false)).toList());
        p.setGithubUrl(url(request.githubUrl()));p.setLiveUrl(url(request.liveUrl()));
        p.setImageUrl(url(request.imageUrl()));p.setImageAlt(opt(request.imageAlt(),180,false));
        p.setFeatured(request.featured());p.setPublished(request.published());p.setSortOrder(request.sortOrder());
        Instant now=Instant.now(); if(p.getCreatedAt()==null)p.setCreatedAt(now);p.setUpdatedAt(now);
        return projectMapper.toAdmin(projects.save(p));
    }
    public AdminProjectResponse patchProject(String id,Boolean published,Boolean featured,Integer sortOrder){
        Project p=requireProject(id);
        if(published==null && featured==null && sortOrder==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No update values");
        if(published!=null)p.setPublished(published);
        if(featured!=null)p.setFeatured(featured);
        if(sortOrder!=null){if(sortOrder<0||sortOrder>100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid order");p.setSortOrder(sortOrder);}
        p.setUpdatedAt(Instant.now());return projectMapper.toAdmin(projects.save(p));
    }
    public void reorder(List<String> ids){
        if(ids==null||ids.isEmpty()||ids.size()>100||ids.stream().distinct().count()!=ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid order list");
        for(int i=0;i<ids.size();i++){Project p=requireProject(ids.get(i));p.setSortOrder((i+1)*10);p.setUpdatedAt(Instant.now());projects.save(p);}
    }
    public void deleteProject(String id){requireProject(id);projects.deleteById(id);}
    private Project requireProject(String id){return projects.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Project not found"));}
    public Page<AdminBlogResponse> blogs(int page,int size){return blogs.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size)).map(AdminBlogResponse::from);}
    public AdminBlogResponse blog(String id){return AdminBlogResponse.from(requireBlog(id));}
    public AdminBlogResponse saveBlog(String id,AdminBlogRequest request){
        BlogPost b=id==null?new BlogPost():requireBlog(id);
        b.setSlug(request.slug());b.setTitle(clean.plainText(request.title(),150,false));
        b.setExcerpt(clean.plainText(request.excerpt(),400,false));
        String article=url(request.articleUrl());if(article==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Article URL must use HTTPS");
        b.setArticleUrl(article);
        b.setTags(request.tags().stream().map(t->clean.plainText(t,50,false)).toList());
        b.setCoverImageUrl(url(request.coverImageUrl()));b.setCoverImageAlt(opt(request.coverImageAlt(),180,false));
        b.setPublished(request.published());b.setPublishedAt(request.publishedAt()==null?Instant.now():request.publishedAt());
        Instant now=Instant.now();if(b.getCreatedAt()==null)b.setCreatedAt(now);b.setUpdatedAt(now);
        return AdminBlogResponse.from(blogs.save(b));
    }
    public AdminBlogResponse patchBlog(String id,Boolean published){
        if(published==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Missing published status");
        BlogPost b=requireBlog(id);b.setPublished(published);b.setUpdatedAt(Instant.now());
        return AdminBlogResponse.from(blogs.save(b));
    }
    public void deleteBlog(String id){requireBlog(id);blogs.deleteById(id);}
    private BlogPost requireBlog(String id){return blogs.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Blog not found"));}
    public Page<AdminMessageResponse> messages(int page,int size){return messages.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size)).map(AdminMessageResponse::from);}
    public AdminMessageResponse messageStatus(String id,ContactMessage.InboxStatus status){
        ContactMessage m=requireMessage(id);m.setInboxStatus(status);m.setReviewedAt(Instant.now());return AdminMessageResponse.from(messages.save(m));
    }
    public AdminMessageResponse retry(String id){
        ContactMessage m=requireMessage(id);
        if(m.getNotificationStatus()==ContactMessage.NotificationStatus.PROCESSING) throw new ResponseStatusException(HttpStatus.CONFLICT,"Delivery in progress");
        m.setNotificationAttempts(0);m.setNotificationStatus(ContactMessage.NotificationStatus.PENDING);
        m.setNextNotificationAt(Instant.now());return AdminMessageResponse.from(messages.save(m));
    }
    public void deleteMessage(String id){requireMessage(id);messages.deleteById(id);}
    private ContactMessage requireMessage(String id){return messages.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Message not found"));}
    public PortfolioProfile profile(){return profiles.findById("owner").orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile not found"));}
    public java.util.Map<String,Object> profileView(){return profileMap(profile());}
    public java.util.Map<String,Object> updateProfile(AdminProfileRequest body){
        PortfolioProfile p=profile();p.setFullName(clean.plainText(body.fullName(),100,false));
        p.setBrand(clean.plainText(body.brand(),20,false));p.setTagline(clean.plainText(body.tagline(),180,false));
        p.setPublicEmail(clean.email(body.publicEmail()));p.setPhone(opt(body.phone(),30,false));
        p.setLocation(clean.plainText(body.location(),120,false));p.setWebsiteUrl(url(body.websiteUrl()));
        p.setGithubUrl(url(body.githubUrl()));p.setLinkedinUrl(url(body.linkedinUrl()));
        p.setInstagramHandle(opt(body.instagramHandle(),40,false));p.setEducation(body.education());p.setUpdatedAt(Instant.now());
        return profileMap(profiles.save(p));
    }
    private java.util.Map<String,Object> profileMap(PortfolioProfile p){
        java.util.Map<String,Object> m=new java.util.LinkedHashMap<>();
        m.put("fullName",p.getFullName());m.put("brand",p.getBrand());m.put("tagline",p.getTagline());
        m.put("publicEmail",p.getPublicEmail());m.put("phone",p.getPhone());m.put("location",p.getLocation());
        m.put("websiteUrl",p.getWebsiteUrl());m.put("githubUrl",p.getGithubUrl());m.put("linkedinUrl",p.getLinkedinUrl());
        m.put("instagramHandle",p.getInstagramHandle());m.put("education",p.getEducation());return m;
    }
    private String opt(String raw,int max,boolean multi){return raw==null||raw.isBlank()?null:clean.plainText(raw,max,multi);}
    private String url(String raw){
        if(raw==null||raw.isBlank())return null;
        String valid=clean.publicHttpsUrl(raw);
        if(valid==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid HTTPS URL");
        return valid;
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/BlogPostService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.BlogPostResponse;
import com.mlb.portfolio.model.BlogPost;
import com.mlb.portfolio.repository.BlogPostRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlogPostService {
    private final BlogPostRepository repository;
    private final ContentSanitizer sanitizer;
    private final Clock clock = Clock.systemUTC();
    public BlogPostService(BlogPostRepository repository, ContentSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
    }
    public List<BlogPostResponse> list(int limit) {
        return repository.findByPublishedTrueAndPublishedAtLessThanEqualOrderByPublishedAtDesc(
            Instant.now(clock), PageRequest.of(0, limit)).stream().map(this::map).toList();
    }
    public BlogPostResponse get(String slug) {
        return repository.findBySlugAndPublishedTrueAndPublishedAtLessThanEqual(slug, Instant.now(clock))
            .map(this::map).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
    }
    private BlogPostResponse map(BlogPost p) {
        return new BlogPostResponse(p.getSlug(), p.getTitle(), p.getExcerpt(),
            sanitizer.publicHttpsUrl(p.getArticleUrl()),
            p.getTags() == null ? List.of() : List.copyOf(p.getTags()),
            sanitizer.publicHttpsUrl(p.getCoverImageUrl()), p.getCoverImageAlt(), p.getPublishedAt());
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/ContactNotificationService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.ContactMessage.NotificationStatus;
import com.mlb.portfolio.repository.ContactMessageRepository;
import com.mlb.portfolio.repository.ContactQueueRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mlb.mail.enabled", havingValue = "true")
public class ContactNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ContactNotificationService.class);
    private final ContactMessageRepository repository;
    private final ContactQueueRepository queue;
    private final JavaMailSender sender;
    private final String from;
    private final String to;

    public ContactNotificationService(ContactMessageRepository repository, ContactQueueRepository queue,
           JavaMailSender sender, @Value("${mlb.mail.from}") String from,
           @Value("${mlb.mail.notify-to}") String to) {
        if (from.isBlank() || to.isBlank()) throw new IllegalArgumentException("Mail addresses must be configured");
        this.repository = repository;
        this.queue = queue;
        this.sender = sender;
        this.from = from;
        this.to = to;
    }
    @Scheduled(fixedDelayString = "${mlb.mail.poll-interval-ms:30000}")
    public void deliverPending() {
        for (int i = 0; i < 10; i++) {
            var next = queue.claimNext(Instant.now());
            if (next.isEmpty()) return;
            ContactMessage contact = next.get();
            try {
                SimpleMailMessage email = new SimpleMailMessage();
                email.setFrom(from);
                email.setTo(to);
                email.setSubject("MLB inquiry: " + contact.getSubject());
                email.setText("Reference: " + contact.getReferenceId()
                    + "\nName: " + contact.getName() + "\nEmail: " + contact.getEmail()
                    + "\n\n" + contact.getMessage());
                sender.send(email);
                contact.setNotificationStatus(NotificationStatus.SENT);
                contact.setNextNotificationAt(null);
            } catch (RuntimeException ex) {
                log.warn("Contact notification failed for receipt {} (attempt {})",
                    contact.getReferenceId(), contact.getNotificationAttempts());
                contact.setNotificationStatus(NotificationStatus.FAILED);
                if (contact.getNotificationAttempts() < 5) {
                    long backoff = 1L << Math.min(contact.getNotificationAttempts(), 5);
                    contact.setNextNotificationAt(Instant.now().plusSeconds(backoff * 60));
                } else {
                    contact.setNextNotificationAt(null);
                }
            }
            contact.setNotificationLeaseUntil(null);
            repository.save(contact);
        }
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/ContactService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.ContactRequest;
import com.mlb.portfolio.dto.ContactResponse;
import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.repository.ContactMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContactService {
    private final ContactMessageRepository repository;
    private final RateLimitService rateLimit;
    private final ContentSanitizer sanitizer;
    private final int retentionDays;

    public ContactService(ContactMessageRepository repository, RateLimitService rateLimit,
                          ContentSanitizer sanitizer,
                          @Value("${mlb.contact.retention-days:90}") int retentionDays) {
        if (retentionDays < 1 || retentionDays > 365) throw new IllegalArgumentException("Invalid retention days");
        this.repository = repository;
        this.rateLimit = rateLimit;
        this.sanitizer = sanitizer;
        this.retentionDays = retentionDays;
    }

    public ContactResponse submit(ContactRequest request, String clientIp) {
        rateLimit.check(clientIp);
        String reference = UUID.randomUUID().toString();
        if (request.website() != null && !request.website().isBlank()) {
            return new ContactResponse(reference, "Your message has been received.");
        }
        Instant now = Instant.now();
        ContactMessage message = new ContactMessage(reference,
            sanitizer.plainText(request.name(), 100, false),
            sanitizer.email(request.email()),
            sanitizer.plainText(request.subject(), 150, false),
            sanitizer.plainText(request.message(), 5000, true),
            now, now.plus(retentionDays, ChronoUnit.DAYS));
        repository.save(message);
        return new ContactResponse(reference, "Your message has been received.");
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/ContentSanitizer.java`

```java
package com.mlb.portfolio.service;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContentSanitizer {
    public String plainText(String input, int maxLength, boolean multiline) {
        String normalized = Normalizer.normalize(Objects.requireNonNull(input), Normalizer.Form.NFKC);
        String cleanHtml = Jsoup.clean(normalized, Safelist.none());
        String text = Parser.unescapeEntities(cleanHtml, false)
            .replace("\r\n", "\n").replace('\r', '\n');
        text = multiline ? text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
            : text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ");
        text = text.strip();
        if (text.isBlank() || text.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid text field");
        }
        return text;
    }

    public String email(String email) {
        String value = Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.contains("<") || value.contains(">")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address");
        }
        return value;
    }

    public String publicHttpsUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = URI.create(raw.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/ExperienceService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.AdminExperienceResponse;
import com.mlb.portfolio.dto.ExperienceRequest;
import com.mlb.portfolio.dto.ExperienceResponse;
import com.mlb.portfolio.mapper.ExperienceMapper;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.repository.ExperienceRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExperienceService {
    private final ExperienceRepository repository;
    private final ExperienceMapper mapper;
    private final ContentSanitizer sanitizer;

    public ExperienceService(
            ExperienceRepository repository,
            ExperienceMapper mapper,
            ContentSanitizer sanitizer) {
        this.repository = repository;
        this.mapper = mapper;
        this.sanitizer = sanitizer;
    }

    public List<ExperienceResponse> publicList() {
        return repository.findByPublishedTrueOrderBySortOrderAscStartDateDesc()
            .stream()
            .map(mapper::toPublic)
            .toList();
    }

    public Page<AdminExperienceResponse> adminList(int page, int size) {
        return repository.findAllByOrderBySortOrderAscStartDateDesc(PageRequest.of(page, size))
            .map(mapper::toAdmin);
    }

    public AdminExperienceResponse adminGet(String id) {
        return mapper.toAdmin(requireExperience(id));
    }

    public AdminExperienceResponse create(ExperienceRequest request) {
        return save(new Experience(), request);
    }

    public AdminExperienceResponse update(String id, ExperienceRequest request) {
        return save(requireExperience(id), request);
    }

    public void delete(String id) {
        repository.delete(requireExperience(id));
    }

    private AdminExperienceResponse save(Experience experience, ExperienceRequest request) {
        validateDates(request);
        experience.setRole(sanitizer.plainText(request.role(), 120, false));
        experience.setCompany(sanitizer.plainText(request.company(), 120, false));
        experience.setEmploymentType(sanitizer.plainText(request.employmentType(), 80, false));
        experience.setWorkMode(sanitizer.plainText(request.workMode(), 80, false));
        experience.setLocation(sanitizer.plainText(request.location(), 120, false));
        experience.setStartDate(request.startDate());
        experience.setEndDate(request.current() ? null : request.endDate());
        experience.setCurrent(request.current());
        experience.setSummary(sanitizer.plainText(request.summary(), 1500, true));
        experience.setHighlights(request.highlights().stream()
            .map(value -> sanitizer.plainText(value, 300, false))
            .toList());
        experience.setTechnologies(request.technologies().stream()
            .map(value -> sanitizer.plainText(value, 60, false))
            .toList());
        experience.setSortOrder(request.sortOrder());
        experience.setPublished(request.published());
        Instant now = Instant.now();
        if (experience.getCreatedAt() == null) experience.setCreatedAt(now);
        experience.setUpdatedAt(now);
        return mapper.toAdmin(repository.save(experience));
    }

    private static void validateDates(ExperienceRequest request) {
        if (request.current() && request.endDate() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current experience cannot have an end date");
        }
        if (!request.current() && request.endDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date is required for past experience");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot precede start date");
        }
    }

    private Experience requireExperience(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Experience not found"));
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/FeedbackService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.AdminFeedbackResponse;
import com.mlb.portfolio.dto.FeedbackRequest;
import com.mlb.portfolio.dto.FeedbackResponse;
import com.mlb.portfolio.model.SiteFeedback;
import com.mlb.portfolio.repository.SiteFeedbackRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeedbackService {
    private final SiteFeedbackRepository repository;
    private final AbuseProtectionService abuse;
    private final ContentSanitizer sanitizer;
    private final int retentionDays;
    public FeedbackService(SiteFeedbackRepository repository, AbuseProtectionService abuse, ContentSanitizer sanitizer,
        @Value("${mlb.feedback.retention-days:90}") int retentionDays) {
        this.repository=repository; this.abuse=abuse; this.sanitizer=sanitizer; this.retentionDays=retentionDays;
    }
    public FeedbackResponse submit(FeedbackRequest request,String clientIp){
        abuse.check("feedback",clientIp,10,3600);
        if(request.website()!=null && !request.website().isBlank()) return new FeedbackResponse("Thanks for your feedback.");
        Instant now=Instant.now();
        String comment=request.comment()==null ? "" : (request.comment().isBlank() ? "" : sanitizer.plainText(request.comment(), 500, true));
        repository.save(new SiteFeedback(request.rating(),request.category(),comment,now,now.plusSeconds(86400L*retentionDays)));
        return new FeedbackResponse("Thanks for your feedback.");
    }
    public Page<AdminFeedbackResponse> list(int page,int size){
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size))
            .map(f->new AdminFeedbackResponse(f.getId(),f.getRating(),f.getCategory(),f.getComment(),f.getCreatedAt()));
    }
    public void delete(String id){
        if(!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Feedback not found");
        repository.deleteById(id);
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/ProfileService.java`

```java
package com.mlb.portfolio.service;
import com.mlb.portfolio.dto.ProfileResponse;
import com.mlb.portfolio.model.PortfolioProfile;
import com.mlb.portfolio.repository.PortfolioProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ProfileService {
    private final PortfolioProfileRepository profiles;
    public ProfileService(PortfolioProfileRepository profiles){this.profiles=profiles;}
    public ProfileResponse publicProfile(){
        PortfolioProfile p=profiles.findById("owner").orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile unavailable"));
        return new ProfileResponse(p.getFullName(),p.getBrand(),p.getTagline(),p.getPublicEmail(),p.getPhone(),
            p.getLocation(),p.getWebsiteUrl(),p.getGithubUrl(),p.getLinkedinUrl(),p.getInstagramHandle(),p.getEducation());
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/ProjectService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.ProjectResponse;
import com.mlb.portfolio.mapper.ProjectMapper;
import com.mlb.portfolio.repository.ProjectRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {
    private final ProjectRepository repository;
    private final ProjectMapper mapper;

    public ProjectService(ProjectRepository repository, ProjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<ProjectResponse> list(int limit) {
        return repository.findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(PageRequest.of(0, limit))
            .stream()
            .map(mapper::toPublic)
            .toList();
    }

    public ProjectResponse get(String slug) {
        return repository.findBySlugAndPublishedTrue(slug)
            .map(mapper::toPublic)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }
}
```

## `backend/src/main/java/com/mlb/portfolio/service/RateLimitService.java`

```java
package com.mlb.portfolio.service;

import com.mlb.portfolio.exception.RateLimitExceededException;
import com.mlb.portfolio.repository.RateLimitCounterRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private static final long WINDOW_SECONDS = 3600;
    private final RateLimitCounterRepository repository;
    private final byte[] key;
    private final int maxRequests;
    private final Clock clock;

    @Autowired
    public RateLimitService(RateLimitCounterRepository repository,
            @Value("${mlb.security.rate-limit-hmac-key}") String secret,
            @Value("${mlb.contact.max-per-hour:5}") int maxRequests) {
        this(repository, secret, maxRequests, Clock.systemUTC());
    }
    RateLimitService(RateLimitCounterRepository repository, String secret, int maxRequests, Clock clock) {
        if (secret == null || secret.length() < 32 || maxRequests < 1) {
            throw new IllegalArgumentException("Rate-limit secret must have 32+ characters and max requests must be positive");
        }
        this.repository = repository;
        this.key = secret.getBytes(StandardCharsets.UTF_8).clone();
        this.maxRequests = maxRequests;
        this.clock = clock;
    }
    public void check(String clientIp) {
        Instant now = clock.instant();
        long window = Math.floorDiv(now.getEpochSecond(), WINDOW_SECONDS) * WINDOW_SECONDS;
        Instant start = Instant.ofEpochSecond(window);
        String id = "contact:" + window + ":" + hash(clientIp);
        int count = repository.increment(id, start, start.plusSeconds(WINDOW_SECONDS * 2));
        if (count > maxRequests) {
            repository.markBlocked(id, start.plusSeconds(WINDOW_SECONDS));
            throw new RateLimitExceededException(Math.max(1, window + WINDOW_SECONDS - now.getEpochSecond()));
        }
    }
    private String hash(String identity) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize rate limiting", ex);
        }
    }
}
```

## `backend/src/main/resources/application.yml`

```yaml
server:
  port: ${PORT:8080}
  address: 0.0.0.0
  forward-headers-strategy: framework
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never
  tomcat:
    max-http-form-post-size: 8192

spring:
  application:
    name: mlb-portfolio
  profiles:
    default: local
  data:
    mongodb:
      uri: ${MONGODB_URI}
      auto-index-creation: false
  jackson:
    deserialization:
      fail-on-unknown-properties: true
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${SMTP_AUTH:true}
          starttls:
            enable: ${SMTP_STARTTLS:true}
            required: ${SMTP_STARTTLS:true}
          connectiontimeout: 10000
          timeout: 10000
          writetimeout: 10000

management:
  health:
    mail:
      enabled: ${MANAGEMENT_HEALTH_MAIL_ENABLED:false}
  endpoints:
    enabled-by-default: false
    web:
      exposure:
        include: health
  endpoint:
    health:
      enabled: true
      probes:
        enabled: true
      show-details: never

mlb:
  security:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5500,http://127.0.0.1:5500}
    trusted-proxy-cidrs: ${TRUSTED_PROXY_CIDRS:}
    rate-limit-hmac-key: ${RATE_LIMIT_HMAC_SECRET}
    admin-username: ${ADMIN_USERNAME}
    admin-password-hash: ${ADMIN_PASSWORD_HASH}
    jwt-secret: ${JWT_SECRET}
    jwt-issuer: mlb-portfolio
    jwt-ttl-minutes: ${JWT_TTL_MINUTES:15}
  feedback:
    retention-days: ${FEEDBACK_RETENTION_DAYS:90}
  contact:
    retention-days: ${CONTACT_RETENTION_DAYS:90}
    max-per-hour: ${CONTACT_MAX_PER_HOUR:5}
  seed:
    enabled: ${SEED_INITIAL_DATA:true}
  mail:
    enabled: ${MAIL_ENABLED:false}
    from: ${MAIL_FROM:}
    notify-to: ${CONTACT_NOTIFY_EMAIL:}
    poll-interval-ms: ${MAIL_POLL_INTERVAL_MS:30000}

---
spring:
  config:
    activate:
      on-profile: production

mlb:
  security:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:https://mufaddalbatti53.github.io}
```

## `backend/src/test/java/com/mlb/portfolio/controller/ContactControllerTest.java`

```java
package com.mlb.portfolio.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.ContactResponse;
import com.mlb.portfolio.exception.GlobalExceptionHandler;
import com.mlb.portfolio.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ContactControllerTest {
    private MockMvc mvc;
    private ContactService service;
    @BeforeEach
    void setUp() {
        service = mock(ContactService.class);
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        when(resolver.resolve(any())).thenReturn("203.0.113.4");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ContactController(service, resolver))
            .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }
    @Test
    void acceptsValidRequest() throws Exception {
        when(service.submit(any(), anyString())).thenReturn(
            new ContactResponse("c81e8760-b0fb-4ad3-9f21-b7aa7d212f01", "Your message has been received."));
        mvc.perform(post("/api/v1/contact")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"name":"Visitor","email":"visitor@example.org","subject":"Hello","message":"Message","website":"","privacyAccepted":true}
                    """))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.referenceId").exists());
    }
    @Test
    void rejectsInvalidEmailWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/contact")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"name":"Visitor","email":"invalid","subject":"Hello","message":"Message","website":"","privacyAccepted":true}
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        verifyNoInteractions(service);
    }
}
```

## `backend/src/test/java/com/mlb/portfolio/controller/FeedbackControllerTest.java`

```java
package com.mlb.portfolio.controller;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.FeedbackResponse;
import com.mlb.portfolio.exception.GlobalExceptionHandler;
import com.mlb.portfolio.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FeedbackControllerTest {
    @Test void acceptsValidatedFeedback() throws Exception {
        var service=mock(FeedbackService.class);var resolver=mock(ClientIpResolver.class);
        when(resolver.resolve(any())).thenReturn("203.0.113.4");
        when(service.submit(any(),anyString())).thenReturn(new FeedbackResponse("Thanks for your feedback."));
        var validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        var mvc=MockMvcBuilders.standaloneSetup(new FeedbackController(service,resolver))
            .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":5,\"category\":\"UI_UX\",\"comment\":\"Beautiful\",\"website\":\"\"}"))
            .andExpect(status().isAccepted());
        verify(service).submit(any(),eq("203.0.113.4"));
    }
    @Test void rejectsInvalidRatingBeforePersistence() throws Exception {
        var service=mock(FeedbackService.class);var resolver=mock(ClientIpResolver.class);
        var validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        var mvc=MockMvcBuilders.standaloneSetup(new FeedbackController(service,resolver))
            .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":8,\"category\":\"BUG\",\"comment\":\"bad\",\"website\":\"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
```

## `backend/src/test/java/com/mlb/portfolio/service/AdminAuthServiceTest.java`

```java
package com.mlb.portfolio.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.dto.LoginRequest;
import com.mlb.portfolio.repository.AdminSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AdminAuthServiceTest {
    @Test void issuesRevocableShortLivedJwtOnValidCredentials() {
        var sessions=mock(AdminSessionRepository.class);
        var throttle=mock(AbuseProtectionService.class);
        var encoder=new BCryptPasswordEncoder(12);
        var hash=encoder.encode("Strong secret used only in test");
        String key="independent-testing-key-with-32-bytes-minimum";
        when(sessions.existsByTokenIdDigestAndRevokedAtIsNullAndExpiresAtAfter(anyString(),any())).thenReturn(true);
        var auth=new AdminAuthService(sessions,throttle,encoder,"mlbadmin",hash,key,"mlb-portfolio",15);
        String token=auth.login(new LoginRequest("mlbadmin","Strong secret used only in test"),"203.0.113.7");
        assertNotNull(auth.validate(token));
        verify(sessions).save(any());
        verify(throttle).check(eq("admin-login-ip"),anyString(),eq(5),eq(900L));
    }
    @Test void refusesIncorrectPassword() {
        var encoder=new BCryptPasswordEncoder(12);
        var auth=new AdminAuthService(mock(AdminSessionRepository.class),mock(AbuseProtectionService.class),encoder,
            "mlbadmin",encoder.encode("Real test-only secret"),
            "independent-testing-key-with-32-bytes-minimum","mlb-portfolio",15);
        var error=assertThrows(ResponseStatusException.class,
            ()->auth.login(new LoginRequest("mlbadmin","incorrect"),"203.0.113.7"));
        assertEquals(HttpStatus.UNAUTHORIZED,error.getStatusCode());
    }
}
```

## `backend/src/test/java/com/mlb/portfolio/service/ContactServiceTest.java`

```java
package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.dto.ContactRequest;
import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.repository.ContactMessageRepository;
import org.junit.jupiter.api.Test;

class ContactServiceTest {
    private final ContactMessageRepository repository = mock(ContactMessageRepository.class);
    private final RateLimitService rateLimit = mock(RateLimitService.class);
    private final ContactService service = new ContactService(repository, rateLimit,
        new ContentSanitizer(), 90);

    @Test
    void honeypotIsAcknowledgedWithoutPersistence() {
        var request = new ContactRequest("Visitor", "visitor@example.org", "Hello", "Message", "spam.site", true);
        var result = service.submit(request, "198.51.100.8");
        assertNotNull(result.referenceId());
        verify(rateLimit).check("198.51.100.8");
        verifyNoInteractions(repository);
    }

    @Test
    void validMessageIsSanitizedBeforeStorage() {
        var request = new ContactRequest("Visitor", "VISITOR@EXAMPLE.ORG", "Hello",
            "<b>Thank you</b>", "", true);
        var result = service.submit(request, "198.51.100.9");
        assertNotNull(result.referenceId());
        verify(repository).save(argThat(saved ->
            "visitor@example.org".equals(saved.getEmail()) &&
            "Thank you".equals(saved.getMessage())));
    }
}
```

## `backend/src/test/java/com/mlb/portfolio/service/ExperienceServiceTest.java`

```java
package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlb.portfolio.dto.ExperienceRequest;
import com.mlb.portfolio.mapper.ExperienceMapper;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.repository.ExperienceRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ExperienceServiceTest {
    private static ExperienceService service(ExperienceRepository repository) {
        return new ExperienceService(repository, new ExperienceMapper(), new ContentSanitizer());
    }

    private static ExperienceRequest request(LocalDate endDate, boolean current) {
        return new ExperienceRequest(
            "Software Developer Intern",
            "Web Graphics Studio",
            "Internship · Part-Time",
            "Remote (Work From Home)",
            "Remote · India",
            LocalDate.of(2026, 1, 1),
            endDate,
            current,
            "Built responsive interfaces and order workflows.",
            List.of("Delivered client-side UI components."),
            List.of("HTML5", "CSS3", "JavaScript"),
            10,
            true
        );
    }

    @Test
    void publicListMapsPublishedExperiences() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        when(repository.findByPublishedTrueOrderBySortOrderAscStartDateDesc()).thenReturn(List.of());

        assertEquals(List.of(), service(repository).publicList());
        verify(repository).findByPublishedTrueOrderBySortOrderAscStartDateDesc();
    }

    @Test
    void pastExperienceRequiresEndDate() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        assertThrows(ResponseStatusException.class, () -> service(repository).create(request(null, false)));
    }

    @Test
    void currentExperienceRejectsEndDate() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        assertThrows(ResponseStatusException.class, () -> service(repository).create(
            request(LocalDate.of(2026, 6, 30), true)));
    }

    @Test
    void validExperiencePersists() {
        ExperienceRepository repository = mock(ExperienceRepository.class);
        when(repository.save(any(Experience.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository).create(request(LocalDate.of(2026, 6, 30), false));
        verify(repository).save(any(Experience.class));
    }
}
```

## `backend/src/test/java/com/mlb/portfolio/service/FeedbackServiceTest.java`

```java
package com.mlb.portfolio.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.dto.FeedbackRequest;
import com.mlb.portfolio.model.SiteFeedback;
import com.mlb.portfolio.repository.SiteFeedbackRepository;
import org.junit.jupiter.api.Test;

class FeedbackServiceTest {
    @Test void honeypotNeverPersists() {
        var repository=mock(SiteFeedbackRepository.class);
        var throttle=mock(AbuseProtectionService.class);
        var sanitizer=mock(ContentSanitizer.class);
        var service=new FeedbackService(repository,throttle,sanitizer,90);
        var response=service.submit(new FeedbackRequest(5,SiteFeedback.Category.GENERAL,"Good","spam.example"),"203.0.113.4");
        assertEquals("Thanks for your feedback.",response.message());
        verify(throttle).check(eq("feedback"),anyString(),eq(10),eq(3600L));
        verifyNoInteractions(repository);
    }
}
```

## `backend/src/test/java/com/mlb/portfolio/service/ProjectServiceTest.java`

```java
package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mlb.portfolio.mapper.ProjectMapper;
import com.mlb.portfolio.repository.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class ProjectServiceTest {
    private static ProjectService service(ProjectRepository repository) {
        return new ProjectService(repository, new ProjectMapper(new ContentSanitizer()));
    }

    @Test
    void listReturnsOnlyMappedResponseFields() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectService service = service(repository);
        when(repository.findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(any(Pageable.class)))
            .thenReturn(List.of());

        assertEquals(List.of(), service.list(6));
        verify(repository).findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(argThat(page ->
            page.getPageNumber() == 0 && page.getPageSize() == 6));
    }

    @Test
    void missingOrUnpublishedProjectDoesNotLeak() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectService service = service(repository);
        when(repository.findBySlugAndPublishedTrue("private-draft"))
            .thenReturn(java.util.Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.get("private-draft"));
    }
}
```

## `backend/src/test/java/com/mlb/portfolio/service/RateLimitServiceTest.java`

```java
package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.exception.RateLimitExceededException;
import com.mlb.portfolio.repository.RateLimitCounterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {
    @Test
    void rejectsAttemptAboveConfiguredWindowCount() {
        RateLimitCounterRepository repository = mock(RateLimitCounterRepository.class);
        when(repository.increment(anyString(), any(), any())).thenReturn(6);
        RateLimitService service = new RateLimitService(repository,
            "this-is-a-valid-example-test-secret-123456", 5,
            Clock.fixed(Instant.parse("2026-11-16T12:30:00Z"), ZoneOffset.UTC));
        var error = assertThrows(RateLimitExceededException.class,
            () -> service.check("203.0.113.4"));
        assertEquals(1800, error.getRetryAfterSeconds());
        verify(repository).increment(argThat(id -> id.startsWith("contact:") &&
            !id.contains("203.0.113.4")), any(), any());
    }
    @Test
    void refusesWeakSecret() {
        assertThrows(IllegalArgumentException.class,
            () -> new RateLimitService(mock(RateLimitCounterRepository.class), "short", 5,
                Clock.systemUTC()));
    }
}
```

## `compose.dev.yaml`

```yaml
services:
  web:
    ports:
      - "127.0.0.1:${WEB_PORT:-8080}:8080"
```

## `compose.prod.yaml`

```yaml
services:
  gateway:
    image: caddy:2.10-alpine
    restart: unless-stopped
    environment:
      SITE_HOST: ${SITE_HOST:?Set the public domain for automatic HTTPS}
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - ./deploy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on: [web]
    networks:
      private:
        ipv4_address: 172.29.77.10
volumes:
  caddy_data:
  caddy_config:
```

## `compose.yaml`

```yaml
services:
  mongo:
    image: mongo:7.0
    restart: unless-stopped
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_ROOT_USER:?Set MONGO_ROOT_USER}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_ROOT_PASSWORD:?Set MONGO_ROOT_PASSWORD}
    volumes:
      - mongo_data:/data/db
    networks: [private]
    healthcheck:
      test:
        - CMD-SHELL
        - >-
          mongosh --quiet -u "$${MONGO_INITDB_ROOT_USERNAME}" -p "$${MONGO_INITDB_ROOT_PASSWORD}"
          --authenticationDatabase admin --eval 'db.adminCommand({ping:1}).ok' | grep -q 1
      interval: 15s
      timeout: 8s
      retries: 10
  backend:
    build: ./backend
    restart: unless-stopped
    depends_on:
      mongo: {condition: service_healthy}
    environment:
      MONGODB_URI: ${MONGODB_URI:?Set an authenticated Mongo URI}
      RATE_LIMIT_HMAC_SECRET: ${RATE_LIMIT_HMAC_SECRET:?Set a 32+ character HMAC secret}
      TRUSTED_PROXY_CIDRS: ${TRUSTED_PROXY_CIDRS:?Set the internal Nginx proxy CIDR}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}
      SEED_INITIAL_DATA: ${SEED_INITIAL_DATA:-true}
      CONTACT_RETENTION_DAYS: ${CONTACT_RETENTION_DAYS:-90}
      CONTACT_MAX_PER_HOUR: ${CONTACT_MAX_PER_HOUR:-5}
      FEEDBACK_RETENTION_DAYS: ${FEEDBACK_RETENTION_DAYS:-90}
      ADMIN_USERNAME: ${ADMIN_USERNAME:?Set admin username}
      ADMIN_PASSWORD_HASH: ${ADMIN_PASSWORD_HASH:?Set BCrypt admin password hash}
      JWT_SECRET: ${JWT_SECRET:?Set a 32+ character JWT signing secret}
      JWT_TTL_MINUTES: ${JWT_TTL_MINUTES:-15}
      MAIL_ENABLED: ${MAIL_ENABLED:-false}
      SMTP_HOST: ${SMTP_HOST:-localhost}
      SMTP_PORT: ${SMTP_PORT:-587}
      SMTP_USERNAME: ${SMTP_USERNAME:-}
      SMTP_PASSWORD: ${SMTP_PASSWORD:-}
      SMTP_AUTH: ${SMTP_AUTH:-true}
      SMTP_STARTTLS: ${SMTP_STARTTLS:-true}
      MAIL_FROM: ${MAIL_FROM:-}
      CONTACT_NOTIFY_EMAIL: ${CONTACT_NOTIFY_EMAIL:-}
      MAIL_POLL_INTERVAL_MS: ${MAIL_POLL_INTERVAL_MS:-30000}
    networks: [private]
    healthcheck:
      test:
        - CMD-SHELL
        - >-
          curl --fail --silent http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
      interval: 20s
      timeout: 5s
      retries: 5
      start_period: 20s
  web:
    build:
      context: .
      dockerfile: frontend/Dockerfile
      args:
        SITE_BASE_URL: ${SITE_BASE_URL:?Set the canonical URL}
        SITE_DISPLAY_NAME: ${SITE_DISPLAY_NAME:?Set the portfolio owner name}
        SITE_EMAIL: ${SITE_EMAIL:?Set the professional email}
        SITE_LOCATION: ${SITE_LOCATION:?Set the public location}
        SITE_GITHUB: ${SITE_GITHUB:?Set a GitHub profile URL}
        SITE_LINKEDIN: ${SITE_LINKEDIN:?Set a LinkedIn profile URL}
        BUILD_MODE: ${BUILD_MODE:-production}
    restart: unless-stopped
    depends_on:
      backend: {condition: service_healthy}
    networks:
      private:
        ipv4_address: 172.29.77.11
networks:
  private:
    driver: bridge
    ipam:
      config:
        - subnet: 172.29.77.0/24
volumes:
  mongo_data:
```

## `deploy/Caddyfile`

```text
{$SITE_HOST} {
    encode zstd gzip
    reverse_proxy web:8080
    header {
        Strict-Transport-Security "max-age=31536000"
        X-Content-Type-Options nosniff
    }
}
```

## `deploy/nginx.conf`

```nginx
map $uri $admin_robots {
  default "";
  /admin.html "noindex, nofollow, noarchive";
}
map $uri $contact_limit_key {
  default "";
  /api/v1/contact $binary_remote_addr;
  /api/v1/feedback $binary_remote_addr;
  /api/v1/auth/login $binary_remote_addr;
}
limit_req_zone $contact_limit_key zone=contact_ip:10m rate=10r/m;

server {
  listen 8080;
  # Trust production gateway
  set_real_ip_from 172.29.77.10;
  real_ip_header X-Forwarded-For;
  server_name _;
  root /usr/share/nginx/html;
  index index.html;
  server_tokens off;
  autoindex off;
  charset utf-8;
  client_max_body_size 8k;

  add_header X-Robots-Tag $admin_robots always;
  add_header X-Content-Type-Options "nosniff" always;
  add_header X-Frame-Options "DENY" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin" always;
  add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
  add_header Content-Security-Policy "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self'; font-src 'self'; connect-src 'self'; img-src 'self' https: data:; manifest-src 'self'" always;
  add_header Strict-Transport-Security "max-age=31536000" always;

  error_page 404 /404.html;
  error_page 413 = @payload_too_large;
  error_page 429 = @too_many_requests;
  location @payload_too_large {
    default_type application/problem+json;
    return 413 '{"type":"about:blank","title":"Payload too large","status":413,"detail":"The request body exceeds 8 KB."}';
  }
  location @too_many_requests {
    default_type application/problem+json;
    return 429 '{"type":"about:blank","title":"Rate limited","status":429,"detail":"Too many requests."}';
  }
  location = /admin.html { try_files /admin.html =404; }
  location = /404.html { try_files /404.html =404; }
  location = /healthz { access_log off; add_header Content-Type text/plain; return 200 "ok"; }
  location /api/ {
    limit_req zone=contact_ip burst=20 nodelay;
    limit_req_status 429;
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Connection "";
    proxy_connect_timeout 5s;
    proxy_read_timeout 30s;
    proxy_send_timeout 30s;
  }
  location = /actuator/health {
    access_log off;
    proxy_pass http://backend:8080/actuator/health;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Connection "";
    proxy_connect_timeout 3s;
    proxy_read_timeout 5s;
  }
  location /actuator/ { return 404; }
  location ~ /\. { deny all; }
  location ~* \.(?:css|js|ico|png|svg|webp)$ {
    expires 7d;
    try_files $uri =404;
  }
  location / { try_files $uri $uri/ =404; }
}
```

## `frontend/404.html`

```html
<!doctype html>
<html lang="en" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; connect-src 'self' http://localhost:8080 https://*.onrender.com; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'">
  <meta name="mlb-api-origin" content="">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" content="#0A0E17">
  <meta name="description" content="This MLB portfolio page could not be found. Return to the homepage to explore engineering projects, writing and contact information.">
  <meta name="robots" content="noindex,follow">
  <title>404 — Page Not Found | MLB Portfolio</title>
  <link rel="canonical" href="https://mufaddalbatti53.github.io/MLB-Portfolio/404.html">
  <base href="https://mufaddalbatti53.github.io/MLB-Portfolio/">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Mufaddal Battiwala Portfolio">
  <meta property="og:title" content="404 — Page Not Found | MLB Portfolio">
  <meta property="og:description" content="This MLB portfolio page could not be found. Return to the homepage to explore engineering projects, writing and contact information.">
  <meta property="og:url" content="https://mufaddalbatti53.github.io/MLB-Portfolio/404.html">
  <meta property="og:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <meta property="og:image:alt" content="Mufaddal Battiwala full stack developer portfolio">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="404 — Page Not Found | MLB Portfolio">
  <meta name="twitter:description" content="This MLB portfolio page could not be found. Return to the homepage to explore engineering projects, writing and contact information.">
  <meta name="twitter:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <link rel="icon" href="./assets/favicon.ico" sizes="any">
  <link rel="icon" href="./assets/favicon.svg" type="image/svg+xml">
  <link rel="icon" href="./assets/favicon-32x32.png" type="image/png" sizes="32x32">
  <link rel="apple-touch-icon" href="./assets/apple-touch-icon.png">
  <link rel="manifest" href="./site.webmanifest">
  <script src="./js/theme-init.js"></script>
  <link rel="stylesheet" href="./css/main.css">
</head>
<body>

<a class="skip-link" href="#main-content">Skip to main content</a>
<header class="site-header">
  <div class="container site-header__inner">
    <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
    <button class="nav-toggle" id="nav-toggle" type="button" aria-label="Toggle navigation" aria-controls="site-nav" aria-expanded="false">Menu ☰</button>
    <nav class="site-nav" id="site-nav" aria-label="Primary navigation">
      <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
      <a href="./#skills">Expertise</a>
      <a href="./#blog">Writing</a>
      <a href="./#contact">Contact</a>
      <div class="mobile-theme-panel" aria-label="Choose theme">
        <span class="mobile-theme-panel__label">Theme</span>
        <div class="mobile-theme-options" role="group" aria-label="Theme options">
          <button class="mobile-theme-option" type="button" data-theme-choice="light" aria-pressed="false"><span aria-hidden="true">☀</span> Light</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dusk" aria-pressed="false"><span aria-hidden="true">◐</span> Dusk</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dark" aria-pressed="false"><span aria-hidden="true">☾</span> Dark</button>
        </div>
      </div>
    </nav>
<div class="header-utilities" role="group" aria-label="Site tools"><button class="utility-button" type="button" data-open-search aria-label="Search portfolio (Ctrl+K)" title="Search (Ctrl+K)"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></svg></button><button class="utility-button theme-toggle" type="button" data-theme-toggle aria-label="Theme: Dark. Switch to Light." title="Theme: Dark · Next: Light"><span class="theme-toggle__icon" data-theme-icon aria-hidden="true">☾</span><span class="theme-toggle__caption" data-theme-caption>Dark</span></button><button class="utility-button utility-button--feedback" type="button" data-open-feedback aria-label="Give site feedback"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l2-3A8.5 8.5 0 1 1 21 11.5Z"/><path d="M8 10h8M8 14h5"/></svg><span>Feedback</span></button></div>
    <a class="button button--small button--secondary nav-action" href="./#contact">Let's connect <span aria-hidden="true">↗</span></a>
  </div>
</header>


<main id="main-content" class="status-page"><div class="container status-panel">
  <div class="status-panel__symbol" aria-hidden="true">404</div>
  <span class="eyebrow">Lost in the source</span>
  <h1>That page isn't here.</h1>
  <p>The link may have moved, or the address might not be quite right. The good stuff is still just a click away.</p>
  <a href="./" class="button button--primary">Back to homepage <span aria-hidden="true">↗</span></a>
</div></main>


<footer class="site-footer">
  <div class="container">
    <div class="footer-main">
      <div>
        <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
        <p>Thoughtful engineering, practical solutions, and a focus on building better digital experiences.</p>
        <p><a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a><span aria-hidden="true"> · </span>Kolkata, India</p>
      </div>
      <nav class="footer-nav" aria-label="Footer navigation">
        <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
        <a href="./#skills">Expertise</a>
        <a href="./#blog">Writing</a>
        <a href="./#contact">Contact</a>
        <a href="https://github.com/mufaddalbatti53" data-profile-github target="_blank" rel="noopener noreferrer">GitHub ↗</a>
        <a href="https://www.linkedin.com/in/mufaddal-tech/" data-profile-linkedin target="_blank" rel="noopener noreferrer">LinkedIn ↗</a>
      </nav>
    </div>
    <div class="footer-bottom">
      <span>© <span id="copyright-year">2026</span> MLB. Built with care.</span>
      <div class="footer-bottom-links"><a href="./privacy.html">Privacy</a><a href="./terms.html">Terms</a><a href="mailto:mufaddal.techpro@gmail.com">Email</a></div>
    </div>
  </div>
</footer>
<div class="mobile-cta"><span>Have an idea in mind?</span><a class="button button--primary" href="./#contact">Let's talk <span aria-hidden="true">↗</span></a></div>
<aside class="cookie-banner" id="cookie-banner" hidden aria-label="Analytics consent preferences">
  <div class="cookie-banner__body">
    <div><strong>Your privacy matters</strong><p>This site only stores your analytics preference locally. Optional analytics is inactive by default and only runs with permission. <a href="./privacy.html">Privacy details</a>.</p></div>
    <div class="cookie-banner__actions"><button type="button" class="button button--secondary" data-consent="rejected">Decline</button><button type="button" class="button button--primary" data-consent="accepted">Accept</button></div>
  </div>
</aside>
<dialog id="site-search" class="app-modal" aria-labelledby="search-title"><div class="modal-body"><div class="modal-heading"><h2 id="search-title">Search MLB</h2><button class="utility-button" data-close-search type="button" aria-label="Close search">✕</button></div><label for="search-input">Find projects, experience, skills and articles</label><input type="search" id="search-input" autocomplete="off" aria-controls="search-results"/><div id="search-results" class="search-results" role="region" aria-live="polite"></div><small>Press Ctrl+K to open · Esc to close</small></div></dialog>
<dialog id="site-feedback" class="app-modal" aria-labelledby="feedback-title"><div class="modal-body"><div class="modal-heading"><h2 id="feedback-title">Site feedback</h2><button class="utility-button" data-close-feedback type="button" aria-label="Close feedback">✕</button></div><p>Help improve the MLB portfolio experience.</p><form id="feedback-form"><fieldset class="stars"><legend>How was your experience?</legend><label><input type="radio" name="rating" value="1" required/><span aria-label="1 stars">★ 1</span></label><label><input type="radio" name="rating" value="2" required/><span aria-label="2 stars">★ 2</span></label><label><input type="radio" name="rating" value="3" required/><span aria-label="3 stars">★ 3</span></label><label><input type="radio" name="rating" value="4" required/><span aria-label="4 stars">★ 4</span></label><label><input type="radio" name="rating" value="5" required/><span aria-label="5 stars">★ 5</span></label></fieldset><label class="field">Category <select name="category" required><option value="UI_UX">UI/UX</option><option value="BUG">Bug</option><option value="FEATURE_IDEA">Feature Idea</option><option value="GENERAL">General</option></select></label><label class="field">Comment (optional)<textarea name="comment" maxlength="500" rows="4"></textarea></label><div class="honeypot" aria-hidden="true"><label for="feedback-website">Leave empty</label><input id="feedback-website" name="website" maxlength="200" tabindex="-1" autocomplete="off"/></div><p role="status" aria-live="polite" id="feedback-status"></p><button class="button button--primary" type="submit">Send feedback</button></form></div></dialog>
<script type="module" src="./js/main.js"></script>

</body>
</html>
```

## `frontend/Dockerfile`

```text
FROM python:3.13-alpine AS site-builder
WORKDIR /source
COPY frontend ./frontend
COPY scripts ./scripts
ARG SITE_BASE_URL
ARG SITE_DISPLAY_NAME
ARG SITE_EMAIL
ARG SITE_LOCATION
ARG SITE_GITHUB
ARG SITE_LINKEDIN
ARG BUILD_MODE=production
ENV SITE_BASE_URL=${SITE_BASE_URL} \
    SITE_DISPLAY_NAME=${SITE_DISPLAY_NAME} \
    SITE_EMAIL=${SITE_EMAIL} \
    SITE_LOCATION=${SITE_LOCATION} \
    SITE_GITHUB=${SITE_GITHUB} \
    SITE_LINKEDIN=${SITE_LINKEDIN}
RUN python scripts/build_site.py --mode "${BUILD_MODE}" --output /site

FROM nginx:1.28-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=site-builder /site/ /usr/share/nginx/html/
EXPOSE 8080
```

## `frontend/admin.html`

```html
<!doctype html>
<html lang="en" data-theme="dark">
<head><meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; connect-src 'self' http://localhost:8080 https://*.onrender.com; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'">
  <meta name="mlb-api-origin" content=""><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow,noarchive"><meta name="description" content="Private MLB portfolio administration sign-in and dashboard for authorized owner access only."><title>Admin Sign In | MLB Portfolio</title><link rel="canonical" href="https://mufaddalbatti53.github.io/MLB-Portfolio/admin.html"><meta property="og:title" content="Admin Sign In | MLB Portfolio"><meta property="og:description" content="Private owner dashboard"><meta property="og:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png"><meta property="og:url" content="https://mufaddalbatti53.github.io/MLB-Portfolio/admin.html"><meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="MLB Admin"><meta name="twitter:description" content="Private dashboard"><meta name="twitter:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png"><link rel="icon" href="./assets/favicon.svg" type="image/svg+xml"><link rel="apple-touch-icon" href="./assets/apple-touch-icon.png"><script src="./js/theme-init.js"></script><link rel="stylesheet" href="./css/main.css"><link rel="stylesheet" href="./css/admin.css"></head>
<body class="admin-body"><a href="#admin-main" class="skip-link">Skip to dashboard</a><header class="site-header"><div class="container site-header__inner"><a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a><div class="header-utilities"><button class="utility-button theme-toggle" type="button" data-theme-toggle aria-label="Theme: Dark. Switch to Light." title="Theme: Dark · Next: Light"><span class="theme-toggle__icon" data-theme-icon aria-hidden="true">☾</span><span class="theme-toggle__caption" data-theme-caption>Dark</span></button><a href="./" class="button button--secondary button--small">View site</a></div></div></header>
<main class="container admin-shell" id="admin-main"><section id="login-panel" class="admin-login" aria-labelledby="login-title"><div class="admin-login-logo"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></div><h1 id="login-title">Owner sign in</h1><p>Secure access to manage your portfolio and visitor messages.</p><form id="login-form"><label for="admin-user">Username</label><input id="admin-user" name="username" autocomplete="username" maxlength="64" required/><label for="admin-pass">Password</label><input id="admin-pass" name="password" type="password" autocomplete="current-password" maxlength="256" required/><button type="submit" class="button button--primary">Sign in</button><p id="login-status" role="alert" aria-live="polite"></p></form></section>
<section id="dashboard-panel" hidden aria-label="Portfolio administration"><div class="admin-layout"><nav class="admin-sidebar" aria-label="Admin sections"><div class="admin-sidebar-logo"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></div><h2>Manage MLB</h2><button class="admin-tab" data-tab="dashboard" type="button">Overview</button><button class="admin-tab" data-tab="projects" type="button">Projects</button><button class="admin-tab" data-tab="experiences" type="button">Experience</button><button class="admin-tab" data-tab="blog" type="button">Blog Posts</button><button class="admin-tab" data-tab="messages" type="button">Contact Inbox</button><button class="admin-tab" data-tab="feedback" type="button">Feedback</button><button class="admin-tab" data-tab="profile" type="button">Profile</button><button id="admin-logout" type="button" class="button button--secondary">Sign out</button></nav><div class="admin-content"><div class="admin-top"><div><span class="eyebrow">MLB / CONTROL CENTER</span><h1 id="admin-section-title">Dashboard</h1></div><button id="admin-refresh" class="button button--secondary" type="button">Refresh data</button></div><p id="admin-status" role="status" aria-live="polite"></p><div id="admin-collection" aria-live="polite"></div><section id="admin-editor-panel" hidden aria-labelledby="admin-editor-title"><h2 id="admin-editor-title">Edit content</h2><p id="admin-editor-help" class="admin-editor-help">Use the editor to manage structured portfolio content.</p><form id="admin-editor"></form></section></div></div></section></main>
<script type="module" src="./js/admin.js"></script></body></html>
```

## `frontend/assets/favicon.svg`

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 192 192" role="img" aria-labelledby="icon-title"><title id="icon-title">MLB favicon</title><defs><linearGradient id="brand" x1="0%" y1="0%" x2="100%" y2="70%"><stop offset="0%" stop-color="#3B66F5"/><stop offset="53%" stop-color="#7A5AF8"/><stop offset="100%" stop-color="#C84B9E"/></linearGradient></defs><rect x="2" y="2" width="188" height="188" rx="40" fill="#0B0F19"/><g transform="translate(5 48)"><path d="M20 72 V24 L42 50 L64 24 V72" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M83 24 V72 H106" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M119 72 V24 H141 C157 24 160 42 144 47 H119 M119 47 H144 C162 47 162 72 140 72 H119" fill="none" stroke="url(#brand)" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/></g></svg>
```

## `frontend/assets/logo.svg`

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 96" role="img" aria-labelledby="logo-title"><title id="logo-title">MLB monogram</title><defs><linearGradient id="brand" x1="0%" y1="0%" x2="100%" y2="70%"><stop offset="0%" stop-color="#3B66F5"/><stop offset="53%" stop-color="#7A5AF8"/><stop offset="100%" stop-color="#C84B9E"/></linearGradient></defs><path d="M20 72 V24 L42 50 L64 24 V72" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M83 24 V72 H106" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M119 72 V24 H141 C157 24 160 42 144 47 H119 M119 47 H144 C162 47 162 72 140 72 H119" fill="none" stroke="url(#brand)" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/></svg>
```

## `frontend/css/admin.css`

```css
.admin-body { min-height: 100vh; }
.admin-shell { padding-top: 42px; padding-bottom: 90px; }
.admin-login { width: min(480px,100%); margin: min(12vh,100px) auto; border: 1px solid var(--border-subtle); background: var(--surface-card); border-radius: var(--radius-lg); padding: clamp(24px,5vw,40px); box-shadow: var(--shadow-card); }
.admin-login h1 { margin: 0 0 10px; font-size: 2rem; }
.admin-login p { color: var(--text-muted); }
.admin-login label { display: block; font-size: .87rem; margin: 20px 0 7px; }
.admin-login input, .admin-editor input, .admin-editor textarea, .admin-editor select { width: 100%; border: 1px solid var(--border-hover); background: var(--surface-input); color: var(--text-main); border-radius: 10px; padding: 11px 12px; }
.admin-login .button { margin-top: 23px; width: 100%; justify-content: center; }
.admin-layout { display: grid; grid-template-columns: 230px minmax(0,1fr); gap: 34px; }
.admin-sidebar { display: flex; flex-direction: column; gap: 7px; align-self: start; position: sticky; top: 110px; border: 1px solid var(--border-subtle); background: var(--surface-card); border-radius: 18px; padding: 18px; }
.admin-sidebar h2 { font-size: 1rem; }
.admin-tab { text-align: left; background: transparent; color: var(--text-muted); border: 1px solid transparent; border-radius: 9px; padding: 10px 13px; }
.admin-tab[aria-current="page"] { color: var(--text-main); background: var(--surface-raised); border-color: var(--border-hover); }
.admin-content { min-width: 0; }
.admin-top { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 25px; }
.admin-top h1 { margin: 4px 0; font-size: clamp(1.6rem,4vw,2.7rem); }
.admin-stats { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 18px; }
.admin-stat, .admin-card { border: 1px solid var(--border-subtle); background: var(--surface-card); border-radius: 15px; padding: 20px; }
.admin-stat strong { display: block; font-size: 2rem; background: var(--brand-gradient); color: transparent; background-clip: text; }
.admin-stat span, .admin-card p, .admin-card small { color: var(--text-muted); }
.admin-items { display: grid; gap: 13px; }
.admin-card h3 { margin: 0; }
.admin-card__actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 15px; }
.admin-card__actions button { border: 1px solid var(--border-hover); background: var(--surface-raised); color: var(--text-main); padding: 8px 12px; border-radius: 8px; }
.admin-toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; align-items: center; }
#admin-editor-panel { margin-top: 30px; padding: 24px; background: var(--surface-card); border: 1px solid var(--border-subtle); border-radius: 16px; }
.admin-editor { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.admin-editor label { display: flex; flex-direction: column; font-size: .84rem; gap: 6px; }
.admin-editor label:has(textarea), .admin-editor-actions { grid-column: 1 / -1; }
.admin-editor .admin-check { flex-direction: row; align-items: center; }
.admin-editor .admin-check input { width: auto; }
.admin-editor-actions { display: flex; gap: 12px; margin-top: 10px; }
#admin-status { min-height: 1.5rem; color: var(--text-muted); }
.admin-pagination { display: flex; gap: 12px; margin-top: 20px; }
.admin-pagination button { padding: 9px 12px; border: 1px solid var(--border-hover); background: var(--surface-raised); color: var(--text-main); border-radius: 8px; }
@media(max-width: 768px) { .admin-layout { grid-template-columns: 1fr; }.admin-sidebar { position: static; flex-direction: row; flex-wrap: wrap; }.admin-sidebar h2 { width: 100%; }.admin-top { align-items: start; }.admin-editor { grid-template-columns: 1fr; } }
@media(max-width: 375px) { .admin-stats { grid-template-columns: 1fr; } }
.admin-login-logo { margin-bottom: 18px; }
.admin-login-logo .brand__svg { width: 90px; height: 48px; }
.admin-sidebar-logo .brand__svg { width: 65px; height: 38px; }
.admin-editor-help { margin: -4px 0 18px; color: var(--text-muted); line-height: 1.65; }
.admin-field-hint { color: var(--text-dim); line-height: 1.5; }
.admin-editor textarea[name="problemStatement"],
.admin-editor textarea[name="architectureOverview"] { min-height: 130px; resize: vertical; }
.admin-editor textarea[name="architectureFlow"],
.admin-editor textarea[name="keyMetrics"] { min-height: 150px; resize: vertical; font-family: var(--font-code); font-size: .82rem; }
```

## `frontend/css/main.css`

```css
@import url('./variables.css');
*, *::before, *::after { box-sizing: border-box; }
html { scroll-behavior: smooth; scroll-padding-top: 100px; }
body { margin: 0; min-width: 320px; background: var(--page-background); color: var(--text-main); font: 400 16px/1.65 var(--font-body); -webkit-font-smoothing: antialiased; }
::selection { background: var(--selection-bg); color: var(--selection-text); }
a { color: inherit; text-decoration: none; }
img { max-width: 100%; height: auto; }
button, input, select, textarea { font: inherit; }
button { cursor: pointer; }
:focus-visible { outline: 3px solid var(--focus-ring); outline-offset: 4px; }
.skip-link { position: fixed; z-index: 100; top: -100px; left: 15px; padding: 10px 18px; border-radius: 8px; background: var(--text-main); color: var(--surface-base); }
.skip-link:focus { top: 12px; }
.container { width: min(var(--container-width), calc(100% - 48px)); margin-inline: auto; }
.site-header { position: sticky; top: 0; z-index: 40; backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px); background: var(--header-bg); border-bottom: 1px solid var(--header-border); }
.site-header__inner { min-height: 78px; display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.brand { display: inline-flex; align-items: center; }
.brand__mark { display: grid; place-items: center; color: #fff; width: 40px; height: 40px; font-size: .96rem; border-radius: 12px; background: var(--brand-gradient); box-shadow: var(--brand-shadow); letter-spacing: -.08em; }
.site-nav { display: flex; align-items: center; gap: clamp(16px, 2.5vw, 34px); }
.site-nav a { color: var(--text-muted); font-size: .88rem; font-weight: 550; transition: color var(--transition-fast); }
.site-nav a:hover, .site-nav a[aria-current='page'] { color: var(--text-main); }
.nav-toggle { display: none; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); color: var(--text-main); background: var(--surface-card); padding: 8px 12px; }
.nav-action { display: inline-flex; }
.button { display: inline-flex; justify-content: center; align-items: center; gap: 10px; font-weight: 690; font-size: .91rem; line-height: 1.2; padding: 15px 22px; border-radius: 11px; border: 1px solid transparent; transition: transform var(--transition-fast), box-shadow var(--transition-fast), background var(--transition-fast); text-align: center; }
.button:hover { transform: translateY(-2px); }
.button:active { transform: translateY(0); }
.button--primary { color: white; background: var(--brand-gradient); box-shadow: var(--primary-shadow); }
.button--primary:hover { box-shadow: var(--primary-shadow-hover); }
.button--secondary { background: var(--surface-card); color: var(--text-main); border-color: var(--border-hover); }
.button--secondary:hover { background: var(--surface-raised); }
.button--small { padding: 12px 16px; font-size: .84rem; }
.button[disabled] { cursor: not-allowed; opacity: .68; transform: none; }
.icon-inline { display: inline-block; width: 1em; text-align: center; }
.hero { isolation: isolate; position: relative; padding: clamp(82px, 9vw, 132px) 0 104px; overflow: hidden; }
.hero::before { content: ''; position: absolute; inset: -58% -24% auto auto; width: 820px; aspect-ratio: 1; border-radius: 50%; background: radial-gradient(circle, var(--hero-glow-blue), var(--hero-glow-violet) 38%, transparent 66%); pointer-events: none; z-index: -1; }
.hero::after { content: ''; position: absolute; left: 2%; top: 33%; width: 340px; aspect-ratio: 1; background: radial-gradient(circle, var(--hero-glow-pink), transparent 69%); pointer-events: none; z-index: -1; }
.hero-grid { display: grid; grid-template-columns: minmax(0, 3fr) minmax(300px, 2fr); align-items: center; gap: clamp(42px, 6vw, 82px); }
.hero__copy { min-width: 0; }
.eyebrow { display: inline-flex; align-items: center; gap: 9px; color: var(--eyebrow-text); font-weight: 720; font-size: .73rem; letter-spacing: .14em; text-transform: uppercase; }
.eyebrow::before { content: ''; width: 23px; height: 2px; border-radius: 4px; background: var(--brand-gradient); }
.hero__eyebrow { border: 1px solid var(--accent-soft-border); background: var(--accent-soft); border-radius: 100px; padding: 9px 15px; letter-spacing: .075em; }
.hero__eyebrow::before { width: 7px; height: 7px; background: var(--accent-success); box-shadow: 0 0 0 4px var(--success-ring); }
h1, h2, h3, p { margin-top: 0; }
.hero h1 { font-size: clamp(3rem, 5.6vw, 5.5rem); line-height: 1.04; letter-spacing: -.072em; margin: 27px 0 18px; font-weight: 820; max-width: 820px; }
.gradient-text { background: var(--brand-gradient); -webkit-background-clip: text; background-clip: text; color: transparent; }
.hero__subtitle { margin: 0 0 16px; color: var(--text-main); font-size: clamp(1.15rem, 2vw, 1.48rem); font-weight: 710; letter-spacing: -.025em; }
.hero__description { max-width: 650px; margin-bottom: 0; color: var(--text-muted); font-size: clamp(1rem, 1.45vw, 1.15rem); line-height: 1.82; }
.hero__actions { display: flex; flex-wrap: wrap; gap: 13px; margin-top: 31px; }
.hero__portrait-column { min-width: 0; display: flex; justify-content: center; }
.hero-portrait { position: relative; width: min(100%, 380px); margin: 0; padding-bottom: 28px; }
.hero-portrait__frame { position: relative; width: 100%; max-width: 380px; aspect-ratio: 1 / 1; padding: 2px; overflow: hidden; border-radius: 24px; background: var(--brand-gradient); box-shadow: var(--portrait-shadow); }
.hero-portrait__frame::after { content: ''; position: absolute; inset: 2px; border-radius: 22px; box-shadow: inset 0 0 0 1px var(--portrait-inset); pointer-events: none; }
.hero-portrait__image { display: block; width: 100%; height: 100%; border-radius: 22px; object-fit: cover; object-position: center 15%; background: #F8FAFC; }
.hero-status-pill { position: absolute; left: 50%; bottom: 0; z-index: 2; display: inline-flex; align-items: center; justify-content: center; gap: 9px; width: max-content; max-width: calc(100% - 24px); min-height: 44px; padding: 10px 15px; border: 1px solid var(--portrait-pill-border); border-radius: 999px; background: var(--portrait-pill-bg); color: var(--text-main); box-shadow: var(--portrait-pill-shadow); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); transform: translateX(-50%); font-size: .8rem; font-weight: 690; white-space: nowrap; }
.hero-status-pill__dot { width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; background: var(--accent-success); box-shadow: 0 0 0 4px var(--success-ring); }
.section { padding-block: 100px; scroll-margin-top: 95px; }
.section--tinted { background: var(--section-tint); border-top: 1px solid var(--header-border); }
.section-head { display: flex; justify-content: space-between; align-items: end; gap: 30px; margin-bottom: 36px; }
.section-head h2 { font-weight: 780; font-size: clamp(2rem, 3.8vw, 3.4rem); line-height: 1.15; letter-spacing: -.055em; margin: 11px 0 0; }
.section-head p { margin: 8px 0 0; color: var(--text-muted); max-width: 530px; }
.section-head__aside { font-size: .85rem; color: var(--text-muted); }
.projects-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 22px; }
.project-card, .blog-card, .skill-card { min-width: 0; background: var(--surface-card); border: 1px solid var(--border-subtle); border-radius: var(--radius-lg); overflow: hidden; transition: border-color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast); }
.project-card:hover, .blog-card:hover, .skill-card:hover { transform: translateY(-4px); border-color: var(--card-hover-border); box-shadow: var(--shadow-card); }
.project-card__visual { height: 190px; display: flex; align-items: center; justify-content: center; background: var(--project-visual-bg); border-bottom: 1px solid var(--border-subtle); overflow: hidden; }
.project-card__visual img { width: 100%; height: 100%; object-fit: cover; }
.project-card__glyph { display: grid; place-items: center; font: 900 3rem var(--font-code); background: var(--brand-gradient); color: transparent; -webkit-background-clip: text; background-clip: text; }
.project-card__body { padding: 25px; }
.project-card h3, .blog-card h3 { font-size: 1.25rem; line-height: 1.35; letter-spacing: -.025em; margin-bottom: 9px; }
.project-card p, .blog-card p { font-size: .9rem; line-height: 1.75; color: var(--text-muted); min-height: 70px; margin-bottom: 18px; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
.tag { color: var(--accent-text); background: var(--accent-soft); border: 1px solid var(--accent-soft-border); border-radius: 7px; padding: 4px 10px; font: 600 .7rem var(--font-code); }
.card-links { display: flex; flex-wrap: wrap; gap: 18px; padding-top: 20px; margin-top: 20px; border-top: 1px solid var(--border-subtle); }
.card-links a, .blog-card__link { color: var(--link-accent); font-size: .85rem; font-weight: 720; }
.card-links a:hover, .blog-card__link:hover { text-decoration: underline; }
.skills-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.skill-card { padding: 27px 23px; }
.skill-card__icon { display: grid; place-items: center; width: 45px; height: 45px; background: var(--accent-soft); border: 1px solid var(--accent-soft-border); border-radius: 12px; margin-bottom: 20px; color: var(--link-accent); font: 750 1.05rem var(--font-code); }
.skill-card h3 { font-size: 1.03rem; margin-bottom: 7px; }
.skill-card p { color: var(--text-muted); font-size: .84rem; margin-bottom: 0; }
.blog-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 22px; }
.blog-card { padding: 26px; display: flex; flex-direction: column; }
.blog-card__date { color: var(--accent-text); text-transform: uppercase; font-size: .71rem; font-weight: 720; letter-spacing: .1em; margin-bottom: 16px; }
.blog-card p { flex: 1; }
.blog-card .tags { margin-bottom: 16px; }
.contact-layout { display: grid; grid-template-columns: minmax(0,.85fr) minmax(0,1.15fr); gap: clamp(28px,6vw,100px); align-items: start; }
.contact-intro h2 { font-size: clamp(2.45rem, 4vw, 4rem); line-height: 1.17; letter-spacing: -.06em; margin: 17px 0; }
.contact-intro > p { color: var(--text-muted); max-width: 450px; }
.contact-details { display: grid; gap: 22px; margin-top: 34px; }
.contact-detail { display: flex; gap: 15px; align-items: start; }
.contact-detail__icon { display: grid; flex: 0 0 44px; place-items: center; height: 44px; border: 1px solid var(--accent-soft-border); background: var(--accent-soft); border-radius: 12px; color: var(--accent-text); }
.contact-detail small { display: block; color: var(--text-dim); font-size: .76rem; }
.contact-detail a, .contact-detail span { font-weight: 600; font-size: .94rem; overflow-wrap: anywhere; }
.contact-detail a:hover { color: var(--link-accent); }
.contact-form { padding: clamp(23px,3.1vw,38px); border-radius: var(--radius-lg); border: 1px solid var(--border-subtle); background: var(--surface-card); box-shadow: var(--shadow-card); }
.contact-form__title { font-size: 1.35rem; margin-bottom: 4px; }
.contact-form__subtitle { font-size: .85rem; color: var(--text-muted); margin-bottom: 26px; }
.form-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.field { display: flex; flex-direction: column; gap: 8px; margin-bottom: 18px; }
.field label { font-size: .82rem; font-weight: 650; }
.field input, .field textarea { width: 100%; border: 1px solid var(--border-hover); background: var(--surface-input); color: var(--text-main); border-radius: 10px; padding: 13px 15px; font-size: .91rem; transition: border-color var(--transition-fast), box-shadow var(--transition-fast); }
.field textarea { min-height: 140px; resize: vertical; }
.field input:focus, .field textarea:focus { outline: none; border-color: var(--focus-ring); box-shadow: 0 0 0 3px var(--focus-shadow); }
.field [aria-invalid='true'] { border-color: var(--accent-error); }
.field-error { min-height: 15px; color: var(--status-error); font-size: .77rem; line-height: 1.3; }
.honeypot { position: absolute !important; left: -10000px !important; width: 1px !important; height: 1px !important; opacity: 0 !important; overflow: hidden !important; }
.check-row { display: flex; align-items: start; gap: 11px; color: var(--text-muted); font-size: .79rem; margin-bottom: 18px; }
.check-row input { width: 17px; height: 17px; flex: 0 0 17px; accent-color: var(--brand-violet); margin: 4px 0 0; }
.check-row a { color: var(--link-accent); text-decoration: underline; }
.form-status { font-size: .83rem; min-height: 25px; color: var(--text-muted); }
.form-status--error { color: var(--status-error); }
.form-status--success { color: var(--status-success); }
.contact-form .button { width: 100%; }
.empty-state { display: grid; place-items: center; padding: 60px 25px; text-align: center; border: 1px dashed var(--border-hover); background: var(--empty-bg); border-radius: var(--radius-lg); grid-column: 1 / -1; }
.empty-state__icon { display: grid; place-items: center; font: 800 1.9rem var(--font-code); width: 70px; height: 70px; border-radius: 18px; background: var(--accent-soft); color: var(--accent-text); margin-bottom: 15px; }
.empty-state h3 { font-size: 1.15rem; margin-bottom: 4px; }
.empty-state p { color: var(--text-muted); font-size: .87rem; max-width: 440px; margin-bottom: 0; }
.skeleton-card { padding: 0 0 22px; border: 1px solid var(--border-subtle); background: var(--surface-card); border-radius: var(--radius-lg); overflow: hidden; }
.skeleton-block { background: var(--skeleton-gradient); background-size: 230% 100%; animation: shimmer 1.25s linear infinite; }
.skeleton-card__image { height: 185px; margin-bottom: 22px; }
.skeleton-card__line { height: 16px; margin: 0 23px 15px; border-radius: 5px; }
.skeleton-card__line--short { width: 58%; }
@keyframes shimmer { to { background-position-x: -230%; } }
.site-footer { border-top: 1px solid var(--border-subtle); padding: 48px 0 32px; }
.footer-main { display: flex; align-items: start; justify-content: space-between; gap: 30px; padding-bottom: 40px; }
.footer-main p { color: var(--text-muted); font-size: .85rem; max-width: 335px; margin-top: 16px; }
.footer-nav { display: flex; flex-wrap: wrap; gap: 30px; }
.footer-nav a { color: var(--text-muted); font-size: .83rem; }
.footer-nav a:hover { color: var(--text-main); }
.footer-bottom { display: flex; align-items: center; justify-content: space-between; gap: 20px; flex-wrap: wrap; border-top: 1px solid var(--border-subtle); padding-top: 24px; color: var(--text-dim); font-size: .75rem; }
.footer-bottom a { color: var(--text-muted); }
.footer-bottom a:hover { color: var(--text-main); }
.footer-bottom-links { display: flex; flex-wrap: wrap; gap: 22px; }
.mobile-cta { display: none; }
.cookie-banner { position: fixed; z-index: 80; bottom: 20px; left: 50%; transform: translateX(-50%); width: min(680px, calc(100% - 36px)); padding: 19px 22px; border: 1px solid var(--cookie-border); border-radius: var(--radius-md); background: var(--cookie-bg); box-shadow: var(--modal-shadow); }
.cookie-banner[hidden] { display: none; }
.cookie-banner__body { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.cookie-banner p { color: var(--text-muted); margin: 6px 0 0; font-size: .79rem; line-height: 1.6; }
.cookie-banner a { text-decoration: underline; color: var(--link-accent); }
.cookie-banner__actions { display: flex; gap: 8px; flex-shrink: 0; }
.cookie-banner__actions button { padding: 11px 13px; font-size: .78rem; }
.legal-hero { padding: 90px 0 48px; }
.legal-hero h1 { font-size: clamp(2.4rem,6vw,4.5rem); line-height: 1.15; letter-spacing: -.055em; margin: 18px 0; }
.legal-hero p { color: var(--text-muted); max-width: 690px; }
.legal-layout { width: min(820px, 100%); margin: 0 auto; padding-bottom: 90px; }
.legal-layout h2 { font-size: 1.35rem; letter-spacing: -.025em; margin: 34px 0 13px; }
.legal-layout p, .legal-layout li { color: var(--text-muted); line-height: 1.85; }
.legal-layout a { color: var(--link-accent); text-decoration: underline; }
.legal-layout ul { padding-left: 22px; }
.status-page { min-height: calc(100vh - 190px); display: grid; place-items: center; padding: 100px 0; }
.status-panel { max-width: 780px; margin-inline: auto; text-align: center; }
.status-panel__symbol { font-weight: 900; line-height: 1; letter-spacing: -.09em; font-size: clamp(7rem,22vw,14rem); background: var(--brand-gradient); -webkit-background-clip: text; background-clip: text; color: transparent; }
.status-panel h1 { font-size: clamp(2.25rem,5vw,3.9rem); letter-spacing: -.05em; margin: 17px 0; }
.status-panel p { color: var(--text-muted); max-width: 530px; margin: 0 auto 28px; }
.receipt { color: var(--text-muted); font: .82rem var(--font-code); overflow-wrap: anywhere; margin-bottom: 23px; }
.receipt strong { color: var(--text-main); }
@media (min-width: 1440px) {
  .hero { padding-block: 164px 148px; }
  .section { padding-block: 112px; }
}
@media (max-width: 1024px) {
  .hero-grid { grid-template-columns: minmax(0, 1.1fr) minmax(290px, .9fr); gap: 36px; }
  .projects-grid, .blog-grid { grid-template-columns: repeat(2, minmax(0,1fr)); }
  .skills-grid { grid-template-columns: repeat(2, minmax(0,1fr)); }
  .contact-layout { gap: 32px; }
}
@media (max-width: 768px) {
  html { scroll-padding-top: 78px; }
  .site-header__inner { min-height: 68px; }
  .nav-toggle { display: inline-flex; }
  .site-nav { display: none; position: absolute; top: 68px; left: 0; right: 0; padding: 18px 24px 25px; flex-direction: column; align-items: stretch; gap: 0; background: var(--nav-mobile-bg); border-bottom: 1px solid var(--border-hover); }
  .site-nav.is-open { display: flex; }
  .site-nav a { padding: 13px 0; }
  .nav-action { display: none; }
  .hero { padding: 62px 0 75px; }
  .hero-grid { grid-template-columns: 1fr; gap: 34px; }
  .hero__portrait-column { order: -1; }
  .hero-portrait { width: min(82vw, 360px); }
  .hero h1 { max-width: 650px; }
  .section { padding-block: 72px; }
  .contact-layout { grid-template-columns: 1fr; }
  .section-head { display: block; }
  .section-head__aside { margin-top: 15px; }
  .footer-main { flex-direction: column; }
  .mobile-cta { position: fixed; z-index: 30; bottom: 0; left: 0; right: 0; padding: 10px max(20px, env(safe-area-inset-right)) calc(10px + env(safe-area-inset-bottom)); display: flex; justify-content: space-between; align-items: center; gap: 12px; background: var(--mobile-cta-bg); border-top: 1px solid var(--border-hover); backdrop-filter: blur(12px); }
  .mobile-cta span { font-size: .79rem; font-weight: 650; }
  .mobile-cta .button { padding: 11px 16px; font-size: .77rem; }
  body { padding-bottom: 70px; }
  .cookie-banner { bottom: 88px; }
}
@media (max-width: 600px) {
  .container { width: min(var(--container-width), calc(100% - 36px)); }
  .projects-grid, .blog-grid { grid-template-columns: 1fr; }
  .section-head h2 { font-size: 2.1rem; }
  .form-row { grid-template-columns: 1fr; gap: 0; }
  .contact-form { padding: 22px; }
  .hero h1 { font-size: clamp(3rem,10.2vw,4.7rem); }
  .hero__actions .button { flex: 1 1 180px; }
  .cookie-banner__body { flex-direction: column; align-items: stretch; gap: 14px; }
  .cookie-banner__actions { justify-content: flex-end; }
  .footer-nav { gap: 18px; }
}
@media (max-width: 375px) {
  .hero h1 { font-size: 2.9rem; }
  .hero-portrait { width: min(86vw, 320px); }
  .hero-status-pill { max-width: calc(100% - 12px); padding-inline: 12px; font-size: .73rem; }
  .skills-grid { grid-template-columns: 1fr; }
  .mobile-cta span { font-size: .71rem; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}




.site-header { background: var(--header-bg); }
.brand__svg { width: 62px; height: 39px; flex-shrink: 0; }
.header-utilities { display: flex; align-items: center; gap: 7px; margin-left: auto; }
.utility-button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-width: 39px; height: 39px; padding: 6px 9px; border: 1px solid var(--border-subtle); background: var(--surface-card); border-radius: 11px; color: var(--text-main); font-size: .77rem; }
.utility-button:hover { border-color: var(--brand-violet); }
.theme-toggle { min-width: 86px; justify-content: flex-start; }
.theme-toggle__icon { display: inline-grid; place-items: center; width: 19px; font-size: 1rem; line-height: 1; }
.theme-toggle__caption { font-weight: 700; letter-spacing: .01em; }
body, .site-header, .site-nav, .mobile-cta, .cookie-banner, .project-card, .blog-card, .skill-card, .experience-card, .contact-form, .app-modal, .utility-button, .button--secondary, input, textarea, select { transition: background-color var(--transition-theme), border-color var(--transition-theme), color var(--transition-theme), box-shadow var(--transition-theme); }
.app-modal { width: min(610px, calc(100vw - 28px)); max-height: min(88vh, 780px); overflow: auto; border: 1px solid var(--border-hover); border-radius: 21px; color: var(--text-main); background: var(--surface-card); box-shadow: var(--modal-shadow); padding: 0; }
.app-modal::backdrop { background: var(--modal-backdrop); backdrop-filter: blur(5px); }
.modal-body { padding: 26px; }
.modal-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.modal-heading h2 { margin: 0; font-size: 1.45rem; }
.modal-body p, .modal-body small { color: var(--text-muted); }
.modal-body label { display: block; margin-block: 14px 7px; font-size: .85rem; }
.modal-body input[type="search"], .modal-body textarea, .modal-body select { display: block; background: var(--surface-input); color: var(--text-main); border: 1px solid var(--border-hover); border-radius: 10px; width: 100%; padding: 12px 13px; }
.search-results { margin-top: 18px; max-height: 48vh; overflow: auto; }
.search-result { display: flex; gap: 3px; flex-direction: column; border-bottom: 1px solid var(--border-subtle); padding: 11px 4px; }
.search-result:hover { background: var(--surface-raised); }
.search-result__type { color: var(--brand-violet); font-size: .72rem; font-weight: 750; text-transform: uppercase; }
.search-result strong { font-size: .96rem; }
.search-result small { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
mark { background: var(--selection-bg); color: var(--text-main); border-radius: 3px; }
.search-empty { padding: 12px 0; }
.stars { display: flex; flex-wrap: wrap; gap: 6px; padding: 12px 0; border: 0; }
.stars legend { font-weight: 650; font-size: .85rem; }
.stars label { margin: 0; }
.stars input { position: absolute; opacity: 0; width: 1px; }
.stars span { border: 1px solid var(--border-hover); border-radius: 9px; display: inline-flex; padding: 7px 10px; }
.stars input:checked + span { background: var(--brand-gradient); color: #fff; }
.stars input:focus-visible + span { outline: 3px solid var(--focus-ring); outline-offset: 3px; }
@media (max-width: 1100px) { .theme-toggle { min-width: 39px; justify-content: center; } .theme-toggle__caption { display: none; } }
@media (max-width: 1024px) { .site-header__inner { gap: 10px; } .header-utilities { gap: 4px; } }
@media (max-width: 768px) { .brand__svg { width: 52px; height: 33px; } .site-header__inner { flex-wrap: nowrap; } .header-utilities { margin-left: auto; } .utility-button--feedback span { display: none; } }
@media (max-width: 375px) { .brand__svg { width: 53px; } .header-utilities .utility-button { min-width: 35px; padding: 5px; } }


.education-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 18px; }
@media (max-width: 768px) { .education-grid { grid-template-columns: 1fr; } }



.case-study-trigger {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--text-main);
  font: inherit;
  font-weight: 750;
  cursor: pointer;
}
.case-study-trigger:hover { color: var(--brand-violet); }
.case-study-modal { width: min(920px, calc(100vw - 28px)); max-height: min(92vh, 920px); }
.case-study-modal__body { padding: clamp(22px, 4vw, 38px); }
.case-study-modal__heading { align-items: flex-start; padding-bottom: 22px; border-bottom: 1px solid var(--border-subtle); }
.case-study-modal__heading h2 { margin-top: 7px; font-size: clamp(1.65rem, 4vw, 2.45rem); letter-spacing: -.035em; }
.case-study-intro { padding: 26px 0 8px; }
.case-study-kicker { margin: 0 0 9px; color: var(--brand-violet) !important; font: 700 .72rem var(--font-code); letter-spacing: .12em; text-transform: uppercase; }
.case-study-summary { max-width: 760px; margin: 0; color: var(--text-main) !important; font-size: 1.07rem; line-height: 1.75; }
.case-study-section { padding: 24px 0; border-top: 1px solid var(--border-subtle); }
.case-study-section h3 { margin: 0 0 12px; font-size: 1.08rem; letter-spacing: -.015em; }
.case-study-section p { margin: 0; line-height: 1.8; white-space: pre-line; }
.case-study-metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 11px; }
.case-study-metric { display: flex; align-items: center; min-height: 62px; padding: 13px 15px; border: 1px solid var(--border-hover); border-radius: 12px; background: var(--surface-raised); color: var(--text-main); font-weight: 720; }
.architecture-flow { list-style: none; padding: 0 2px 10px; margin: 18px 0 0; display: flex; gap: 22px; overflow-x: auto; overscroll-behavior-inline: contain; scrollbar-width: thin; }
.architecture-flow__step { position: relative; flex: 1 0 160px; min-height: 118px; padding: 17px; border: 1px solid var(--border-hover); border-radius: 14px; background: var(--surface-raised); }
.architecture-flow__step:not(:last-child)::after { content: '→'; position: absolute; right: -18px; top: 50%; transform: translateY(-50%); color: var(--brand-violet); font-weight: 900; z-index: 2; }
.architecture-flow__marker { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 28px; margin-bottom: 12px; border-radius: 8px; background: var(--brand-gradient); color: #fff; font: 750 .72rem var(--font-code); }
.architecture-flow__text { display: block; color: var(--text-main); font-size: .86rem; line-height: 1.55; }
.case-study-actions { display: flex; flex-wrap: wrap; gap: 11px; padding-top: 26px; border-top: 1px solid var(--border-subtle); }
@media (max-width: 768px) {
  .case-study-metrics { grid-template-columns: 1fr; }
  .architecture-flow { display: grid; grid-template-columns: 1fr; gap: 12px; overflow: visible; padding-bottom: 0; }
  .architecture-flow__step { min-height: auto; }
  .architecture-flow__step:not(:last-child)::after { content: '↓'; right: 50%; top: auto; bottom: -18px; transform: translateX(50%); }
}


.section--experience { position: relative; }
.experience-timeline { position: relative; display: grid; gap: 22px; padding-left: 34px; }
.experience-timeline::before { content: ''; position: absolute; top: 8px; bottom: 8px; left: 8px; width: 3px; border-radius: 999px; background: var(--brand-gradient); opacity: .9; }
.experience-card { position: relative; padding: clamp(22px, 4vw, 30px); border: 1px solid var(--border-subtle); border-radius: 18px; background: var(--surface-card); box-shadow: var(--shadow-card); }
.experience-card__marker { position: absolute; top: 30px; left: -34px; width: 18px; height: 18px; border: 4px solid var(--surface-base); border-radius: 50%; background: var(--brand-gradient); box-shadow: 0 0 0 2px var(--brand-violet); }
.experience-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.experience-card h3 { margin: 0; font-size: clamp(1.25rem, 2.4vw, 1.65rem); letter-spacing: -.025em; }
.experience-company { display: inline-flex; margin-top: 7px; padding: 5px 10px; border: 1px solid var(--border-hover); border-radius: 999px; background: var(--surface-raised); color: var(--text-main); font-size: .79rem; font-weight: 750; }
.experience-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.experience-pill { display: inline-flex; align-items: center; min-height: 30px; padding: 5px 10px; border: 1px solid var(--border-subtle); border-radius: 999px; color: var(--text-muted); background: var(--surface-raised); font-size: .78rem; font-weight: 650; }
.experience-pill--date { border-color: var(--accent-soft-border); color: var(--text-main); }
.experience-summary { margin: 18px 0 0; color: var(--text-muted); max-width: 900px; }
.experience-highlights { display: grid; gap: 9px; margin: 18px 0; padding-left: 20px; color: var(--text-muted); }
.experience-highlights li::marker { color: var(--brand-violet); }
.experience-card .tags { margin-top: 18px; }
@media (max-width: 768px) {
  .experience-timeline { padding-left: 26px; }
  .experience-timeline::before { left: 6px; }
  .experience-card__marker { left: -28px; width: 16px; height: 16px; }
  .experience-card__header { display: block; }
}

.mobile-theme-panel { display: none; }

@media (max-width: 768px) {
  .nav-toggle {
    width: 44px;
    height: 44px;
    padding: 0;
    align-items: center;
    justify-content: center;
    font-size: 0;
  }

  .nav-toggle::after {
    content: '☰';
    font-size: 1.2rem;
    line-height: 1;
  }

  .header-utilities .utility-button {
    width: 44px;
    min-width: 44px;
    height: 44px;
    padding: 0;
  }

  .site-nav {
    max-height: calc(100dvh - 68px);
    overflow-y: auto;
    box-shadow: 0 18px 42px rgba(20, 28, 42, .16);
  }

  .mobile-theme-panel {
    display: block;
    margin-top: 9px;
    padding-top: 16px;
    border-top: 1px solid var(--border-subtle);
  }

  .mobile-theme-panel__label {
    display: block;
    margin-bottom: 9px;
    color: var(--text-dim);
    font-size: .72rem;
    font-weight: 750;
    letter-spacing: .1em;
    text-transform: uppercase;
  }

  .mobile-theme-options {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .mobile-theme-option {
    min-height: 44px;
    border: 1px solid var(--border-hover);
    border-radius: 11px;
    background: var(--surface-card);
    color: var(--text-main);
    font-size: .78rem;
    font-weight: 720;
    transition: background-color var(--transition-theme), border-color var(--transition-theme), color var(--transition-theme), box-shadow var(--transition-theme), transform var(--transition-fast);
  }

  .mobile-theme-option:active { transform: scale(.98); }

  .mobile-theme-option.is-active {
    border-color: transparent;
    background: var(--brand-gradient);
    color: #FFFFFF;
    box-shadow: var(--primary-shadow);
  }
}

@media (max-width: 390px) {
  .container { width: min(var(--container-width), calc(100% - 28px)); }
  .site-header__inner { gap: 6px; }
  .brand__svg { width: 48px; height: 31px; }
  .header-utilities { gap: 3px; }
  .header-utilities .utility-button,
  .nav-toggle { width: 40px; min-width: 40px; height: 40px; }
}

.profile-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.profile-summary__card {
  padding: clamp(1.2rem, 2vw, 1.6rem);
  border: 1px solid var(--border);
  border-radius: 20px;
  background: var(--card);
  box-shadow: var(--shadow-card);
}

.profile-summary__card h3 {
  margin-top: 0;
}

.profile-summary__card p:last-child {
  margin-bottom: 0;
}

@media (max-width: 767px) {
  .profile-summary {
    grid-template-columns: 1fr;
  }
}
```

## `frontend/css/variables.css`

```css
:root {
  color-scheme: dark;
  --brand-blue: #3B66F5;
  --brand-violet: #7A5AF8;
  --brand-pink: #C84B9E;
  --brand-gradient: linear-gradient(115deg, var(--brand-blue) 0%, var(--brand-violet) 53%, var(--brand-pink) 100%);
  --surface-base: #0A0E17;
  --surface-card: #121A29;
  --surface-raised: #19243A;
  --surface-input: #0D1523;
  --border-subtle: #1E293B;
  --border-hover: #364764;
  --card-hover-border: #41567A;
  --text-main: #F8FAFC;
  --text-muted: #9AA8BC;
  --text-dim: #77879E;
  --accent-success: #34D399;
  --accent-error: #FB7185;
  --focus-ring: #5B7CFA;
  --link-accent: #C4BCFF;
  --accent-text: #C1B9FF;
  --eyebrow-text: #B7AFFF;
  --status-error: #FDA4AF;
  --status-success: #6EE7B7;
  --accent-soft: rgba(122, 90, 248, .11);
  --accent-soft-border: rgba(122, 90, 248, .24);
  --success-ring: rgba(52, 211, 153, .14);
  --focus-shadow: rgba(59, 102, 245, .18);
  --selection-bg: rgba(122, 90, 248, .42);
  --selection-text: #FFFFFF;
  --page-background: radial-gradient(circle at 92% 4%, rgba(59, 102, 245, .08), transparent 28%), #0A0E17;
  --header-bg: rgba(10, 14, 23, .90);
  --header-border: rgba(54, 71, 100, .55);
  --section-tint: linear-gradient(180deg, rgba(18, 26, 41, .62), rgba(10, 14, 23, 0));
  --project-visual-bg: radial-gradient(ellipse at 28% 40%, rgba(59, 102, 245, .18), transparent 60%), linear-gradient(125deg, #1C2945, #151827);
  --empty-bg: rgba(18, 26, 41, .52);
  --nav-mobile-bg: rgba(15, 22, 36, .98);
  --mobile-cta-bg: rgba(18, 26, 41, .96);
  --cookie-bg: #192238;
  --cookie-border: #455673;
  --skeleton-gradient: linear-gradient(100deg, #1D273A 8%, #2C3A50 18%, #1D273A 33%);
  --modal-backdrop: rgba(2, 6, 23, .74);
  --hero-glow-blue: rgba(59, 102, 245, .15);
  --hero-glow-violet: rgba(122, 90, 248, .08);
  --hero-glow-pink: rgba(200, 75, 158, .09);
  --portrait-inset: rgba(255, 255, 255, .08);
  --portrait-pill-shadow: 0 14px 38px rgba(2, 6, 23, .22);
  --brand-shadow: 0 6px 24px rgba(122, 90, 248, .27);
  --primary-shadow: 0 12px 32px rgba(80, 92, 239, .23);
  --primary-shadow-hover: 0 16px 38px rgba(80, 92, 239, .36);
  --shadow-card: 0 18px 54px rgba(2, 5, 15, .28);
  --modal-shadow: 0 24px 80px rgba(0, 0, 0, .50);
  --portrait-shadow: 0 24px 72px rgba(2, 5, 15, .42), 0 0 46px rgba(122, 90, 248, .18);
  --portrait-pill-bg: rgba(18, 26, 41, .82);
  --portrait-pill-border: rgba(148, 163, 184, .24);
  --font-body: Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-code: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  --container-width: 1200px;
  --radius-sm: 8px;
  --radius-md: 14px;
  --radius-lg: 22px;
  --radius-xl: 32px;
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-10: 40px;
  --space-12: 48px;
  --space-16: 64px;
  --space-20: 80px;
  --transition-fast: 180ms ease;
  --transition-theme: 340ms cubic-bezier(.2, .7, .2, 1);
}

:root,
[data-theme="dark"] {
  color-scheme: dark;
}

[data-theme="dusk"] {
  color-scheme: light;
  --surface-base: #AEB7C5;
  --surface-card: #BEC5D0;
  --surface-raised: #CBD1DA;
  --surface-input: #D8DDE4;
  --border-subtle: #929CAA;
  --border-hover: #778295;
  --card-hover-border: #66728A;
  --text-main: #172033;
  --text-muted: #344156;
  --text-dim: #4B596D;
  --accent-success: #0B6B4C;
  --accent-error: #A52C46;
  --focus-ring: #3D4FD7;
  --link-accent: #4939B7;
  --accent-text: #4636AF;
  --eyebrow-text: #4939B7;
  --status-error: #8C233A;
  --status-success: #075E43;
  --accent-soft: rgba(73, 57, 183, .12);
  --accent-soft-border: rgba(73, 57, 183, .27);
  --success-ring: rgba(11, 107, 76, .15);
  --focus-shadow: rgba(61, 79, 215, .18);
  --selection-bg: rgba(73, 57, 183, .24);
  --selection-text: #172033;
  --page-background: radial-gradient(circle at 90% 5%, rgba(67, 89, 210, .13), transparent 30%), radial-gradient(circle at 8% 44%, rgba(169, 69, 139, .08), transparent 25%), #AEB7C5;
  --header-bg: rgba(190, 197, 208, .91);
  --header-border: rgba(91, 103, 122, .48);
  --section-tint: linear-gradient(180deg, rgba(203, 209, 218, .78), rgba(174, 183, 197, 0));
  --project-visual-bg: radial-gradient(ellipse at 28% 40%, rgba(59, 102, 245, .16), transparent 60%), linear-gradient(125deg, #C7CFDE, #CEC6D4);
  --empty-bg: rgba(202, 208, 218, .72);
  --nav-mobile-bg: rgba(195, 202, 213, .98);
  --mobile-cta-bg: rgba(190, 197, 208, .96);
  --cookie-bg: #C7CDD6;
  --cookie-border: #8792A3;
  --skeleton-gradient: linear-gradient(100deg, #9FA9B7 8%, #C8CED8 18%, #9FA9B7 33%);
  --modal-backdrop: rgba(18, 24, 38, .46);
  --hero-glow-blue: rgba(59, 102, 245, .15);
  --hero-glow-violet: rgba(122, 90, 248, .10);
  --hero-glow-pink: rgba(200, 75, 158, .08);
  --portrait-inset: rgba(255, 255, 255, .25);
  --portrait-pill-shadow: 0 14px 34px rgba(29, 38, 55, .18);
  --brand-shadow: 0 8px 24px rgba(73, 57, 183, .18);
  --primary-shadow: 0 12px 30px rgba(59, 78, 190, .20);
  --primary-shadow-hover: 0 16px 36px rgba(73, 57, 183, .27);
  --shadow-card: 0 16px 42px rgba(31, 40, 57, .15);
  --modal-shadow: 0 24px 70px rgba(24, 31, 45, .24);
  --portrait-shadow: 0 22px 58px rgba(31, 40, 57, .22), 0 0 38px rgba(122, 90, 248, .10);
  --portrait-pill-bg: rgba(202, 208, 218, .90);
  --portrait-pill-border: rgba(94, 105, 123, .42);
}

[data-theme="light"] {
  color-scheme: light;
  --surface-base: #F4F7FB;
  --surface-card: #FFFFFF;
  --surface-raised: #EEF3FA;
  --surface-input: #FBFDFF;
  --border-subtle: #DCE4EF;
  --border-hover: #BCC9DB;
  --card-hover-border: #9EAEC6;
  --text-main: #172033;
  --text-muted: #526074;
  --text-dim: #667085;
  --accent-success: #0F8A5F;
  --accent-error: #C93654;
  --focus-ring: #315EF4;
  --link-accent: #5A46D6;
  --accent-text: #5A46D6;
  --eyebrow-text: #5947C9;
  --status-error: #B4233E;
  --status-success: #087A54;
  --accent-soft: rgba(90, 70, 214, .08);
  --accent-soft-border: rgba(90, 70, 214, .18);
  --success-ring: rgba(15, 138, 95, .13);
  --focus-shadow: rgba(49, 94, 244, .15);
  --selection-bg: rgba(90, 70, 214, .20);
  --selection-text: #172033;
  --page-background: radial-gradient(circle at 92% 5%, rgba(59, 102, 245, .08), transparent 30%), radial-gradient(circle at 7% 40%, rgba(200, 75, 158, .045), transparent 24%), #F4F7FB;
  --header-bg: rgba(248, 250, 253, .90);
  --header-border: rgba(148, 163, 184, .34);
  --section-tint: linear-gradient(180deg, rgba(232, 238, 247, .82), rgba(244, 247, 251, 0));
  --project-visual-bg: radial-gradient(ellipse at 28% 40%, rgba(59, 102, 245, .13), transparent 60%), linear-gradient(125deg, #EDF2FF, #F7F0FA);
  --empty-bg: rgba(255, 255, 255, .70);
  --nav-mobile-bg: rgba(255, 255, 255, .98);
  --mobile-cta-bg: rgba(255, 255, 255, .96);
  --cookie-bg: #FFFFFF;
  --cookie-border: #CFD8E6;
  --skeleton-gradient: linear-gradient(100deg, #E8EDF4 8%, #F7F9FC 18%, #E8EDF4 33%);
  --modal-backdrop: rgba(15, 23, 42, .38);
  --hero-glow-blue: rgba(59, 102, 245, .11);
  --hero-glow-violet: rgba(122, 90, 248, .07);
  --hero-glow-pink: rgba(200, 75, 158, .055);
  --portrait-inset: rgba(255, 255, 255, .42);
  --portrait-pill-shadow: 0 12px 30px rgba(15, 23, 42, .12);
  --brand-shadow: 0 8px 24px rgba(90, 70, 214, .15);
  --primary-shadow: 0 12px 30px rgba(59, 102, 245, .18);
  --primary-shadow-hover: 0 16px 36px rgba(90, 70, 214, .26);
  --shadow-card: 0 16px 42px rgba(39, 54, 82, .09);
  --modal-shadow: 0 24px 80px rgba(39, 54, 82, .18);
  --portrait-shadow: 0 22px 54px rgba(39, 54, 82, .16), 0 0 36px rgba(122, 90, 248, .08);
  --portrait-pill-bg: rgba(255, 255, 255, .88);
  --portrait-pill-border: rgba(148, 163, 184, .42);
}
```

## `frontend/index.html`

```html
<!doctype html>
<html lang="en" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' 'sha256-YYpEwRpsP+rYTHIuGm/kWxRgE9+cGEXHU+g17pAMCz0='; style-src 'self'; img-src 'self' data: https:; connect-src 'self' http://localhost:8080 https://*.onrender.com; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'">
  <meta name="mlb-api-origin" content="">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" content="#0A0E17">
  <meta name="description" content="Official portfolio of Mufaddal Battiwala, a Full Stack Developer in Kolkata building with Java, Spring Boot, MongoDB and modern JavaScript. Explore projects, experience and contact details.">
  <meta name="author" content="Mufaddal Battiwala">
  <meta name="application-name" content="Mufaddal Battiwala Portfolio">
  <meta name="robots" content="index,follow,max-snippet:-1,max-image-preview:large,max-video-preview:-1">
  <meta name="googlebot" content="index,follow,max-snippet:-1,max-image-preview:large,max-video-preview:-1">
  <title>Mufaddal Battiwala | Full Stack Developer in Kolkata</title>
  <link rel="canonical" href="https://mufaddalbatti53.github.io/MLB-Portfolio/">
  <meta property="og:type" content="profile">
  <meta property="og:locale" content="en_IN">
  <meta property="og:site_name" content="Mufaddal Battiwala Portfolio">
  <meta property="og:title" content="Mufaddal Battiwala | Full Stack Developer">
  <meta property="og:description" content="Official portfolio of Mufaddal Battiwala — Java, Spring Boot, MongoDB, modern web development, projects and professional experience.">
  <meta property="og:url" content="https://mufaddalbatti53.github.io/MLB-Portfolio/">
  <meta property="og:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta property="og:image:alt" content="Mufaddal Battiwala full stack developer portfolio">
  <meta property="profile:first_name" content="Mufaddal">
  <meta property="profile:last_name" content="Battiwala">
  <meta property="profile:username" content="mufaddalbatti53">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="Mufaddal Battiwala | Full Stack Developer">
  <meta name="twitter:description" content="Official portfolio of Mufaddal Battiwala — Java, Spring Boot, MongoDB and modern web development.">
  <meta name="twitter:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <script type="application/ld+json" id="seo-structured-data">
    {
      "@context": "https://schema.org",
      "@graph": [
        {
          "@type": "WebSite",
          "@id": "https://mufaddalbatti53.github.io/MLB-Portfolio/#website",
          "url": "https://mufaddalbatti53.github.io/MLB-Portfolio/",
          "name": "Mufaddal Battiwala",
          "alternateName": [
            "Mufaddal Battiwala Portfolio",
            "MLB Portfolio"
          ],
          "inLanguage": "en",
          "publisher": {
            "@id": "https://mufaddalbatti53.github.io/MLB-Portfolio/#mufaddal-battiwala"
          }
        },
        {
          "@type": "ProfilePage",
          "@id": "https://mufaddalbatti53.github.io/MLB-Portfolio/#profile",
          "url": "https://mufaddalbatti53.github.io/MLB-Portfolio/",
          "name": "Mufaddal Battiwala | Full Stack Developer",
          "description": "Official developer portfolio and profile of Mufaddal Battiwala.",
          "isPartOf": {
            "@id": "https://mufaddalbatti53.github.io/MLB-Portfolio/#website"
          },
          "mainEntity": {
            "@id": "https://mufaddalbatti53.github.io/MLB-Portfolio/#mufaddal-battiwala"
          }
        },
        {
          "@type": "Person",
          "@id": "https://mufaddalbatti53.github.io/MLB-Portfolio/#mufaddal-battiwala",
          "name": "Mufaddal Battiwala",
          "givenName": "Mufaddal",
          "familyName": "Battiwala",
          "alternateName": [
            "Mufaddal",
            "MLB",
            "mufaddalbatti53"
          ],
          "url": "https://mufaddalbatti53.github.io/MLB-Portfolio/",
          "image": "https://mufaddalbatti53.github.io/MLB-Portfolio/assets/mufaddal-battiwala-full-stack-developer.jpg",
          "jobTitle": "Full Stack Developer",
          "description": "Full Stack Developer in Kolkata focused on Java, Spring Boot, MongoDB, REST APIs and modern web development.",
          "email": "mailto:mufaddal.techpro@gmail.com",
          "homeLocation": {
            "@type": "Place",
            "name": "Kolkata, India"
          },
          "affiliation": {
            "@type": "CollegeOrUniversity",
            "name": "Netaji Subhash Engineering College",
            "alternateName": "NSEC",
            "address": {
              "@type": "PostalAddress",
              "addressLocality": "Kolkata",
              "addressCountry": "IN"
            }
          },
          "sameAs": [
            "https://github.com/mufaddalbatti53",
            "https://www.linkedin.com/in/mufaddal-tech/",
            "https://instagram.com/mlb1606"
          ],
          "knowsAbout": [
            "Java",
            "Spring Boot",
            "MongoDB",
            "REST APIs",
            "JavaScript",
            "HTML",
            "CSS",
            "Full Stack Development",
            "Web Development"
          ]
        }
      ]
    }
  </script>
  <link rel="icon" href="./assets/favicon.ico" sizes="any">
  <link rel="icon" href="./assets/favicon.svg" type="image/svg+xml">
  <link rel="icon" href="./assets/favicon-32x32.png" type="image/png" sizes="32x32">
  <link rel="apple-touch-icon" href="./assets/apple-touch-icon.png">
  <link rel="preload" as="image" href="./assets/mufaddal-battiwala-full-stack-developer.jpg" type="image/jpeg">
  <link rel="manifest" href="./site.webmanifest">
  <script src="./js/theme-init.js"></script>
  <link rel="stylesheet" href="./css/main.css">
</head>
<body>

<a class="skip-link" href="#main-content">Skip to main content</a>
<header class="site-header">
  <div class="container site-header__inner">
    <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
    <button class="nav-toggle" id="nav-toggle" type="button" aria-label="Toggle navigation" aria-controls="site-nav" aria-expanded="false">Menu ☰</button>
    <nav class="site-nav" id="site-nav" aria-label="Primary navigation">
      <a href="./#about">About</a>
      <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
      <a href="./#skills">Expertise</a>
      <a href="./#blog">Writing</a>
      <a href="./#contact">Contact</a>
      <div class="mobile-theme-panel" aria-label="Choose theme">
        <span class="mobile-theme-panel__label">Theme</span>
        <div class="mobile-theme-options" role="group" aria-label="Theme options">
          <button class="mobile-theme-option" type="button" data-theme-choice="light" aria-pressed="false"><span aria-hidden="true">☀</span> Light</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dusk" aria-pressed="false"><span aria-hidden="true">◐</span> Dusk</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dark" aria-pressed="false"><span aria-hidden="true">☾</span> Dark</button>
        </div>
      </div>
    </nav>
<div class="header-utilities" role="group" aria-label="Site tools"><button class="utility-button" type="button" data-open-search aria-label="Search portfolio (Ctrl+K)" title="Search (Ctrl+K)"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></svg></button><button class="utility-button theme-toggle" type="button" data-theme-toggle aria-label="Theme: Dark. Switch to Light." title="Theme: Dark · Next: Light"><span class="theme-toggle__icon" data-theme-icon aria-hidden="true">☾</span><span class="theme-toggle__caption" data-theme-caption>Dark</span></button><button class="utility-button utility-button--feedback" type="button" data-open-feedback aria-label="Give site feedback"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l2-3A8.5 8.5 0 1 1 21 11.5Z"/><path d="M8 10h8M8 14h5"/></svg><span>Feedback</span></button></div>
    <a class="button button--small button--secondary nav-action" href="./#contact">Let's connect <span aria-hidden="true">↗</span></a>
  </div>
</header>


<main id="main-content">
  <section id="hero" class="hero" aria-labelledby="hero-title">
    <div class="container hero-grid">
      <div class="hero__copy">
        <span class="eyebrow hero__eyebrow">Available for Full-Stack Opportunities</span>
        <h1 id="hero-title">Hi, I'm <span id="owner-name" class="gradient-text">Mufaddal Battiwala</span></h1>
        <p class="hero__subtitle" id="owner-tagline">Full Stack Developer | Java + Spring Boot &amp; Modern Web Developer</p>
        <p class="hero__description">I build secure, accessible full-stack products with Java, Spring Boot, MongoDB and modern vanilla JavaScript—focused on clear architecture, reliable APIs and thoughtful user experiences.</p>
        <div class="hero__actions">
          <a class="button button--primary" href="#projects">View Projects <span aria-hidden="true">↗</span></a>
          <a class="button button--secondary" href="#contact">Get In Touch <span aria-hidden="true">↗</span></a>
        </div>
      </div>
      <div class="hero__portrait-column">
        <figure class="hero-portrait">
          <div class="hero-portrait__frame">
            <img class="hero-portrait__image"
                 src="./assets/mufaddal-battiwala-full-stack-developer.jpg"
                 width="420"
                 height="420"
                 fetchpriority="high"
                 decoding="async"
                 alt="Mufaddal Battiwala, Full Stack Developer in Kolkata">
          </div>
          <figcaption class="hero-status-pill">
            <span class="hero-status-pill__dot" aria-hidden="true"></span>
            <span>B.Tech CSE • Kolkata, India</span>
          </figcaption>
        </figure>
      </div>
    </div>
  </section>
  <section class="section section--profile" id="about" aria-labelledby="about-title">
    <div class="container">
      <div class="section-head">
        <div>
          <span class="eyebrow">About Mufaddal</span>
          <h2 id="about-title">About <span class="gradient-text">Mufaddal Battiwala.</span></h2>
          <p>Mufaddal Battiwala is a Full Stack Developer based in Kolkata, India, focused on Java, Spring Boot, MongoDB, REST APIs and modern web development.</p>
        </div>
        <span class="section-head__aside">Official developer profile</span>
      </div>
      <div class="profile-summary">
        <article class="profile-summary__card">
          <h3>Developer profile</h3>
          <p>This is the official portfolio of Mufaddal Battiwala, also known online as MLB and <strong>@mufaddalbatti53</strong>. He builds secure, accessible full-stack products with clear architecture and reliable APIs.</p>
          <p><a href="https://github.com/mufaddalbatti53" rel="me noopener noreferrer" target="_blank">GitHub profile ↗</a><span aria-hidden="true"> · </span><a href="https://www.linkedin.com/in/mufaddal-tech/" rel="me noopener noreferrer" target="_blank">LinkedIn profile ↗</a></p>
        </article>
        <article class="profile-summary__card">
          <h3>Background &amp; work</h3>
          <p>Mufaddal's B.Tech Computer Science Engineering work at Netaji Subhash Engineering College (NSEC), Kolkata supports a practical portfolio spanning the MLB Full-Stack Portfolio &amp; Custom CMS, CompliScan Prototype and WGS Order Processing. His experience also includes a Software Developer Intern role at Web Graphics Studio.</p>
          <p>Core technologies include Java 21, Spring Boot, MongoDB, JavaScript, semantic HTML, responsive CSS and secure REST API design.</p>
        </article>
      </div>
    </div>
  </section>
  <section class="section section--tinted" id="projects" aria-labelledby="projects-title">
    <div class="container">
      <div class="section-head"><div><span class="eyebrow">Selected work</span><h2 id="projects-title">Projects that tell a <span class="gradient-text">story.</span></h2><p>A closer look at how ideas become useful, maintainable products.</p></div><span class="section-head__aside">Work and experiments</span></div>
      <div class="projects-grid" id="projects-list" aria-live="polite" aria-busy="true"><div class="empty-state"><p>Loading projects…</p></div></div>
    </div>
  </section>
  <section class="section" id="skills" aria-labelledby="skills-title">
    <div class="container">
      <div class="section-head"><div><span class="eyebrow">Tools &amp; approach</span><h2 id="skills-title">The craft behind <span class="gradient-text">the code.</span></h2><p>A foundation across modern interfaces, APIs, data and secure engineering practices.</p></div></div>
      <div class="skills-grid">
        <article class="skill-card"><span class="skill-card__icon" aria-hidden="true">{ }</span><h3>Frontend engineering</h3><p>Semantic HTML, CSS Grid &amp; Flexbox, accessibility and JavaScript.</p></article>
        <article class="skill-card"><span class="skill-card__icon" aria-hidden="true">&lt;/&gt;</span><h3>Backend systems</h3><p>Java, Spring Boot, RESTful APIs, input validation and layered design.</p></article>
        <article class="skill-card"><span class="skill-card__icon" aria-hidden="true">DB</span><h3>Data &amp; storage</h3><p>MongoDB, indexed data models, API contracts and persistence.</p></article>
        <article class="skill-card"><span class="skill-card__icon" aria-hidden="true">↗</span><h3>Shipping reliably</h3><p>Testing, responsive design, observability and secure deployments.</p></article>
      </div>
    </div>
  </section>
  <section class="section section--experience" id="experience" aria-labelledby="experience-title">
    <div class="container">
      <div class="section-head">
        <div>
          <span class="eyebrow">Professional experience</span>
          <h2 id="experience-title">Building in the <span class="gradient-text">real world.</span></h2>
          <p>Hands-on engineering experience across responsive interfaces, workflow design and production web delivery.</p>
        </div>
        <span class="section-head__aside">Work timeline</span>
      </div>
      <div id="experience-list" aria-live="polite" aria-busy="true">
        <div class="empty-state"><p>Loading experience…</p></div>
      </div>
    </div>
  </section>
  <section class="section section--education" id="education" aria-labelledby="education-title"><div class="container"><div class="section-head"><div><span class="eyebrow">Background</span><h2 id="education-title">Learning that <span class="gradient-text">builds forward.</span></h2><p>My academic journey in engineering and technology.</p></div></div><div class="education-grid" id="education-list"><article class="skill-card"><h3>B.Tech — Computer Science Engineering</h3><p>Netaji Subhash Engineering College (NSEC), Kolkata</p></article><article class="skill-card"><h3>Higher Secondary (12th)</h3><p>Maharshi Sandipan Vidhyapeeth Senior Secondary School, Kota · 81.2%</p></article><article class="skill-card"><h3>Secondary (10th)</h3><p>New Haideriya English Senior Secondary School · 82.33%</p></article></div></div></section>
  <section class="section section--tinted" id="blog" aria-labelledby="blog-title">
    <div class="container">
      <div class="section-head"><div><span class="eyebrow">Notes from the field</span><h2 id="blog-title">Learning out <span class="gradient-text">loud.</span></h2><p>Thoughts, discoveries and clear explanations from the software-building process.</p></div></div>
      <div class="blog-grid" id="blog-list" aria-live="polite" aria-busy="true"><div class="empty-state"><p>Loading articles…</p></div></div>
    </div>
  </section>
  <section class="section" id="contact" aria-labelledby="contact-title">
    <div class="container contact-layout">
      <div class="contact-intro">
        <span class="eyebrow">Start a conversation</span>
        <h2 id="contact-title">Let's build something <span class="gradient-text">worth sharing.</span></h2>
        <p>Have a technical challenge, collaboration idea or opportunity? Send a message and let's see what we can create together.</p>
        <div class="contact-details">
          <div class="contact-detail"><span class="contact-detail__icon" aria-hidden="true">@</span><div><small>Email</small><a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a></div></div>
          <div class="contact-detail"><span class="contact-detail__icon" aria-hidden="true">☎</span><div><small>Phone</small><a data-profile-phone href="tel:+91907933526">+91 907933526</a></div></div>
          <div class="contact-detail"><span class="contact-detail__icon" aria-hidden="true">◎</span><div><small>Location</small><span data-profile-location>Kolkata, India</span></div></div>
          <div class="contact-detail"><span class="contact-detail__icon" aria-hidden="true">↗</span><div><small>Find me online</small><a href="https://github.com/mufaddalbatti53" data-profile-github target="_blank" rel="me noopener noreferrer">GitHub</a><span aria-hidden="true"> · </span><a href="https://www.linkedin.com/in/mufaddal-tech/" data-profile-linkedin target="_blank" rel="me noopener noreferrer">LinkedIn</a><span aria-hidden="true"> · </span><a href="https://instagram.com/mlb1606" target="_blank" rel="me noopener noreferrer">Instagram @mlb1606</a></div></div>
        </div>
      </div>
      <form id="contact-form" class="contact-form" novalidate aria-labelledby="contact-form-title">
        <h3 id="contact-form-title" class="contact-form__title">Send a message</h3>
        <noscript><p>JavaScript is required to submit this form. You can also email <a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a> directly.</p></noscript>
        <p class="contact-form__subtitle">All fields marked * are required.</p>
        <div class="form-row">
          <div class="field"><label for="contact-name">Name *</label><input id="contact-name" name="name" type="text" autocomplete="name" maxlength="100" required aria-describedby="name-error"><span id="name-error" class="field-error" aria-live="polite"></span></div>
          <div class="field"><label for="contact-email">Email *</label><input id="contact-email" name="email" type="email" autocomplete="email" maxlength="254" required aria-describedby="email-error"><span id="email-error" class="field-error" aria-live="polite"></span></div>
        </div>
        <div class="field"><label for="contact-subject">Subject *</label><input id="contact-subject" name="subject" type="text" maxlength="150" required aria-describedby="subject-error"><span id="subject-error" class="field-error" aria-live="polite"></span></div>
        <div class="field"><label for="contact-message">Message *</label><textarea id="contact-message" name="message" maxlength="5000" required aria-describedby="message-error"></textarea><span id="message-error" class="field-error" aria-live="polite"></span></div>
        <div class="honeypot" aria-hidden="true"><label for="contact-website">Leave this field empty</label><input id="contact-website" name="website" type="text" maxlength="200" tabindex="-1" autocomplete="off"></div>
        <label class="check-row" for="privacy-accepted"><input id="privacy-accepted" name="privacyAccepted" type="checkbox" required aria-describedby="privacyAccepted-error"><span>I agree to the storage of my message as explained in the <a href="./privacy.html">Privacy Policy</a>. *</span></label>
        <p id="privacyAccepted-error" class="field-error" aria-live="polite"></p>
        <p id="form-status" class="form-status" role="status" aria-live="polite"></p>
        <button type="submit" class="button button--primary">Send message</button>
      </form>
    </div>
  </section>
</main>


<footer class="site-footer">
  <div class="container">
    <div class="footer-main">
      <div>
        <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
        <p>Mufaddal Battiwala builds thoughtful full-stack software with Java, Spring Boot, MongoDB and modern web technologies.</p>
        <p><a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a><span aria-hidden="true"> · </span>Kolkata, India</p>
      </div>
      <nav class="footer-nav" aria-label="Footer navigation">
        <a href="./#about">About</a>
        <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
        <a href="./#skills">Expertise</a>
        <a href="./#blog">Writing</a>
        <a href="./#contact">Contact</a>
        <a href="https://github.com/mufaddalbatti53" data-profile-github target="_blank" rel="me noopener noreferrer">GitHub ↗</a>
        <a href="https://www.linkedin.com/in/mufaddal-tech/" data-profile-linkedin target="_blank" rel="me noopener noreferrer">LinkedIn ↗</a>
      </nav>
    </div>
    <div class="footer-bottom">
      <span>© <span id="copyright-year">2026</span> MLB. Built with care.</span>
      <div class="footer-bottom-links"><a href="./privacy.html">Privacy</a><a href="./terms.html">Terms</a><a href="mailto:mufaddal.techpro@gmail.com">Email</a></div>
    </div>
  </div>
</footer>
<div class="mobile-cta"><span>Have an idea in mind?</span><a class="button button--primary" href="./#contact">Let's talk <span aria-hidden="true">↗</span></a></div>
<aside class="cookie-banner" id="cookie-banner" hidden aria-label="Analytics consent preferences">
  <div class="cookie-banner__body">
    <div><strong>Your privacy matters</strong><p>This site only stores your analytics preference locally. Optional analytics is inactive by default and only runs with permission. <a href="./privacy.html">Privacy details</a>.</p></div>
    <div class="cookie-banner__actions"><button type="button" class="button button--secondary" data-consent="rejected">Decline</button><button type="button" class="button button--primary" data-consent="accepted">Accept</button></div>
  </div>
</aside>
<dialog id="site-search" class="app-modal" aria-labelledby="search-title"><div class="modal-body"><div class="modal-heading"><h2 id="search-title">Search MLB</h2><button class="utility-button" data-close-search type="button" aria-label="Close search">✕</button></div><label for="search-input">Find projects, experience, skills and articles</label><input type="search" id="search-input" autocomplete="off" aria-controls="search-results"/><div id="search-results" class="search-results" role="region" aria-live="polite"></div><small>Press Ctrl+K to open · Esc to close</small></div></dialog>
<dialog id="site-feedback" class="app-modal" aria-labelledby="feedback-title"><div class="modal-body"><div class="modal-heading"><h2 id="feedback-title">Site feedback</h2><button class="utility-button" data-close-feedback type="button" aria-label="Close feedback">✕</button></div><p>Help improve the MLB portfolio experience.</p><form id="feedback-form"><fieldset class="stars"><legend>How was your experience?</legend><label><input type="radio" name="rating" value="1" required/><span aria-label="1 stars">★ 1</span></label><label><input type="radio" name="rating" value="2" required/><span aria-label="2 stars">★ 2</span></label><label><input type="radio" name="rating" value="3" required/><span aria-label="3 stars">★ 3</span></label><label><input type="radio" name="rating" value="4" required/><span aria-label="4 stars">★ 4</span></label><label><input type="radio" name="rating" value="5" required/><span aria-label="5 stars">★ 5</span></label></fieldset><label class="field">Category <select name="category" required><option value="UI_UX">UI/UX</option><option value="BUG">Bug</option><option value="FEATURE_IDEA">Feature Idea</option><option value="GENERAL">General</option></select></label><label class="field">Comment (optional)<textarea name="comment" maxlength="500" rows="4"></textarea></label><div class="honeypot" aria-hidden="true"><label for="feedback-website">Leave empty</label><input id="feedback-website" name="website" maxlength="200" tabindex="-1" autocomplete="off"/></div><p role="status" aria-live="polite" id="feedback-status"></p><button class="button button--primary" type="submit">Send feedback</button></form></div></dialog>
<dialog id="project-case-study" class="app-modal case-study-modal" aria-labelledby="case-study-title">
  <div class="modal-body case-study-modal__body">
    <div class="modal-heading case-study-modal__heading">
      <div><span class="eyebrow">PROJECT DEEP DIVE</span><h2 id="case-study-title">Project case study</h2></div>
      <button class="utility-button" data-close-case-study type="button" aria-label="Close project case study">✕</button>
    </div>
    <div id="case-study-content"></div>
  </div>
</dialog>
<script type="module" src="./js/main.js"></script>

</body>
</html>
```

## `frontend/js/admin-api.js`

```javascript
import { ApiError, apiUrl } from './api.js';

const TOKEN_KEY = 'mlb-admin-token';

function readToken() {
  return sessionStorage.getItem(TOKEN_KEY) || '';
}

function writeToken(token) {
  if (typeof token === 'string' && token.length > 20) sessionStorage.setItem(TOKEN_KEY, token);
}

function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

async function send(path, { method = 'GET', data, timeout = 12000, authenticated = true } = {}) {
  const abort = new AbortController();
  const clock = setTimeout(() => abort.abort(), timeout);
  try {
    const token = authenticated ? readToken() : '';
    const response = await fetch(apiUrl(path), {
      method,
      credentials: 'omit',
      cache: 'no-store',
      signal: abort.signal,
      headers: {
        Accept: 'application/json',
        ...(data !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      ...(data !== undefined ? { body: JSON.stringify(data) } : {})
    });
    const type = response.headers.get('content-type') || '';
    const body = type.includes('json') ? await response.json() : null;
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) clearToken();
      throw new ApiError(body?.detail || `Request failed (${response.status})`, response.status, body?.errors || {});
    }
    return body;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error.name === 'AbortError') throw new ApiError('The admin request timed out');
    throw new ApiError('Unable to connect to the administration API');
  } finally {
    clearTimeout(clock);
  }
}


export async function login(username, password) {
  const body = await send('/auth/login', {
    method: 'POST',
    data: { username, password },
    authenticated: false
  });
  if (!body?.token) throw new ApiError('Authentication token was not returned by the server');
  writeToken(body.token);
  return { status: body.status || 'authenticated' };
}

export const currentUser = () => send('/auth/me');

export async function logout() {
  try {
    return await send('/auth/logout', { method: 'POST' });
  } finally {
    clearToken();
  }
}

export const getDashboard = () => send('/admin/dashboard');

export const getAdminExperiences = (page = 0) => send(`/admin/experiences?page=${encodeURIComponent(page)}&size=20`);
export const createExperience = (body) => send('/admin/experiences', { method: 'POST', data: body });
export const updateExperience = (id, body) => send(`/admin/experiences/${encodeURIComponent(id)}`, { method: 'PUT', data: body });
export const deleteExperience = (id) => send(`/admin/experiences/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const getAdminList = (resource, page = 0) => send(`/admin/${resource}?page=${encodeURIComponent(page)}&size=20`);
export const createAdminItem = (resource, body) => send(`/admin/${resource}`, { method: 'POST', data: body });
export const putAdminItem = (resource, id, body) => send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'PUT', data: body });
export const patchAdminItem = (resource, id, body) => send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'PATCH', data: body });
export const deleteAdminItem = (resource, id) => send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const retryNotification = (id) => send(`/admin/messages/${encodeURIComponent(id)}/retry`, { method: 'POST' });
export const getProfile = () => send('/admin/profile');
export const saveProfile = (body) => send('/admin/profile', { method: 'PUT', data: body });
```

## `frontend/js/admin.js`

```javascript
import { initTheme } from './theme.js';
import {
  createAdminItem,
  createExperience,
  currentUser,
  deleteAdminItem,
  deleteExperience,
  getAdminExperiences,
  getAdminList,
  getDashboard,
  getProfile,
  login,
  logout,
  patchAdminItem,
  putAdminItem,
  retryNotification,
  saveProfile,
  updateExperience
} from './admin-api.js';

const byId = (id) => document.getElementById(id);

function element(name, className = '', text = '') {
  const node = document.createElement(name);
  if (className) node.className = className;
  node.textContent = String(text);
  return node;
}

function status(message) {
  byId('admin-status').textContent = message;
}

function loginStatus(message) {
  byId('login-status').textContent = message;
}

const tabs = ['dashboard', 'projects', 'experiences', 'blog', 'messages', 'feedback', 'profile'];
const titles = {
  dashboard: 'Dashboard Overview',
  projects: 'Project Manager',
  experiences: 'Work Experience',
  blog: 'Blog Posts Manager',
  messages: 'Contact Inbox',
  feedback: 'Site Feedback',
  profile: 'Public Profile'
};
const resourceNames = {
  projects: 'project',
  experiences: 'experience',
  blog: 'article',
  messages: 'message',
  feedback: 'feedback entry'
};

let activeTab = 'dashboard';
let currentPage = 0;
let cache = [];
let editorSubmitController = null;

function setAuthenticated(value) {
  byId('login-panel').hidden = value;
  byId('dashboard-panel').hidden = !value;
  if (!value) {
    byId('admin-editor-panel').hidden = true;
    byId('admin-collection').replaceChildren();
  }
}

function button(label, action, variant = '') {
  const control = element('button', variant, label);
  control.type = 'button';
  control.addEventListener('click', action);
  return control;
}

function pillCard(title, description, detail = '') {
  const card = element('article', 'admin-card');
  card.append(element('h3', '', title), element('p', '', description));
  if (detail) card.append(element('small', '', detail));
  return card;
}

function toolbar(actionTitle, handler) {
  const bar = element('div', 'admin-toolbar');
  bar.append(element('span', '', `Page ${currentPage + 1}`));
  if (handler) bar.append(button(actionTitle, handler, 'button button--primary'));
  byId('admin-collection').append(bar);
}

function experiencePayload(item, overrides = {}) {
  return {
    role: item.role,
    company: item.company,
    employmentType: item.employmentType,
    workMode: item.workMode,
    location: item.location,
    startDate: item.startDate,
    endDate: item.current ? null : item.endDate,
    current: Boolean(item.current),
    summary: item.summary,
    highlights: Array.isArray(item.highlights) ? item.highlights : [],
    technologies: Array.isArray(item.technologies) ? item.technologies : [],
    sortOrder: Number(item.sortOrder ?? 0),
    published: Boolean(item.published),
    ...overrides
  };
}

async function listResponse(type) {
  if (type === 'experiences') return getAdminExperiences(currentPage);
  return getAdminList(type, currentPage);
}

async function deleteItem(type, id) {
  if (type === 'experiences') return deleteExperience(id);
  return deleteAdminItem(type, id);
}

async function toggleExperience(item) {
  await updateExperience(item.id, experiencePayload(item, { published: !item.published }));
}

async function loadList(type) {
  const result = await listResponse(type);
  cache = Array.isArray(result.content) ? result.content : [];
  const totalPages = Number(result.totalPages || 0);

  if (type === 'projects') toolbar('Add project', () => showEditor('projects'));
  else if (type === 'experiences') toolbar('Add experience', () => showEditor('experiences'));
  else if (type === 'blog') toolbar('Add article', () => showEditor('blog'));
  else toolbar('', null);

  const items = element('div', 'admin-items');
  if (!cache.length) items.append(pillCard('No items found', 'This collection is empty.'));

  for (const item of cache) {
    const heading = item.title || item.role || item.subject || `Rating ${item.rating} / 5`;
    const description = item.summary || item.excerpt || item.message || item.comment || 'No additional details';
    let detail = '';

    if (type === 'projects') {
      detail = `${item.slug} • Order ${item.sortOrder} • ${item.published ? 'Published' : 'Draft'} • ${item.featured ? 'Featured' : 'Standard'}`;
    } else if (type === 'experiences') {
      detail = `${item.company} • ${item.employmentType} • ${item.startDate} → ${item.current ? 'Present' : item.endDate} • ${item.published ? 'Published' : 'Draft'}`;
    } else if (type === 'blog') {
      detail = `${item.slug} • ${item.published ? 'Published' : 'Draft'}`;
    } else if (type === 'messages') {
      detail = `${item.name} · ${item.email} · ${item.inboxStatus} · Mail ${item.notificationStatus}`;
    } else {
      detail = `${item.category} · ${new Date(item.createdAt).toLocaleDateString()}`;
    }

    const card = pillCard(heading, description, detail);
    const actions = element('div', 'admin-card__actions');

    if (type === 'projects') {
      actions.append(
        button('Edit', () => showEditor('projects', item)),
        button(item.published ? 'Unpublish' : 'Publish', () => mutate(type, item.id, { published: !item.published })),
        button(item.featured ? 'Unfeature' : 'Feature', () => mutate(type, item.id, { featured: !item.featured })),
        button('↑', () => mutate(type, item.id, { sortOrder: Math.max(0, item.sortOrder - 10) })),
        button('↓', () => mutate(type, item.id, { sortOrder: item.sortOrder + 10 }))
      );
    } else if (type === 'experiences') {
      actions.append(
        button('Edit', () => showEditor('experiences', item)),
        button(item.published ? 'Unpublish' : 'Publish', async () => {
          try {
            await toggleExperience(item);
            status('Changes saved.');
            await switchTab(type, false);
          } catch (error) {
            status(error.message);
          }
        })
      );
    } else if (type === 'blog') {
      actions.append(
        button('Edit', () => showEditor('blog', item)),
        button(item.published ? 'Unpublish' : 'Publish', () => mutate(type, item.id, { published: !item.published }))
      );
    } else if (type === 'messages') {
      for (const state of ['READ', 'ARCHIVED', 'SPAM']) {
        actions.append(button(state, () => mutate(type, item.id, { inboxStatus: state })));
      }
      if (item.notificationStatus === 'FAILED') {
        actions.append(button('Retry email', async () => {
          try {
            await retryNotification(item.id);
            status('Email notification queued.');
            await switchTab(type);
          } catch (error) {
            status(error.message);
          }
        }));
      }
    }

    actions.append(button('Delete', async () => {
      const name = resourceNames[type] || 'item';
      if (!confirm(`Delete this ${name}? This cannot be undone.`)) return;
      try {
        await deleteItem(type, item.id);
        status('Deleted successfully.');
        await switchTab(type);
      } catch (error) {
        status(error.message);
      }
    }));

    card.append(actions);
    items.append(card);
  }

  byId('admin-collection').append(items);

  if (totalPages > 1) {
    const pager = element('div', 'admin-pagination');
    if (currentPage > 0) {
      pager.append(button('Previous page', async () => {
        currentPage -= 1;
        await switchTab(type, false);
      }));
    }
    if (currentPage + 1 < totalPages) {
      pager.append(button('Next page', async () => {
        currentPage += 1;
        await switchTab(type, false);
      }));
    }
    byId('admin-collection').append(pager);
  }
}

async function mutate(type, id, patch) {
  try {
    await patchAdminItem(type, id, patch);
    status('Changes saved.');
    await switchTab(type, false);
  } catch (error) {
    status(error.message);
  }
}

const specs = {
  projects: [
    ['slug', 'text', true],
    ['title', 'text', true],
    ['summary', 'textarea', true],
    ['description', 'textarea', false],
    ['problemStatement', 'textarea', false],
    ['architectureOverview', 'textarea', false],
    ['architectureFlow', 'lines', false],
    ['keyMetrics', 'lines', false],
    ['technologies', 'csv', true],
    ['githubUrl', 'url', false],
    ['liveUrl', 'url', false],
    ['imageUrl', 'url', false],
    ['imageAlt', 'text', false],
    ['sortOrder', 'number', true],
    ['published', 'checkbox', false],
    ['featured', 'checkbox', false]
  ],
  experiences: [
    ['role', 'text', true],
    ['company', 'text', true],
    ['employmentType', 'text', true],
    ['workMode', 'text', true],
    ['location', 'text', true],
    ['startDate', 'date', true],
    ['endDate', 'date', false],
    ['current', 'checkbox', false],
    ['summary', 'textarea', true],
    ['highlights', 'lines', true],
    ['technologies', 'csv', true],
    ['sortOrder', 'number', true],
    ['published', 'checkbox', false]
  ],
  blog: [
    ['slug', 'text', true],
    ['title', 'text', true],
    ['excerpt', 'textarea', true],
    ['articleUrl', 'url', true],
    ['tags', 'csv', true],
    ['coverImageUrl', 'url', false],
    ['coverImageAlt', 'text', false],
    ['published', 'checkbox', false]
  ]
};

const limits = {
  projects: {
    slug: 100,
    title: 120,
    summary: 300,
    description: 5000,
    problemStatement: 1000,
    architectureOverview: 2000,
    imageAlt: 180
  },
  experiences: {
    role: 120,
    company: 120,
    employmentType: 80,
    workMode: 80,
    location: 120,
    summary: 1500
  },
  blog: {
    slug: 100,
    title: 150,
    excerpt: 400,
    coverImageAlt: 180
  },
  profile: {
    fullName: 100,
    brand: 20,
    tagline: 180,
    publicEmail: 254,
    phone: 30,
    location: 120,
    instagramHandle: 40
  }
};

function field(type, name, kind, required, value) {
  const label = element('label', kind === 'checkbox' ? 'admin-check' : '');
  const pretty = name.replace(/([A-Z])/g, ' $1').replace(/^./, (letter) => letter.toUpperCase());
  const usesTextarea = kind === 'textarea' || kind === 'lines';
  const control = element(usesTextarea ? 'textarea' : 'input');
  control.name = name;

  if (!usesTextarea) {
    control.type = kind === 'checkbox' ? 'checkbox' : kind === 'csv' ? 'text' : kind;
  }

  if (kind === 'checkbox') {
    control.checked = Boolean(value);
  } else if (kind === 'csv') {
    control.value = Array.isArray(value) ? value.join(', ') : '';
  } else if (kind === 'lines') {
    control.value = Array.isArray(value) ? value.join('\n') : '';
    control.rows = name === 'architectureFlow' || name === 'highlights' ? 7 : 5;
  } else {
    control.value = value ?? '';
  }

  if (required && kind !== 'checkbox') control.required = true;
  if (name === 'sortOrder') {
    control.min = '0';
    control.max = '100000';
  }
  if (name === 'slug') control.pattern = '[a-z0-9]+(?:-[a-z0-9]+)*';
  if (limits[type]?.[name]) control.maxLength = limits[type][name];

  label.append(element('span', '', pretty), control);
  if (name === 'architectureFlow') {
    label.append(element('small', 'admin-field-hint', 'One step per line. Maximum 8 steps, 160 characters each.'));
  } else if (name === 'keyMetrics') {
    label.append(element('small', 'admin-field-hint', 'One metric per line. Maximum 4 metrics, 120 characters each.'));
  } else if (name === 'highlights') {
    label.append(element('small', 'admin-field-hint', 'One contribution per line. Maximum 8 items, 300 characters each.'));
  } else if (name === 'technologies' && type === 'experiences') {
    label.append(element('small', 'admin-field-hint', 'Comma-separated. Maximum 15 technologies, 60 characters each.'));
  }
  return label;
}

function parseLineList(input) {
  return input.value
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
}

function validateProject(values) {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug)) return 'Use a lowercase hyphenated slug.';
  if (values.architectureFlow.length > 8) return 'Architecture flow supports up to 8 steps.';
  if (values.architectureFlow.some((step) => step.length > 160)) return 'Each architecture flow step must be 160 characters or fewer.';
  if (values.keyMetrics.length > 4) return 'Key metrics supports up to 4 badges.';
  if (values.keyMetrics.some((metric) => metric.length > 120)) return 'Each key metric must be 120 characters or fewer.';
  if (values.technologies.length > 15) return 'Technologies supports up to 15 items.';
  if (values.technologies.some((technology) => technology.length > 60)) return 'Each technology must be 60 characters or fewer.';
  for (const key of ['githubUrl', 'liveUrl', 'imageUrl']) {
    if (!isHttpsUrl(values[key])) return `${key} must use HTTPS.`;
  }
  return '';
}

function isHttpsUrl(value, required = false) {
  if (!value) return !required;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && !url.hash && !url.port;
  } catch {
    return false;
  }
}

function validateBlog(values) {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug)) return 'Use a lowercase hyphenated slug.';
  if (values.tags.length > 12) return 'Tags supports up to 12 items.';
  if (values.tags.some((tag) => tag.length > 50)) return 'Each tag must be 50 characters or fewer.';
  if (!isHttpsUrl(values.articleUrl, true)) return 'Article URL must use HTTPS.';
  if (!isHttpsUrl(values.coverImageUrl)) return 'Cover image URL must use HTTPS.';
  return '';
}

function validateExperience(values) {
  if (values.current && values.endDate) return 'Current experience must not have an end date.';
  if (!values.current && !values.endDate) return 'End date is required for past experience.';
  if (values.endDate && values.startDate && values.endDate < values.startDate) return 'End date cannot precede start date.';
  if (values.highlights.length > 8) return 'Highlights supports up to 8 items.';
  if (values.highlights.some((highlight) => highlight.length > 300)) return 'Each highlight must be 300 characters or fewer.';
  if (values.technologies.length > 15) return 'Technologies supports up to 15 items.';
  if (values.technologies.some((technology) => technology.length > 60)) return 'Each technology must be 60 characters or fewer.';
  return '';
}

function editorTitle(type, editing) {
  const label = type === 'projects' ? 'project' : type === 'experiences' ? 'experience' : 'article';
  return `${editing ? 'Edit' : 'Create'} ${label}`;
}

function editorHelp(type) {
  if (type === 'projects') return 'Case-study fields power the recruiter-facing project drawer.';
  if (type === 'experiences') return 'Experience fields power the public work timeline and live search.';
  return 'Published article metadata appears in the public writing section.';
}

function formValues(type, form) {
  const values = {};
  for (const [name, kind] of specs[type]) {
    const input = form.elements.namedItem(name);
    values[name] = kind === 'checkbox'
      ? input.checked
      : kind === 'number'
        ? Number(input.value)
        : kind === 'csv'
          ? input.value.split(',').map((value) => value.trim()).filter(Boolean)
          : kind === 'lines'
            ? parseLineList(input)
            : input.value;
  }
  if (type === 'experiences' && values.current) values.endDate = null;
  return values;
}

function showEditor(type, item = null) {
  const panel = byId('admin-editor-panel');
  const form = byId('admin-editor');
  byId('admin-editor-title').textContent = editorTitle(type, Boolean(item));
  byId('admin-editor-help').textContent = editorHelp(type);

  form.replaceChildren();
  form.className = 'admin-editor';
  for (const [name, kind, required] of specs[type]) {
    form.append(field(type, name, kind, required, item?.[name]));
  }

  const actions = element('div', 'admin-editor-actions');
  const save = element('button', 'button button--primary', 'Save');
  save.type = 'submit';
  actions.append(save, button('Cancel', () => { panel.hidden = true; }));
  form.append(actions);
  panel.hidden = false;

  editorSubmitController?.abort();
  editorSubmitController = new AbortController();
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const values = formValues(type, form);

    const validationError = type === 'projects'
      ? validateProject(values)
      : type === 'experiences'
        ? validateExperience(values)
        : type === 'blog'
          ? validateBlog(values)
          : '';
    if (validationError) {
      status(validationError);
      return;
    }

    if (type === 'blog') values.publishedAt = item?.publishedAt || new Date().toISOString();

    save.disabled = true;
    try {
      if (type === 'experiences') {
        if (item) await updateExperience(item.id, values);
        else await createExperience(values);
      } else if (item) {
        await putAdminItem(type, item.id, values);
      } else {
        await createAdminItem(type, values);
      }
      panel.hidden = true;
      status('Saved successfully.');
      await switchTab(type, false);
    } catch (error) {
      status(error.message);
    } finally {
      save.disabled = false;
    }
  }, { signal: editorSubmitController.signal });

  panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function renderDashboard() {
  const overview = await getDashboard();
  const grid = element('div', 'admin-stats');
  for (const [key, label] of Object.entries({
    totalProjects: 'Total projects',
    publishedBlogs: 'Published blogs',
    unreadMessages: 'Unread messages',
    activeRateLimitBlocks: 'Active throttle blocks'
  })) {
    const stat = element('article', 'admin-stat');
    stat.append(element('strong', '', String(overview[key] ?? 0)), element('span', '', label));
    grid.append(stat);
  }
  byId('admin-collection').append(grid);
}

async function renderProfile() {
  const profile = await getProfile();
  const form = element('form', 'admin-editor');
  const fields = [
    'fullName',
    'brand',
    'tagline',
    'publicEmail',
    'phone',
    'location',
    'websiteUrl',
    'githubUrl',
    'linkedinUrl',
    'instagramHandle'
  ];

  fields.forEach((key) => form.append(field(
    'profile',
    key,
    key.endsWith('Url') ? 'url' : key === 'publicEmail' ? 'email' : 'text',
    key === 'fullName' || key === 'publicEmail' || key === 'location',
    profile[key]
  )));

  const education = field('profile', 'education', 'textarea', false, JSON.stringify(profile.education || [], null, 2));
  education.querySelector('textarea').rows = 7;
  form.append(education);

  const submit = element('button', 'button button--primary', 'Save profile');
  submit.type = 'submit';
  form.append(submit);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const body = {};
    fields.forEach((key) => { body[key] = form.elements.namedItem(key).value; });
    try {
      body.education = JSON.parse(form.elements.namedItem('education').value);
      if (!Array.isArray(body.education)) throw new Error('Education must be an array');
      if (body.education.length > 12) throw new Error('Education supports up to 12 entries');
      for (const key of ['websiteUrl', 'githubUrl', 'linkedinUrl']) {
        if (!isHttpsUrl(body[key])) throw new Error(`${key} must use HTTPS`);
      }
      await saveProfile(body);
      status('Profile saved.');
    } catch (error) {
      status(error.message);
    }
  });
  byId('admin-collection').append(form);
}

async function switchTab(next, resetPage = true) {
  if (!tabs.includes(next)) return;
  activeTab = next;
  if (resetPage) currentPage = 0;

  document.querySelectorAll('[data-tab]').forEach((control) => {
    control.setAttribute('aria-current', control.dataset.tab === activeTab ? 'page' : 'false');
  });
  byId('admin-section-title').textContent = titles[activeTab];
  byId('admin-editor-panel').hidden = true;
  byId('admin-collection').replaceChildren(element('p', '', 'Loading admin data…'));

  try {
    byId('admin-collection').replaceChildren();
    if (activeTab === 'dashboard') await renderDashboard();
    else if (activeTab === 'profile') await renderProfile();
    else await loadList(activeTab);
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      setAuthenticated(false);
      loginStatus('Your admin session expired. Please sign in again.');
      return;
    }
    status(error.message);
    byId('admin-collection').replaceChildren(pillCard('Data unavailable', 'Try refreshing the dashboard.'));
  }
}

async function start() {
  initTheme();

  byId('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    loginStatus('Signing in…');
    const data = new FormData(event.currentTarget);
    try {
      await login(String(data.get('username')), String(data.get('password')));
      event.currentTarget.reset();
      setAuthenticated(true);
      loginStatus('');
      await switchTab('dashboard');
    } catch (error) {
      loginStatus(error.status === 429
        ? 'Too many attempts. Try again later.'
        : 'Unable to sign in. Check credentials and connection.');
    }
  });

  byId('admin-logout').addEventListener('click', async () => {
    try {
      await logout();
      setAuthenticated(false);
      loginStatus('Signed out.');
    } catch {
      setAuthenticated(false);
      loginStatus('Sign-out could not be confirmed. Close this browser tab and revoke the session if necessary.');
    }
  });

  byId('admin-refresh').addEventListener('click', () => switchTab(activeTab, false));
  document.querySelectorAll('[data-tab]').forEach((control) => {
    control.addEventListener('click', () => switchTab(control.dataset.tab));
  });

  try {
    await currentUser();
    setAuthenticated(true);
    await switchTab('dashboard');
  } catch {
    setAuthenticated(false);
  }
}

start();
```

## `frontend/js/analytics.js`

```javascript
let consent = false;
export function setAnalyticsConsent(accepted) {
  consent = accepted === true;
}
export function trackPageView() {
  if (!consent) return;
}
```

## `frontend/js/api.js`

```javascript
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1']);

export class ApiError extends Error {
  constructor(message, status = 0, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function configuredProductionOrigin() {
  const value = document.querySelector('meta[name="mlb-api-origin"]')?.content?.trim() || '';
  if (!value) return '';
  try {
    const url = new URL(value);
    const validHost = url.hostname.endsWith('.onrender.com');
    const exactOrigin = url.pathname === '/' && !url.search && !url.hash && !url.username && !url.password;
    return url.protocol === 'https:' && validHost && exactOrigin ? url.origin : '';
  } catch {
    return '';
  }
}

function resolveApiOrigin() {
  if (LOCAL_HOSTS.has(window.location.hostname)) return 'http://localhost:8080';
  const configured = configuredProductionOrigin();
  if (configured) return configured;
  if (window.location.hostname.endsWith('.github.io')) return '';
  return window.location.origin;
}

export const API_BASE_URL = (() => {
  const origin = resolveApiOrigin();
  return origin ? `${origin}/api/v1` : '';
})();

export function apiUrl(path) {
  if (!API_BASE_URL) {
    throw new ApiError('Production API origin is not configured for this deployment.');
  }
  const raw = String(path || '');
  const withoutPrefix = raw.startsWith('/api/v1') ? raw.slice('/api/v1'.length) : raw;
  const normalized = withoutPrefix.startsWith('/') ? withoutPrefix : `/${withoutPrefix}`;
  return `${API_BASE_URL}${normalized}`;
}

async function request(path, { method = 'GET', data, timeoutMs = 10000 } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(apiUrl(path), {
      method,
      credentials: 'omit',
      cache: 'no-store',
      headers: { Accept: 'application/json', ...(data ? { 'Content-Type': 'application/json' } : {}) },
      body: data ? JSON.stringify(data) : undefined,
      signal: controller.signal
    });
    const contentType = response.headers.get('content-type') || '';
    const payload = contentType.includes('json') ? await response.json() : null;
    if (!response.ok) {
      const fields = payload?.errors && typeof payload.errors === 'object' ? payload.errors : {};
      throw new ApiError(payload?.detail || 'The request could not be completed.', response.status, fields);
    }
    return payload;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error.name === 'AbortError') throw new ApiError('The server took too long. Please try again.');
    throw new ApiError('Unable to connect. Please check your internet connection.');
  } finally {
    clearTimeout(timer);
  }
}

export const getPublicProfile = () => request('/profile');
export const getProjects = (limit = 6) => request(`/projects?limit=${encodeURIComponent(limit)}`);
export const getProject = (slug) => request(`/projects/${encodeURIComponent(slug)}`);
export const getBlogPosts = (limit = 3) => request(`/blog?limit=${encodeURIComponent(limit)}`);
export const getExperiences = () => request('/experiences');
export const sendFeedback = (data) => request('/feedback', { method: 'POST', data, timeoutMs: 15000 });
export const sendContact = (data) => request('/contact', { method: 'POST', data, timeoutMs: 15000 });
```

## `frontend/js/feedback.js`

```javascript
import { sendFeedback } from './api.js';
export function initFeedback() {
  const modal = document.getElementById('site-feedback'); const form = document.getElementById('feedback-form');
  if (!modal || !form) return;
  const status = document.getElementById('feedback-status'); let lastTrigger;
  document.querySelectorAll('[data-open-feedback]').forEach(button => button.addEventListener('click', () => {
    lastTrigger = button; status.textContent = ''; if (!modal.open) modal.showModal();
    form.querySelector('input[name="rating"]')?.focus();
  }));
  document.querySelectorAll('[data-close-feedback]').forEach(button => button.addEventListener('click', () => modal.close()));
  modal.addEventListener('close', () => { if (lastTrigger?.isConnected) lastTrigger.focus(); });
  modal.addEventListener('click', event => { if (event.target === modal) modal.close(); });
  form.addEventListener('submit', async event => {
    event.preventDefault(); status.textContent = '';
    const selected = form.querySelector('input[name="rating"]:checked');
    const comment = form.elements.namedItem('comment').value;
    if (!selected || comment.length > 500) { status.textContent = 'Select a rating and use at most 500 characters.'; return; }
    const submit = form.querySelector('[type="submit"]');submit.disabled = true;
    try {
      await sendFeedback({ rating: Number(selected.value), category: form.elements.namedItem('category').value,
        comment, website: form.elements.namedItem('website').value });
      form.reset();status.textContent = 'Thank you. Your feedback was received.';
    } catch (error) { status.textContent = error.status === 429 ? 'Too many requests. Try again later.' : 'Unable to send feedback. Please retry.'; }
    finally { submit.disabled = false; }
  });
}
```

## `frontend/js/main.js`

```javascript
import { ApiError, getBlogPosts, getExperiences, getProjects, getPublicProfile, sendContact } from './api.js';
import { initCookieConsent, renderBlogPosts, renderEmpty, renderExperiences, renderProjects, renderSkeletons } from './ui.js';
import { setAnalyticsConsent, trackPageView } from './analytics.js';
import { initTheme } from './theme.js';
import { initSearch } from './search.js';
import { initFeedback } from './feedback.js';


async function populateProfile() {
  try {
    const owner = await getPublicProfile();
    const name = document.getElementById('owner-name');
    const tagline = document.getElementById('owner-tagline');
    if (name && owner.fullName) name.textContent = owner.fullName;
    if (tagline && owner.tagline) tagline.textContent = owner.tagline;
    document.querySelectorAll('[data-profile-email]').forEach(link => {
      if (typeof owner.publicEmail === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(owner.publicEmail)) {
        link.textContent = owner.publicEmail; link.href = `mailto:${owner.publicEmail}`;
      }
    });
    document.querySelectorAll('[data-profile-location]').forEach(node => {
      if (owner.location) node.textContent = owner.location;
    });
    document.querySelectorAll('[data-profile-phone]').forEach(link => {
      if (typeof owner.phone === 'string' && /^[+0-9 ()-]{7,30}$/.test(owner.phone)) {
        link.textContent = owner.phone; link.href = `tel:${owner.phone.replace(/[^+0-9]/g,'')}`;
      }
    });
    const applyHttpsProfileLink = (selector, value) => {
      if (typeof value !== 'string') return;
      try {
        const url = new URL(value);
        if (url.protocol !== 'https:' || url.username || url.password) return;
        document.querySelectorAll(selector).forEach((link) => { link.href = url.href; });
      } catch {
      }
    };
    applyHttpsProfileLink('[data-profile-github]', owner.githubUrl);
    applyHttpsProfileLink('[data-profile-linkedin]', owner.linkedinUrl);
    const education = document.getElementById('education-list');
    if (education && Array.isArray(owner.education) && owner.education.length) {
      const fragment = document.createDocumentFragment();
      owner.education.slice(0,12).forEach(entry => {
        const card = document.createElement('article'); card.className='skill-card';
        const heading=document.createElement('h3');heading.textContent=entry.qualification || 'Education';
        const body=document.createElement('p');body.textContent=[entry.institution,entry.location,entry.score].filter(Boolean).join(' · ');
        card.append(heading,body);fragment.append(card);
      }); education.replaceChildren(fragment);
    }
  } catch {  }
}

function initNavigation() {
  const toggle = document.getElementById('nav-toggle');
  const nav = document.getElementById('site-nav');
  if (!toggle || !nav) return;
  const close = () => { toggle.setAttribute('aria-expanded', 'false'); nav.classList.remove('is-open'); };
  toggle.addEventListener('click', () => {
    const open = toggle.getAttribute('aria-expanded') !== 'true';
    toggle.setAttribute('aria-expanded', String(open));
    nav.classList.toggle('is-open', open);
  });
  nav.querySelectorAll('a').forEach((link) => link.addEventListener('click', close));
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') close(); });
  document.addEventListener('click', (event) => {
    if (!nav.contains(event.target) && !toggle.contains(event.target)) close();
  });
}

async function populateSections() {
  const projects = document.getElementById('projects-list');
  const experience = document.getElementById('experience-list');
  const blog = document.getElementById('blog-list');
  if (projects) {
    renderSkeletons(projects, 3);
    getProjects().then((data) => renderProjects(projects, data))
      .catch(() => renderEmpty(projects, 'Unable to load projects',
        'The project feed is temporarily unavailable. Please try again later.', '!'));
  }
  if (experience) {
    renderSkeletons(experience, 1);
    getExperiences().then((data) => renderExperiences(experience, data))
      .catch(() => renderEmpty(experience, 'Unable to load experience',
        'The experience feed is temporarily unavailable. Please try again later.', '!'));
  }
  if (blog) {
    renderSkeletons(blog, 3);
    getBlogPosts().then((data) => renderBlogPosts(blog, data))
      .catch(() => renderEmpty(blog, 'Unable to load articles',
        'The article feed is temporarily unavailable. Please check again later.', '!'));
  }
}

function initForm() {
  const form = document.getElementById('contact-form');
  if (!form) return;
  const status = document.getElementById('form-status');
  const submit = form.querySelector('[type="submit"]');
  const keys = ['name', 'email', 'subject', 'message', 'privacyAccepted'];
  const errorFor = (key) => document.getElementById(`${key}-error`);
  const showError = (key, message) => {
    const field = form.elements.namedItem(key);
    if (field) field.setAttribute('aria-invalid', message ? 'true' : 'false');
    const output = errorFor(key);
    if (output) output.textContent = message || '';
  };
  const validate = (values) => {
    const errors = {};
    if (!values.name.trim() || values.name.length > 100) errors.name = 'Enter your name (100 characters maximum).';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email) || values.email.length > 254)
      errors.email = 'Enter a valid email address.';
    if (!values.subject.trim() || values.subject.length > 150) errors.subject = 'Enter a subject (150 characters maximum).';
    if (!values.message.trim() || values.message.length > 5000) errors.message = 'Enter a message (5,000 characters maximum).';
    if (!values.privacyAccepted) errors.privacyAccepted = 'Please accept the privacy policy.';
    return errors;
  };
  const valuesFromForm = () => ({
    name: form.elements.namedItem('name').value,
    email: form.elements.namedItem('email').value,
    subject: form.elements.namedItem('subject').value,
    message: form.elements.namedItem('message').value,
    website: form.elements.namedItem('website').value,
    privacyAccepted: form.elements.namedItem('privacyAccepted').checked
  });
  keys.forEach((key) => {
    const field = form.elements.namedItem(key);
    field.addEventListener(key === 'privacyAccepted' ? 'change' : 'input', () => showError(key, ''));
  });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    status.textContent = '';
    status.className = 'form-status';
    keys.forEach((key) => showError(key, ''));
    const data = valuesFromForm();
    const errors = validate(data);
    if (Object.keys(errors).length) {
      Object.entries(errors).forEach(([key, message]) => showError(key, message));
      status.textContent = 'Please correct the highlighted fields.';
      status.classList.add('form-status--error');
      form.elements.namedItem(Object.keys(errors)[0]).focus();
      return;
    }
    submit.disabled = true;
    submit.textContent = 'Sending message…';
    try {
      const response = await sendContact(data);
      const reference = typeof response?.referenceId === 'string' ? response.referenceId : '';
      const url = new URL('./thank-you.html', window.location.href);
      if (reference) url.searchParams.set('reference', reference);
      location.assign(url.href);
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        Object.entries(error.fieldErrors).forEach(([key, message]) => {
          if (keys.includes(key)) showError(key, message);
        });
      }
      status.textContent = error.status === 429
        ? 'Too many messages right now. Please try again later.'
        : (error.message || 'Unable to submit your message.');
      status.classList.add('form-status--error');
    } finally {
      submit.disabled = false;
      submit.textContent = 'Send message';
    }
  });
}

function initReceipt() {
  const receipt = document.getElementById('receipt-reference');
  if (!receipt) return;
  const reference = new URLSearchParams(location.search).get('reference');
  if (reference && /^[0-9a-f-]{36}$/i.test(reference)) {
    receipt.textContent = reference;
    receipt.parentElement.hidden = false;
  }
}

initTheme();
initSearch();
initFeedback();
initNavigation();
populateSections();
populateProfile();
initForm();
initReceipt();
initCookieConsent((accepted) => { setAnalyticsConsent(accepted); trackPageView(); });
const year = document.getElementById('copyright-year');
if (year) year.textContent = String(new Date().getFullYear());
```

## `frontend/js/search.js`

```javascript
import { getBlogPosts, getExperiences, getProjects } from './api.js';

const staticSkills = [
  'Java',
  'Spring Boot',
  'REST APIs',
  'MongoDB',
  'JavaScript',
  'HTML',
  'CSS',
  'UI/UX',
  'Full Stack Development',
  'Order Management Systems',
  'Computer Science Engineering',
  'NSEC'
];

function safeExternal(url) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' && !parsed.username && !parsed.password ? parsed.href : null;
  } catch {
    return null;
  }
}

function element(tag, className = '', text = '') {
  const node = document.createElement(tag);
  if (className) node.className = className;
  node.textContent = text;
  return node;
}

function markText(parent, text, needle) {
  const source = String(text ?? '');
  const normalized = source.toLocaleLowerCase();
  let cursor = 0;
  let match = normalized.indexOf(needle);

  while (match >= 0) {
    parent.append(document.createTextNode(source.slice(cursor, match)));
    parent.append(element('mark', '', source.slice(match, match + needle.length)));
    cursor = match + needle.length;
    match = normalized.indexOf(needle, cursor);
  }

  parent.append(document.createTextNode(source.slice(cursor)));
}

function experienceDetail(experience) {
  return [
    experience.company,
    experience.employmentType,
    experience.workMode,
    experience.location,
    ...(Array.isArray(experience.technologies) ? experience.technologies : [])
  ].filter(Boolean).join(' · ');
}

export function initSearch() {
  const modal = document.getElementById('site-search');
  const input = document.getElementById('search-input');
  const list = document.getElementById('search-results');
  if (!modal || !input || !list) return;

  let index = null;
  let lastTrigger = null;

  const load = async () => {
    if (index) return index;
    const [projects, experiences, blog] = await Promise.allSettled([
      getProjects(24),
      getExperiences(),
      getBlogPosts(24)
    ]);

    index = [
      ...(projects.status === 'fulfilled' ? projects.value : []).map((project) => ({
        type: 'Project',
        label: project.title,
        detail: [project.summary, ...(project.technologies || [])].filter(Boolean).join(' · '),
        href: typeof project.slug === 'string'
          ? `./index.html#project-${project.slug}`
          : './index.html#projects'
      })),
      ...(experiences.status === 'fulfilled' ? experiences.value : []).map((experience) => ({
        type: 'Experience',
        label: experience.role,
        detail: experienceDetail(experience),
        href: './index.html#experience'
      })),
      ...staticSkills.map((skill) => ({
        type: 'Skill',
        label: skill,
        detail: 'Technical expertise',
        href: './index.html#skills'
      })),
      ...(blog.status === 'fulfilled' ? blog.value : []).map((article) => ({
        type: 'Article',
        label: article.title,
        detail: article.excerpt,
        href: safeExternal(article.articleUrl) || './index.html#blog'
      }))
    ];

    return index;
  };

  const render = () => {
    const needle = input.value.trim().toLocaleLowerCase();
    list.replaceChildren();

    if (!needle) {
      list.append(element('p', 'search-empty', 'Search projects, experience, skills, and published articles.'));
      return;
    }

    const matches = (index || [])
      .filter((item) => `${item.label} ${item.detail}`.toLocaleLowerCase().includes(needle))
      .slice(0, 25);

    if (!matches.length) {
      list.append(element('p', 'search-empty', 'No matching items found.'));
      return;
    }

    matches.forEach((item) => {
      const link = element('a', 'search-result');
      link.href = item.href;
      link.append(element('span', 'search-result__type', item.type));

      const title = element('strong');
      markText(title, item.label, needle);
      link.append(title);

      const detail = element('small');
      markText(detail, item.detail, needle);
      link.append(detail);
      list.append(link);
    });
  };

  const open = async (trigger) => {
    if (!modal.open) {
      lastTrigger = trigger || document.activeElement;
      modal.showModal();
      input.focus();
    }

    list.replaceChildren(element('p', 'search-empty', 'Searching portfolio content…'));
    try {
      await load();
      render();
    } catch {
      list.replaceChildren(element('p', 'search-empty', 'Search is temporarily unavailable.'));
    }
  };

  document.querySelectorAll('[data-open-search]').forEach((button) => {
    button.addEventListener('click', () => open(button));
  });
  document.querySelectorAll('[data-close-search]').forEach((button) => {
    button.addEventListener('click', () => modal.close());
  });
  document.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      open();
    }
  });
  modal.addEventListener('close', () => {
    if (lastTrigger?.isConnected) lastTrigger.focus();
  });
  modal.addEventListener('click', (event) => {
    if (event.target === modal) modal.close();
  });
  input.addEventListener('input', render);
}
```

## `frontend/js/theme-init.js`

```javascript
(() => {
  const MODES = [
    { id: 'light', label: 'Light', icon: '☀', scheme: 'light', color: '#F4F7FB' },
    { id: 'dusk', label: 'Dusk', icon: '◐', scheme: 'light', color: '#AEB7C5' },
    { id: 'dark', label: 'Dark', icon: '☾', scheme: 'dark', color: '#0A0E17' }
  ];

  const modeFor = (value) => MODES.find((mode) => mode.id === value) || MODES[2];
  const nextMode = (value) => {
    const index = MODES.findIndex((mode) => mode.id === value);
    return MODES[(index + 1 + MODES.length) % MODES.length];
  };

  const sync = () => {
    const current = modeFor(document.documentElement.dataset.theme);
    const next = nextMode(current.id);
    const themeColor = document.querySelector('meta[name="theme-color"]');
    if (themeColor) themeColor.content = current.color;

    document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
      button.dataset.themeCurrent = current.id;
      button.setAttribute('aria-label', `Theme: ${current.label}. Switch to ${next.label}.`);
      button.title = `Theme: ${current.label} · Next: ${next.label}`;
      const icon = button.querySelector('[data-theme-icon]');
      const caption = button.querySelector('[data-theme-caption]');
      if (icon) icon.textContent = current.icon;
      if (caption) caption.textContent = current.label;
    });

    document.querySelectorAll('[data-theme-choice]').forEach((button) => {
      const selected = button.dataset.themeChoice === current.id;
      button.classList.toggle('is-active', selected);
      button.setAttribute('aria-pressed', String(selected));
    });
  };

  const apply = (value, persist = false) => {
    const current = modeFor(value);
    document.documentElement.dataset.theme = current.id;
    document.documentElement.style.colorScheme = current.scheme;
    if (persist) {
      try { localStorage.setItem('mlb-theme', current.id); } catch { }
    }
    sync();
  };

  let preference = null;
  try { preference = localStorage.getItem('mlb-theme'); } catch { }
  const known = MODES.some((mode) => mode.id === preference);
  const preferred = known
    ? preference
    : (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
  apply(preferred);

  document.addEventListener('click', (event) => {
    const toggle = event.target.closest('[data-theme-toggle]');
    if (toggle) {
      apply(nextMode(document.documentElement.dataset.theme).id, true);
      return;
    }

    const choice = event.target.closest('[data-theme-choice]');
    if (choice) apply(choice.dataset.themeChoice, true);
  });

  document.addEventListener('DOMContentLoaded', sync, { once: true });
  window.MLBTheme = { apply, sync };
})();
```

## `frontend/js/theme.js`

```javascript
const THEME_IDS = ['light', 'dusk', 'dark'];

export function initTheme() {
  if (!THEME_IDS.includes(document.documentElement.dataset.theme)) {
    document.documentElement.dataset.theme = 'dark';
  }
  window.MLBTheme?.sync();
}
```

## `frontend/js/ui.js`

```javascript
import { getProject } from './api.js';

const create = (tag, className = '', text = '') => {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== '') el.textContent = String(text);
  return el;
};

const append = (parent, ...children) => {
  parent.append(...children);
  return parent;
};

function validatedUrl(value) {
  if (typeof value !== 'string') return null;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
}

function externalLink(url, text, className = '') {
  const target = validatedUrl(url);
  if (!target) return null;
  const anchor = create('a', className, text);
  anchor.href = target;
  anchor.target = '_blank';
  anchor.rel = 'noopener noreferrer';
  return anchor;
}

export function renderSkeletons(container, count) {
  container.replaceChildren();
  for (let i = 0; i < count; i += 1) {
    const card = create('div', 'skeleton-card');
    card.setAttribute('aria-hidden', 'true');
    append(card,
      create('div', 'skeleton-block skeleton-card__image'),
      create('div', 'skeleton-block skeleton-card__line'),
      create('div', 'skeleton-block skeleton-card__line skeleton-card__line--short'));
    container.append(card);
  }
  container.setAttribute('aria-busy', 'true');
}

export function renderEmpty(container, heading, detail, glyph = '{}') {
  const panel = create('div', 'empty-state');
  append(panel,
    create('span', 'empty-state__icon', glyph),
    create('h3', '', heading),
    create('p', '', detail));
  container.replaceChildren(panel);
  container.removeAttribute('aria-busy');
}

function tagsRow(values) {
  const row = create('div', 'tags');
  (Array.isArray(values) ? values : []).slice(0, 15).forEach((value) => {
    row.append(create('span', 'tag', value));
  });
  return row;
}

function parseDate(value) {
  if (typeof value !== 'string') return null;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function monthLabel(value) {
  const date = parseDate(value);
  return date
    ? new Intl.DateTimeFormat('en', { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(date)
    : '';
}

function durationMonths(startValue, endValue, current) {
  const start = parseDate(startValue);
  const end = current ? new Date() : parseDate(endValue);
  if (!start || !end || end < start) return '';
  const months = (end.getUTCFullYear() - start.getUTCFullYear()) * 12
    + end.getUTCMonth() - start.getUTCMonth() + 1;
  return `${months} mo${months === 1 ? '' : 's'}`;
}

function employmentLabel(value) {
  if (value === 'Internship · Part-Time') return 'Part-Time Internship';
  return typeof value === 'string' ? value.replace(' · ', ' ') : '';
}

function workModeLabel(value) {
  if (typeof value !== 'string') return '';
  const match = value.match(/\(([^)]+)\)/);
  return match ? match[1] : value;
}

export function renderExperiences(container, experiences) {
  if (!Array.isArray(experiences) || experiences.length === 0) {
    renderEmpty(container, 'Experience details coming soon',
      'Published work experience will appear here when available.', '↗');
    return;
  }

  const timeline = create('div', 'experience-timeline');
  experiences.forEach((experience) => {
    const card = create('article', 'experience-card');
    const marker = create('span', 'experience-card__marker');
    marker.setAttribute('aria-hidden', 'true');

    const header = create('div', 'experience-card__header');
    const heading = create('div');
    append(heading,
      create('h3', '', experience.role || 'Role'),
      create('span', 'experience-company', experience.company || 'Company'));
    header.append(heading);

    const meta = create('div', 'experience-meta');
    [employmentLabel(experience.employmentType), workModeLabel(experience.workMode), experience.location]
      .filter(Boolean)
      .forEach((value) => meta.append(create('span', 'experience-pill', value)));

    const start = monthLabel(experience.startDate);
    const end = experience.current ? 'Present' : monthLabel(experience.endDate);
    const months = durationMonths(experience.startDate, experience.endDate, experience.current);
    const period = [start && end ? `${start} – ${end}` : start, months].filter(Boolean).join(' • ');
    if (period) meta.append(create('span', 'experience-pill experience-pill--date', period));

    card.append(marker, header, meta);
    if (experience.summary) card.append(create('p', 'experience-summary', experience.summary));

    const highlights = Array.isArray(experience.highlights)
      ? experience.highlights.filter((value) => typeof value === 'string' && value.trim()).slice(0, 8)
      : [];
    if (highlights.length) {
      const list = create('ul', 'experience-highlights');
      highlights.forEach((value) => list.append(create('li', '', value)));
      card.append(list);
    }

    card.append(tagsRow(experience.technologies));
    timeline.append(card);
  });

  container.replaceChildren(timeline);
  container.removeAttribute('aria-busy');
}

const caseStudyProjects = new Map();
let caseStudyListenersBound = false;
let caseStudyTrigger = null;

function caseStudyHash(slug) {
  return `#project-${slug}`;
}

function projectSlugFromHash() {
  const match = location.hash.match(/^#project-([a-z0-9]+(?:-[a-z0-9]+)*)$/);
  return match ? match[1] : null;
}

function caseStudySection(title, text) {
  if (typeof text !== 'string' || !text.trim()) return null;
  const section = create('section', 'case-study-section');
  append(section, create('h3', '', title), create('p', '', text));
  return section;
}

function architectureFlow(values) {
  const steps = Array.isArray(values) ? values.filter((value) => typeof value === 'string' && value.trim()).slice(0, 8) : [];
  if (!steps.length) return null;

  const section = create('section', 'case-study-section');
  section.append(create('h3', '', 'Architecture Flow'));
  const list = create('ol', 'architecture-flow');
  steps.forEach((step, index) => {
    const item = create('li', 'architecture-flow__step');
    const marker = create('span', 'architecture-flow__marker', String(index + 1).padStart(2, '0'));
    marker.setAttribute('aria-hidden', 'true');
    item.append(marker, create('span', 'architecture-flow__text', step));
    list.append(item);
  });
  section.append(list);
  return section;
}

function metricGrid(values) {
  const metrics = Array.isArray(values) ? values.filter((value) => typeof value === 'string' && value.trim()).slice(0, 4) : [];
  if (!metrics.length) return null;

  const section = create('section', 'case-study-section');
  section.append(create('h3', '', 'Key Metrics'));
  const grid = create('div', 'case-study-metrics');
  metrics.forEach((metric) => grid.append(create('span', 'case-study-metric', metric)));
  section.append(grid);
  return section;
}

function renderCaseStudy(project) {
  const content = document.getElementById('case-study-content');
  const title = document.getElementById('case-study-title');
  if (!content || !title) return;

  title.textContent = project.title || 'Project case study';
  content.replaceChildren();

  const intro = create('div', 'case-study-intro');
  intro.append(create('p', 'case-study-kicker', 'Engineering Case Study'));
  intro.append(create('p', 'case-study-summary', project.summary || 'Project overview'));
  content.append(intro);

  const problem = caseStudySection('Problem', project.problemStatement || project.description);
  if (problem) content.append(problem);

  const architecture = caseStudySection('Architecture & Solution', project.architectureOverview || project.description);
  if (architecture) content.append(architecture);

  const flow = architectureFlow(project.architectureFlow);
  if (flow) content.append(flow);

  const metrics = metricGrid(project.keyMetrics);
  if (metrics) content.append(metrics);

  const stack = Array.isArray(project.technologies) && project.technologies.length
    ? append(create('section', 'case-study-section'), create('h3', '', 'Tech Stack'), tagsRow(project.technologies))
    : null;
  if (stack) content.append(stack);

  const actions = create('div', 'case-study-actions');
  const live = externalLink(project.liveUrl, 'Open Live Demo ↗', 'button button--primary');
  const github = externalLink(project.githubUrl, 'View GitHub ↗', 'button button--secondary');
  if (live) actions.append(live);
  if (github) actions.append(github);
  if (actions.childElementCount) content.append(actions);
}

function openCaseStudy(project, updateHash = true, trigger = null) {
  const dialog = document.getElementById('project-case-study');
  if (!dialog) return;
  caseStudyTrigger = trigger || document.activeElement;
  renderCaseStudy(project);
  if (updateHash && location.hash !== caseStudyHash(project.slug)) {
    history.pushState(null, '', caseStudyHash(project.slug));
  }
  if (!dialog.open) dialog.showModal();
}

async function openCaseStudyFromHash() {
  const dialog = document.getElementById('project-case-study');
  const slug = projectSlugFromHash();
  if (!slug) {
    if (dialog?.open) dialog.close();
    return;
  }
  let project = caseStudyProjects.get(slug);
  if (!project) {
    try {
      project = await getProject(slug);
      caseStudyProjects.set(slug, project);
    } catch {
      return;
    }
  }
  openCaseStudy(project, false);
}

function bindCaseStudyDialog() {
  if (caseStudyListenersBound) return;
  const dialog = document.getElementById('project-case-study');
  if (!dialog) return;
  caseStudyListenersBound = true;

  document.querySelectorAll('[data-close-case-study]').forEach((button) => {
    button.addEventListener('click', () => dialog.close());
  });
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });
  dialog.addEventListener('close', () => {
    if (projectSlugFromHash()) {
      history.replaceState(null, '', `${location.pathname}${location.search}`);
    }
    if (caseStudyTrigger?.isConnected) caseStudyTrigger.focus();
    caseStudyTrigger = null;
  });
  window.addEventListener('hashchange', openCaseStudyFromHash);
}

export function renderProjects(container, projects) {
  if (!Array.isArray(projects) || projects.length === 0) {
    renderEmpty(container, 'The next project is taking shape',
      'Featured work will appear here as soon as it is published. Check back soon.');
    return;
  }

  bindCaseStudyDialog();
  caseStudyProjects.clear();
  const fragment = document.createDocumentFragment();

  projects.forEach((project) => {
    if (typeof project.slug === 'string') caseStudyProjects.set(project.slug, project);

    const card = create('article', 'project-card');
    const visual = create('div', 'project-card__visual');
    const imageUrl = validatedUrl(project.imageUrl);
    if (imageUrl && typeof project.imageAlt === 'string' && project.imageAlt.trim()) {
      const image = create('img');
      image.src = imageUrl;
      image.alt = project.imageAlt;
      image.loading = 'lazy';
      image.decoding = 'async';
      visual.append(image);
    } else {
      const glyph = create('span', 'project-card__glyph', '</>');
      glyph.setAttribute('aria-hidden', 'true');
      visual.append(glyph);
    }

    const body = create('div', 'project-card__body');
    append(body,
      create('h3', '', project.title || 'Untitled project'),
      create('p', '', project.summary || 'Project overview unavailable.'),
      tagsRow(project.technologies));

    const links = create('div', 'card-links');
    const caseStudy = create('button', 'case-study-trigger', 'Read Case Study');
    caseStudy.type = 'button';
    caseStudy.addEventListener('click', () => openCaseStudy(project, true, caseStudy));
    links.append(caseStudy);

    const live = externalLink(project.liveUrl, 'Live site ↗');
    const code = externalLink(project.githubUrl, 'Source code ↗');
    if (live) links.append(live);
    if (code) links.append(code);
    body.append(links);

    append(card, visual, body);
    fragment.append(card);
  });

  container.replaceChildren(fragment);
  container.removeAttribute('aria-busy');
  openCaseStudyFromHash();
}

export function renderBlogPosts(container, posts) {
  if (!Array.isArray(posts) || posts.length === 0) {
    renderEmpty(container, 'Articles are on their way',
      'Technical notes and development insights will appear here when published.', '✎');
    return;
  }

  const fragment = document.createDocumentFragment();
  posts.forEach((post) => {
    const card = create('article', 'blog-card');
    const date = post.publishedAt ? new Date(post.publishedAt) : null;
    const dateText = date && !Number.isNaN(date.getTime())
      ? new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
      : 'Article';
    append(card,
      create('span', 'blog-card__date', dateText),
      create('h3', '', post.title || 'Untitled article'),
      create('p', '', post.excerpt || ''),
      tagsRow(post.tags));
    const link = externalLink(post.articleUrl, 'Read article ↗', 'blog-card__link');
    if (link) card.append(link);
    fragment.append(card);
  });

  container.replaceChildren(fragment);
  container.removeAttribute('aria-busy');
}

export function initCookieConsent(onChange) {
  const banner = document.getElementById('cookie-banner');
  if (!banner) return;

  let existing = null;
  try {
    existing = localStorage.getItem('mlb-analytics-consent');
  } catch {
    existing = null;
  }

  if (existing === 'accepted' || existing === 'rejected') {
    onChange(existing === 'accepted');
    return;
  }

  banner.hidden = false;
  for (const button of banner.querySelectorAll('[data-consent]')) {
    button.addEventListener('click', () => {
      const accepted = button.dataset.consent === 'accepted';
      try {
        localStorage.setItem('mlb-analytics-consent', accepted ? 'accepted' : 'rejected');
      } catch {
      }
      banner.hidden = true;
      onChange(accepted);
    });
  }
}
```

## `frontend/privacy.html`

```html
<!doctype html>
<html lang="en" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; connect-src 'self' http://localhost:8080 https://*.onrender.com; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'">
  <meta name="mlb-api-origin" content="">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" content="#0A0E17">
  <meta name="description" content="Read the MLB portfolio privacy policy covering contact form submissions, data retention, analytics consent and data protection requests.">
  <meta name="robots" content="noindex,follow">
  <title>Privacy Policy | MLB Developer Portfolio</title>
  <link rel="canonical" href="https://mufaddalbatti53.github.io/MLB-Portfolio/privacy.html">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Mufaddal Battiwala Portfolio">
  <meta property="og:title" content="Privacy Policy | MLB Developer Portfolio">
  <meta property="og:description" content="Read the MLB portfolio privacy policy covering contact form submissions, data retention, analytics consent and data protection requests.">
  <meta property="og:url" content="https://mufaddalbatti53.github.io/MLB-Portfolio/privacy.html">
  <meta property="og:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <meta property="og:image:alt" content="Mufaddal Battiwala full stack developer portfolio">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="Privacy Policy | MLB Developer Portfolio">
  <meta name="twitter:description" content="Read the MLB portfolio privacy policy covering contact form submissions, data retention, analytics consent and data protection requests.">
  <meta name="twitter:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <link rel="icon" href="./assets/favicon.ico" sizes="any">
  <link rel="icon" href="./assets/favicon.svg" type="image/svg+xml">
  <link rel="icon" href="./assets/favicon-32x32.png" type="image/png" sizes="32x32">
  <link rel="apple-touch-icon" href="./assets/apple-touch-icon.png">
  <link rel="manifest" href="./site.webmanifest">
  <script src="./js/theme-init.js"></script>
  <link rel="stylesheet" href="./css/main.css">
</head>
<body>

<a class="skip-link" href="#main-content">Skip to main content</a>
<header class="site-header">
  <div class="container site-header__inner">
    <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
    <button class="nav-toggle" id="nav-toggle" type="button" aria-label="Toggle navigation" aria-controls="site-nav" aria-expanded="false">Menu ☰</button>
    <nav class="site-nav" id="site-nav" aria-label="Primary navigation">
      <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
      <a href="./#skills">Expertise</a>
      <a href="./#blog">Writing</a>
      <a href="./#contact">Contact</a>
      <div class="mobile-theme-panel" aria-label="Choose theme">
        <span class="mobile-theme-panel__label">Theme</span>
        <div class="mobile-theme-options" role="group" aria-label="Theme options">
          <button class="mobile-theme-option" type="button" data-theme-choice="light" aria-pressed="false"><span aria-hidden="true">☀</span> Light</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dusk" aria-pressed="false"><span aria-hidden="true">◐</span> Dusk</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dark" aria-pressed="false"><span aria-hidden="true">☾</span> Dark</button>
        </div>
      </div>
    </nav>
<div class="header-utilities" role="group" aria-label="Site tools"><button class="utility-button" type="button" data-open-search aria-label="Search portfolio (Ctrl+K)" title="Search (Ctrl+K)"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></svg></button><button class="utility-button theme-toggle" type="button" data-theme-toggle aria-label="Theme: Dark. Switch to Light." title="Theme: Dark · Next: Light"><span class="theme-toggle__icon" data-theme-icon aria-hidden="true">☾</span><span class="theme-toggle__caption" data-theme-caption>Dark</span></button><button class="utility-button utility-button--feedback" type="button" data-open-feedback aria-label="Give site feedback"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l2-3A8.5 8.5 0 1 1 21 11.5Z"/><path d="M8 10h8M8 14h5"/></svg><span>Feedback</span></button></div>
    <a class="button button--small button--secondary nav-action" href="./#contact">Let's connect <span aria-hidden="true">↗</span></a>
  </div>
</header>


<main id="main-content"><section class="legal-hero"><div class="container"><span class="eyebrow">Trust &amp; transparency</span><h1>Privacy <span class="gradient-text">Policy.</span></h1><p>What data this portfolio collects, why it is needed, and how you can ask for it to be removed.</p><p>Effective date: 16 November 2026</p></div></section>
<div class="container"><article class="legal-layout">
  <h2>1. Who operates this website?</h2><p>MLB is a personal developer portfolio maintained by Mufaddal Battiwala. You can contact the operator at <a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a>.</p>
  <h2>2. Information you choose to submit</h2><p>When you send a contact form, we collect the name, email address, subject, message, and your acknowledgment of this policy. A random receipt ID and submission time are generated. We use these details solely to respond to the inquiry, protect the form from misuse and manage delivery.</p>
  <h2>3. How messages are handled</h2><p>Messages are stored in the site's MongoDB database and may be forwarded by authenticated SMTP to the portfolio operator's email inbox. Database records are configured for automatic deletion after 90 days; copies delivered by email can require separate deletion. We do not sell contact data.</p>
  <h2>4. Abuse prevention and server logs</h2><p>Form requests are rate-limited using short-lived HMAC-derived client identifiers. Web servers may temporarily process IP addresses and request metadata for security and service operation. Do not submit passwords, payment-card details or other sensitive material through the public form.</p>
  <h2>5. Site feedback</h2><p>If you submit site feedback, we store your 1–5 rating, category, optional comment, and submission timestamp for up to 90 days. The feedback form does not ask for a name or email; technical rate-limit identifiers expire independently.</p>
  <h2>6. Analytics preferences</h2><p>By default, this website does not load third-party analytics or advertising scripts. The consent banner stores your preference in your browser's local storage; optional analytics will only run if a provider is explicitly configured and you opt in. No analytics provider is preconfigured in this release.</p>
  <h2>7. Sharing and processors</h2><p>Database, hosting and email infrastructure providers may process the minimum information necessary to operate the site under their own applicable agreements. External project, GitHub, LinkedIn and article links have separate privacy policies.</p>
  <h2>8. Your choices and requests</h2><p>To request access, correction or deletion of a contact inquiry, email <a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a> with enough detail to identify your message. You can reset the analytics preference by removing this site's local storage in your browser. Applicable legal rights depend on your jurisdiction.</p>
  <h2>9. Updates</h2><p>This policy may change when site features or service providers change. Material updates will be reflected on this page along with a revised effective date.</p>
</article></div></main>


<footer class="site-footer">
  <div class="container">
    <div class="footer-main">
      <div>
        <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
        <p>Thoughtful engineering, practical solutions, and a focus on building better digital experiences.</p>
        <p><a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a><span aria-hidden="true"> · </span>Kolkata, India</p>
      </div>
      <nav class="footer-nav" aria-label="Footer navigation">
        <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
        <a href="./#skills">Expertise</a>
        <a href="./#blog">Writing</a>
        <a href="./#contact">Contact</a>
        <a href="https://github.com/mufaddalbatti53" data-profile-github target="_blank" rel="noopener noreferrer">GitHub ↗</a>
        <a href="https://www.linkedin.com/in/mufaddal-tech/" data-profile-linkedin target="_blank" rel="noopener noreferrer">LinkedIn ↗</a>
      </nav>
    </div>
    <div class="footer-bottom">
      <span>© <span id="copyright-year">2026</span> MLB. Built with care.</span>
      <div class="footer-bottom-links"><a href="./privacy.html">Privacy</a><a href="./terms.html">Terms</a><a href="mailto:mufaddal.techpro@gmail.com">Email</a></div>
    </div>
  </div>
</footer>
<div class="mobile-cta"><span>Have an idea in mind?</span><a class="button button--primary" href="./#contact">Let's talk <span aria-hidden="true">↗</span></a></div>
<aside class="cookie-banner" id="cookie-banner" hidden aria-label="Analytics consent preferences">
  <div class="cookie-banner__body">
    <div><strong>Your privacy matters</strong><p>This site only stores your analytics preference locally. Optional analytics is inactive by default and only runs with permission. <a href="./privacy.html">Privacy details</a>.</p></div>
    <div class="cookie-banner__actions"><button type="button" class="button button--secondary" data-consent="rejected">Decline</button><button type="button" class="button button--primary" data-consent="accepted">Accept</button></div>
  </div>
</aside>
<dialog id="site-search" class="app-modal" aria-labelledby="search-title"><div class="modal-body"><div class="modal-heading"><h2 id="search-title">Search MLB</h2><button class="utility-button" data-close-search type="button" aria-label="Close search">✕</button></div><label for="search-input">Find projects, experience, skills and articles</label><input type="search" id="search-input" autocomplete="off" aria-controls="search-results"/><div id="search-results" class="search-results" role="region" aria-live="polite"></div><small>Press Ctrl+K to open · Esc to close</small></div></dialog>
<dialog id="site-feedback" class="app-modal" aria-labelledby="feedback-title"><div class="modal-body"><div class="modal-heading"><h2 id="feedback-title">Site feedback</h2><button class="utility-button" data-close-feedback type="button" aria-label="Close feedback">✕</button></div><p>Help improve the MLB portfolio experience.</p><form id="feedback-form"><fieldset class="stars"><legend>How was your experience?</legend><label><input type="radio" name="rating" value="1" required/><span aria-label="1 stars">★ 1</span></label><label><input type="radio" name="rating" value="2" required/><span aria-label="2 stars">★ 2</span></label><label><input type="radio" name="rating" value="3" required/><span aria-label="3 stars">★ 3</span></label><label><input type="radio" name="rating" value="4" required/><span aria-label="4 stars">★ 4</span></label><label><input type="radio" name="rating" value="5" required/><span aria-label="5 stars">★ 5</span></label></fieldset><label class="field">Category <select name="category" required><option value="UI_UX">UI/UX</option><option value="BUG">Bug</option><option value="FEATURE_IDEA">Feature Idea</option><option value="GENERAL">General</option></select></label><label class="field">Comment (optional)<textarea name="comment" maxlength="500" rows="4"></textarea></label><div class="honeypot" aria-hidden="true"><label for="feedback-website">Leave empty</label><input id="feedback-website" name="website" maxlength="200" tabindex="-1" autocomplete="off"/></div><p role="status" aria-live="polite" id="feedback-status"></p><button class="button button--primary" type="submit">Send feedback</button></form></div></dialog>
<script type="module" src="./js/main.js"></script>

</body>
</html>
```

## `frontend/robots.txt`

```text
User-agent: *
Allow: /

Sitemap: https://mufaddalbatti53.github.io/MLB-Portfolio/sitemap.xml
```

## `frontend/site.webmanifest`

```text
{
  "id": "./",
  "name": "Mufaddal Battiwala — Full Stack Developer",
  "short_name": "Mufaddal",
  "description": "Official portfolio of Mufaddal Battiwala, Full Stack Developer in Kolkata.",
  "start_url": "./",
  "scope": "./",
  "display": "browser",
  "background_color": "#0A0E17",
  "theme_color": "#0A0E17",
  "icons": [
    {
      "src": "./assets/favicon.svg",
      "sizes": "any",
      "type": "image/svg+xml"
    },
    {
      "src": "./assets/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "./assets/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

## `frontend/sitemap.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>https://mufaddalbatti53.github.io/MLB-Portfolio/</loc>
    <lastmod>2026-11-24</lastmod>
    <changefreq>weekly</changefreq>
    <priority>1.0</priority>
  </url>
</urlset>
```

## `frontend/terms.html`

```html
<!doctype html>
<html lang="en" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; connect-src 'self' http://localhost:8080 https://*.onrender.com; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'">
  <meta name="mlb-api-origin" content="">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" content="#0A0E17">
  <meta name="description" content="Read the MLB portfolio terms of use for site content, intellectual property, external links, availability and acceptable use.">
  <meta name="robots" content="noindex,follow">
  <title>Terms of Use | MLB Developer Portfolio</title>
  <link rel="canonical" href="https://mufaddalbatti53.github.io/MLB-Portfolio/terms.html">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Mufaddal Battiwala Portfolio">
  <meta property="og:title" content="Terms of Use | MLB Developer Portfolio">
  <meta property="og:description" content="Read the MLB portfolio terms of use for site content, intellectual property, external links, availability and acceptable use.">
  <meta property="og:url" content="https://mufaddalbatti53.github.io/MLB-Portfolio/terms.html">
  <meta property="og:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <meta property="og:image:alt" content="Mufaddal Battiwala full stack developer portfolio">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="Terms of Use | MLB Developer Portfolio">
  <meta name="twitter:description" content="Read the MLB portfolio terms of use for site content, intellectual property, external links, availability and acceptable use.">
  <meta name="twitter:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <link rel="icon" href="./assets/favicon.ico" sizes="any">
  <link rel="icon" href="./assets/favicon.svg" type="image/svg+xml">
  <link rel="icon" href="./assets/favicon-32x32.png" type="image/png" sizes="32x32">
  <link rel="apple-touch-icon" href="./assets/apple-touch-icon.png">
  <link rel="manifest" href="./site.webmanifest">
  <script src="./js/theme-init.js"></script>
  <link rel="stylesheet" href="./css/main.css">
</head>
<body>

<a class="skip-link" href="#main-content">Skip to main content</a>
<header class="site-header">
  <div class="container site-header__inner">
    <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
    <button class="nav-toggle" id="nav-toggle" type="button" aria-label="Toggle navigation" aria-controls="site-nav" aria-expanded="false">Menu ☰</button>
    <nav class="site-nav" id="site-nav" aria-label="Primary navigation">
      <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
      <a href="./#skills">Expertise</a>
      <a href="./#blog">Writing</a>
      <a href="./#contact">Contact</a>
      <div class="mobile-theme-panel" aria-label="Choose theme">
        <span class="mobile-theme-panel__label">Theme</span>
        <div class="mobile-theme-options" role="group" aria-label="Theme options">
          <button class="mobile-theme-option" type="button" data-theme-choice="light" aria-pressed="false"><span aria-hidden="true">☀</span> Light</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dusk" aria-pressed="false"><span aria-hidden="true">◐</span> Dusk</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dark" aria-pressed="false"><span aria-hidden="true">☾</span> Dark</button>
        </div>
      </div>
    </nav>
<div class="header-utilities" role="group" aria-label="Site tools"><button class="utility-button" type="button" data-open-search aria-label="Search portfolio (Ctrl+K)" title="Search (Ctrl+K)"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></svg></button><button class="utility-button theme-toggle" type="button" data-theme-toggle aria-label="Theme: Dark. Switch to Light." title="Theme: Dark · Next: Light"><span class="theme-toggle__icon" data-theme-icon aria-hidden="true">☾</span><span class="theme-toggle__caption" data-theme-caption>Dark</span></button><button class="utility-button utility-button--feedback" type="button" data-open-feedback aria-label="Give site feedback"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l2-3A8.5 8.5 0 1 1 21 11.5Z"/><path d="M8 10h8M8 14h5"/></svg><span>Feedback</span></button></div>
    <a class="button button--small button--secondary nav-action" href="./#contact">Let's connect <span aria-hidden="true">↗</span></a>
  </div>
</header>


<main id="main-content"><section class="legal-hero"><div class="container"><span class="eyebrow">Site information</span><h1>Terms of <span class="gradient-text">Use.</span></h1><p>Guidelines for viewing, sharing and interacting with this personal developer portfolio.</p><p>Effective date: 16 November 2026</p></div></section>
<div class="container"><article class="legal-layout">
  <h2>1. Purpose</h2><p>MLB presents developer projects, professional interests, technical writing and ways to get in touch. Using the website means agreeing to use its public features lawfully and respectfully.</p>
  <h2>2. Content and ownership</h2><p>Unless noted otherwise, site design, text and original portfolio materials belong to their respective creators. Third-party brands, libraries and referenced content retain their original ownership. Source repositories may have their own individual software licenses.</p>
  <h2>3. Permitted use</h2><p>You may browse the site and share links to public pages. You must not attempt to disrupt the service, bypass rate limits, send spam, probe private infrastructure or access non-public data without authorization.</p>
  <h2>4. External links</h2><p>Links to code hosting, social networks, project demonstrations and technical articles are provided for convenience. Their availability, content and terms are governed by their respective operators.</p>
  <h2>5. Availability and accuracy</h2><p>Portfolio information is provided for general informational purposes. While reasonable efforts are made to keep information accurate and accessible, continuous availability or error-free operation cannot be guaranteed.</p>
  <h2>6. Contact and privacy</h2><p>Contact form submissions are handled as described in the <a href="./privacy.html">Privacy Policy</a>. For questions about this website or its content, email <a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a>.</p>
  <h2>7. Updates</h2><p>These terms may be updated as the site evolves. Material changes will be published here with a revised effective date. Nothing here excludes rights that cannot legally be waived.</p>
</article></div></main>


<footer class="site-footer">
  <div class="container">
    <div class="footer-main">
      <div>
        <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
        <p>Thoughtful engineering, practical solutions, and a focus on building better digital experiences.</p>
        <p><a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a><span aria-hidden="true"> · </span>Kolkata, India</p>
      </div>
      <nav class="footer-nav" aria-label="Footer navigation">
        <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
        <a href="./#skills">Expertise</a>
        <a href="./#blog">Writing</a>
        <a href="./#contact">Contact</a>
        <a href="https://github.com/mufaddalbatti53" data-profile-github target="_blank" rel="noopener noreferrer">GitHub ↗</a>
        <a href="https://www.linkedin.com/in/mufaddal-tech/" data-profile-linkedin target="_blank" rel="noopener noreferrer">LinkedIn ↗</a>
      </nav>
    </div>
    <div class="footer-bottom">
      <span>© <span id="copyright-year">2026</span> MLB. Built with care.</span>
      <div class="footer-bottom-links"><a href="./privacy.html">Privacy</a><a href="./terms.html">Terms</a><a href="mailto:mufaddal.techpro@gmail.com">Email</a></div>
    </div>
  </div>
</footer>
<div class="mobile-cta"><span>Have an idea in mind?</span><a class="button button--primary" href="./#contact">Let's talk <span aria-hidden="true">↗</span></a></div>
<aside class="cookie-banner" id="cookie-banner" hidden aria-label="Analytics consent preferences">
  <div class="cookie-banner__body">
    <div><strong>Your privacy matters</strong><p>This site only stores your analytics preference locally. Optional analytics is inactive by default and only runs with permission. <a href="./privacy.html">Privacy details</a>.</p></div>
    <div class="cookie-banner__actions"><button type="button" class="button button--secondary" data-consent="rejected">Decline</button><button type="button" class="button button--primary" data-consent="accepted">Accept</button></div>
  </div>
</aside>
<dialog id="site-search" class="app-modal" aria-labelledby="search-title"><div class="modal-body"><div class="modal-heading"><h2 id="search-title">Search MLB</h2><button class="utility-button" data-close-search type="button" aria-label="Close search">✕</button></div><label for="search-input">Find projects, experience, skills and articles</label><input type="search" id="search-input" autocomplete="off" aria-controls="search-results"/><div id="search-results" class="search-results" role="region" aria-live="polite"></div><small>Press Ctrl+K to open · Esc to close</small></div></dialog>
<dialog id="site-feedback" class="app-modal" aria-labelledby="feedback-title"><div class="modal-body"><div class="modal-heading"><h2 id="feedback-title">Site feedback</h2><button class="utility-button" data-close-feedback type="button" aria-label="Close feedback">✕</button></div><p>Help improve the MLB portfolio experience.</p><form id="feedback-form"><fieldset class="stars"><legend>How was your experience?</legend><label><input type="radio" name="rating" value="1" required/><span aria-label="1 stars">★ 1</span></label><label><input type="radio" name="rating" value="2" required/><span aria-label="2 stars">★ 2</span></label><label><input type="radio" name="rating" value="3" required/><span aria-label="3 stars">★ 3</span></label><label><input type="radio" name="rating" value="4" required/><span aria-label="4 stars">★ 4</span></label><label><input type="radio" name="rating" value="5" required/><span aria-label="5 stars">★ 5</span></label></fieldset><label class="field">Category <select name="category" required><option value="UI_UX">UI/UX</option><option value="BUG">Bug</option><option value="FEATURE_IDEA">Feature Idea</option><option value="GENERAL">General</option></select></label><label class="field">Comment (optional)<textarea name="comment" maxlength="500" rows="4"></textarea></label><div class="honeypot" aria-hidden="true"><label for="feedback-website">Leave empty</label><input id="feedback-website" name="website" maxlength="200" tabindex="-1" autocomplete="off"/></div><p role="status" aria-live="polite" id="feedback-status"></p><button class="button button--primary" type="submit">Send feedback</button></form></div></dialog>
<script type="module" src="./js/main.js"></script>

</body>
</html>
```

## `frontend/thank-you.html`

```html
<!doctype html>
<html lang="en" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; connect-src 'self' http://localhost:8080 https://*.onrender.com; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'">
  <meta name="mlb-api-origin" content="">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" content="#0A0E17">
  <meta name="description" content="Your contact message has been received by MLB. You can return to the developer portfolio to see projects and read technical articles.">
  <meta name="robots" content="noindex,follow">
  <title>Thank You — Message Received | MLB Portfolio</title>
  <link rel="canonical" href="https://mufaddalbatti53.github.io/MLB-Portfolio/thank-you.html">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Mufaddal Battiwala Portfolio">
  <meta property="og:title" content="Thank You — Message Received | MLB Portfolio">
  <meta property="og:description" content="Your contact message has been received by MLB. You can return to the developer portfolio to see projects and read technical articles.">
  <meta property="og:url" content="https://mufaddalbatti53.github.io/MLB-Portfolio/thank-you.html">
  <meta property="og:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <meta property="og:image:alt" content="Mufaddal Battiwala full stack developer portfolio">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="Thank You — Message Received | MLB Portfolio">
  <meta name="twitter:description" content="Your contact message has been received by MLB. You can return to the developer portfolio to see projects and read technical articles.">
  <meta name="twitter:image" content="https://mufaddalbatti53.github.io/MLB-Portfolio/assets/og-preview.png">
  <link rel="icon" href="./assets/favicon.ico" sizes="any">
  <link rel="icon" href="./assets/favicon.svg" type="image/svg+xml">
  <link rel="icon" href="./assets/favicon-32x32.png" type="image/png" sizes="32x32">
  <link rel="apple-touch-icon" href="./assets/apple-touch-icon.png">
  <link rel="manifest" href="./site.webmanifest">
  <script src="./js/theme-init.js"></script>
  <link rel="stylesheet" href="./css/main.css">
</head>
<body>

<a class="skip-link" href="#main-content">Skip to main content</a>
<header class="site-header">
  <div class="container site-header__inner">
    <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
    <button class="nav-toggle" id="nav-toggle" type="button" aria-label="Toggle navigation" aria-controls="site-nav" aria-expanded="false">Menu ☰</button>
    <nav class="site-nav" id="site-nav" aria-label="Primary navigation">
      <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
      <a href="./#skills">Expertise</a>
      <a href="./#blog">Writing</a>
      <a href="./#contact">Contact</a>
      <div class="mobile-theme-panel" aria-label="Choose theme">
        <span class="mobile-theme-panel__label">Theme</span>
        <div class="mobile-theme-options" role="group" aria-label="Theme options">
          <button class="mobile-theme-option" type="button" data-theme-choice="light" aria-pressed="false"><span aria-hidden="true">☀</span> Light</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dusk" aria-pressed="false"><span aria-hidden="true">◐</span> Dusk</button>
          <button class="mobile-theme-option" type="button" data-theme-choice="dark" aria-pressed="false"><span aria-hidden="true">☾</span> Dark</button>
        </div>
      </div>
    </nav>
<div class="header-utilities" role="group" aria-label="Site tools"><button class="utility-button" type="button" data-open-search aria-label="Search portfolio (Ctrl+K)" title="Search (Ctrl+K)"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></svg></button><button class="utility-button theme-toggle" type="button" data-theme-toggle aria-label="Theme: Dark. Switch to Light." title="Theme: Dark · Next: Light"><span class="theme-toggle__icon" data-theme-icon aria-hidden="true">☾</span><span class="theme-toggle__caption" data-theme-caption>Dark</span></button><button class="utility-button utility-button--feedback" type="button" data-open-feedback aria-label="Give site feedback"><svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l2-3A8.5 8.5 0 1 1 21 11.5Z"/><path d="M8 10h8M8 14h5"/></svg><span>Feedback</span></button></div>
    <a class="button button--small button--secondary nav-action" href="./#contact">Let's connect <span aria-hidden="true">↗</span></a>
  </div>
</header>


<main id="main-content" class="status-page"><div class="container status-panel">
  <div class="status-panel__symbol" aria-hidden="true">✓</div>
  <span class="eyebrow">Transmission received</span>
  <h1>Your message is on its way.</h1>
  <p>Thanks for reaching out. Your inquiry has been saved and will be reviewed as soon as possible.</p>
  <p class="receipt" hidden>Reference: <strong id="receipt-reference"></strong></p>
  <a href="./" class="button button--primary">Return home <span aria-hidden="true">↗</span></a>
</div></main>


<footer class="site-footer">
  <div class="container">
    <div class="footer-main">
      <div>
        <a class="brand" href="./" aria-label="Mufaddal Battiwala portfolio home"><img class="brand__svg" src="./assets/logo.svg" width="60" height="32" alt="" aria-hidden="true"></a>
        <p>Thoughtful engineering, practical solutions, and a focus on building better digital experiences.</p>
        <p><a data-profile-email href="mailto:mufaddal.techpro@gmail.com">mufaddal.techpro@gmail.com</a><span aria-hidden="true"> · </span>Kolkata, India</p>
      </div>
      <nav class="footer-nav" aria-label="Footer navigation">
        <a href="./#projects">Projects</a>
      <a href="./#experience">Experience</a>
        <a href="./#skills">Expertise</a>
        <a href="./#blog">Writing</a>
        <a href="./#contact">Contact</a>
        <a href="https://github.com/mufaddalbatti53" data-profile-github target="_blank" rel="noopener noreferrer">GitHub ↗</a>
        <a href="https://www.linkedin.com/in/mufaddal-tech/" data-profile-linkedin target="_blank" rel="noopener noreferrer">LinkedIn ↗</a>
      </nav>
    </div>
    <div class="footer-bottom">
      <span>© <span id="copyright-year">2026</span> MLB. Built with care.</span>
      <div class="footer-bottom-links"><a href="./privacy.html">Privacy</a><a href="./terms.html">Terms</a><a href="mailto:mufaddal.techpro@gmail.com">Email</a></div>
    </div>
  </div>
</footer>
<div class="mobile-cta"><span>Have an idea in mind?</span><a class="button button--primary" href="./#contact">Let's talk <span aria-hidden="true">↗</span></a></div>
<aside class="cookie-banner" id="cookie-banner" hidden aria-label="Analytics consent preferences">
  <div class="cookie-banner__body">
    <div><strong>Your privacy matters</strong><p>This site only stores your analytics preference locally. Optional analytics is inactive by default and only runs with permission. <a href="./privacy.html">Privacy details</a>.</p></div>
    <div class="cookie-banner__actions"><button type="button" class="button button--secondary" data-consent="rejected">Decline</button><button type="button" class="button button--primary" data-consent="accepted">Accept</button></div>
  </div>
</aside>
<dialog id="site-search" class="app-modal" aria-labelledby="search-title"><div class="modal-body"><div class="modal-heading"><h2 id="search-title">Search MLB</h2><button class="utility-button" data-close-search type="button" aria-label="Close search">✕</button></div><label for="search-input">Find projects, experience, skills and articles</label><input type="search" id="search-input" autocomplete="off" aria-controls="search-results"/><div id="search-results" class="search-results" role="region" aria-live="polite"></div><small>Press Ctrl+K to open · Esc to close</small></div></dialog>
<dialog id="site-feedback" class="app-modal" aria-labelledby="feedback-title"><div class="modal-body"><div class="modal-heading"><h2 id="feedback-title">Site feedback</h2><button class="utility-button" data-close-feedback type="button" aria-label="Close feedback">✕</button></div><p>Help improve the MLB portfolio experience.</p><form id="feedback-form"><fieldset class="stars"><legend>How was your experience?</legend><label><input type="radio" name="rating" value="1" required/><span aria-label="1 stars">★ 1</span></label><label><input type="radio" name="rating" value="2" required/><span aria-label="2 stars">★ 2</span></label><label><input type="radio" name="rating" value="3" required/><span aria-label="3 stars">★ 3</span></label><label><input type="radio" name="rating" value="4" required/><span aria-label="4 stars">★ 4</span></label><label><input type="radio" name="rating" value="5" required/><span aria-label="5 stars">★ 5</span></label></fieldset><label class="field">Category <select name="category" required><option value="UI_UX">UI/UX</option><option value="BUG">Bug</option><option value="FEATURE_IDEA">Feature Idea</option><option value="GENERAL">General</option></select></label><label class="field">Comment (optional)<textarea name="comment" maxlength="500" rows="4"></textarea></label><div class="honeypot" aria-hidden="true"><label for="feedback-website">Leave empty</label><input id="feedback-website" name="website" maxlength="200" tabindex="-1" autocomplete="off"/></div><p role="status" aria-live="polite" id="feedback-status"></p><button class="button button--primary" type="submit">Send feedback</button></form></div></dialog>
<script type="module" src="./js/main.js"></script>

</body>
</html>
```

## `scripts/build_site.py`

```python
#!/usr/bin/env python3
"""Build a canonical, owner-configured static MLB site with verified deployment values."""
import argparse
import base64
import hashlib
import html
import os
import re
import shutil
from pathlib import Path
from urllib.parse import urlsplit

SOURCE = Path(__file__).resolve().parents[1] / 'frontend'
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / 'dist'
SOURCE_SITE_BASE = 'https://mufaddalbatti53.github.io/MLB-Portfolio'
IDENTITY = {
    'SITE_DISPLAY_NAME': 'Mufaddal Battiwala',
    'SITE_EMAIL': 'mufaddal.techpro@gmail.com',
    'SITE_LOCATION': 'Kolkata, India',
    'SITE_GITHUB': 'https://github.com/mufaddalbatti53',
    'SITE_LINKEDIN': 'https://www.linkedin.com/in/mufaddal-tech/',
}
EMAIL_PATTERN = re.compile(r'^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$')


def refresh_jsonld_csp(text):
    match = re.search(r'<script type="application/ld\+json" id="seo-structured-data">(.*?)</script>', text, re.S)
    if not match:
        return text
    digest = base64.b64encode(hashlib.sha256(match.group(1).encode("utf-8")).digest()).decode("ascii")
    token = f"'sha256-{digest}'"
    meta = re.search(r'<meta http-equiv="Content-Security-Policy" content="([^"]+)">', text)
    if not meta:
        raise SystemExit('Structured data page is missing CSP')
    content = re.sub(r"\s+'sha256-[A-Za-z0-9+/=]+'", '', meta.group(1))
    content = content.replace("script-src 'self'", "script-src 'self' " + token, 1)
    return text[:meta.start(1)] + content + text[meta.end(1):]


def config(mode):
    base_url = os.environ.get('SITE_BASE_URL', '').strip().rstrip('/')
    parsed = urlsplit(base_url)
    local = mode == 'development' and parsed.hostname in {'localhost', '127.0.0.1'}
    valid_scheme = parsed.scheme == 'https' or (local and parsed.scheme == 'http')
    valid_path = parsed.path == '' or (parsed.path.startswith('/') and '//' not in parsed.path)
    if (not base_url or not valid_scheme or not parsed.hostname or not valid_path
            or parsed.query or parsed.fragment or parsed.username or parsed.password):
        raise SystemExit('SITE_BASE_URL must be an HTTPS site base URL; localhost HTTP is allowed only in development')
    if mode == 'production' and parsed.hostname == 'github.io':
        raise SystemExit('Use the owner-specific GitHub Pages host, not the github.io platform root')

    identity = {name: os.environ.get(name, value).strip() for name, value in IDENTITY.items()}
    if not EMAIL_PATTERN.fullmatch(identity['SITE_EMAIL']):
        raise SystemExit('Invalid SITE_EMAIL')
    if any(not value or len(value) > 180 or '\n' in value or '\r' in value for value in identity.values()):
        raise SystemExit('Invalid public profile field')
    for name in ('SITE_GITHUB', 'SITE_LINKEDIN'):
        url = urlsplit(identity[name])
        if url.scheme != 'https' or not url.hostname or url.username or url.password:
            raise SystemExit(f'{name} must be an HTTPS URL')
    return base_url, identity


def build(mode, output):
    base_url, identity = config(mode)
    output = output.resolve()
    if output == SOURCE.resolve() or SOURCE.resolve() in output.parents:
        raise SystemExit('Output cannot be within the frontend source')
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(SOURCE, output)

    for filename in ('index.html', '404.html', 'thank-you.html', 'privacy.html', 'terms.html',
                     'admin.html', 'robots.txt', 'sitemap.xml'):
        path = output / filename
        text = path.read_text(encoding='utf-8').replace(SOURCE_SITE_BASE, base_url)
        if filename.endswith('.html'):
            for name, default in IDENTITY.items():
                text = text.replace(default, html.escape(identity[name], quote=True))
            text = refresh_jsonld_csp(text)
        path.write_text(text, encoding='utf-8')
    print(f'Static site generated for {base_url} at {output}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', choices=('production', 'development'), default='production')
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    build(args.mode, args.output)
```

## `scripts/generate_brand_assets.py`

```python
#!/usr/bin/env python3
from pathlib import Path
from io import BytesIO
import cairosvg
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1] / 'frontend' / 'assets'
root.mkdir(exist_ok=True)
paths = '''<path d="M20 72 V24 L42 50 L64 24 V72" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M83 24 V72 H106" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M119 72 V24 H141 C157 24 160 42 144 47 H119 M119 47 H144 C162 47 162 72 140 72 H119" fill="none" stroke="url(#brand)" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>'''
base = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 96" role="img" aria-labelledby="logo-title"><title id="logo-title">MLB monogram</title><defs><linearGradient id="brand" x1="0%" y1="0%" x2="100%" y2="70%"><stop offset="0%" stop-color="#3B66F5"/><stop offset="53%" stop-color="#7A5AF8"/><stop offset="100%" stop-color="#C84B9E"/></linearGradient></defs>'''+paths+'</svg>'
(root / 'logo.svg').write_text(base + '\n', encoding='utf-8')
icon = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 192 192" role="img" aria-labelledby="icon-title"><title id="icon-title">MLB favicon</title><defs><linearGradient id="brand" x1="0%" y1="0%" x2="100%" y2="70%"><stop offset="0%" stop-color="#3B66F5"/><stop offset="53%" stop-color="#7A5AF8"/><stop offset="100%" stop-color="#C84B9E"/></linearGradient></defs><rect x="2" y="2" width="188" height="188" rx="40" fill="#0B0F19"/><g transform="translate(5 48)">'''+paths+'</g></svg>'
(root / 'favicon.svg').write_text(icon + '\n', encoding='utf-8')
for size, name in [(32,'favicon-32x32.png'),(192,'icon-192.png'),(512,'icon-512.png'),(180,'apple-touch-icon.png')]:
    data=cairosvg.svg2png(bytestring=icon.encode(),output_width=size,output_height=size)
    (root / name).write_bytes(data)
Image.open(BytesIO(cairosvg.svg2png(bytestring=icon.encode(),output_width=64,output_height=64))).save(root/'favicon.ico',format='ICO',sizes=[(16,16),(32,32),(48,48),(64,64)])
w,h=1200,630
im=Image.new('RGB',(w,h),(11,15,25));d=ImageDraw.Draw(im)
for x in range(w):
    r=int(59+(200-59)*x/w);g=int(102+(75-102)*x/w);b=int(245+(158-245)*x/w)
    d.line([(x,0),(x,10)],fill=(r,g,b),width=1)
d.rounded_rectangle((80,105,1120,530),radius=40,fill=(19,26,42),outline=(45,56,82),width=2)
logo=Image.open(BytesIO(cairosvg.svg2png(bytestring=icon.encode(),output_width=270,output_height=270))).convert('RGBA')
im.paste(logo,(130,172),logo)
try:
    title=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',75)
    body=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',33)
except OSError:
    title=ImageFont.load_default();body=ImageFont.load_default()
d.text((465,208),'MLB Portfolio',font=title,fill=(248,250,252))
d.text((465,331),'Mufaddal Battiwala',font=body,fill=(148,163,184))
d.text((465,387),'Java  •  Spring Boot  •  Web',font=body,fill=(171,159,255))
im.save(root/'og-preview.png')
print('Generated crisp SVG logo and 6 raster assets')
```

## `scripts/validate_source.py`

```python
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
SOURCE_SITE = "https://mufaddalbatti53.github.io/MLB-Portfolio"
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
        fail(all(script.get("src") or script.get("type") == "application/ld+json" for script in parser.scripts), f"{name}: executable inline script found")
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
    for marker in ['id="project-case-study"', './assets/mufaddal-battiwala-full-stack-developer.jpg', 'fetchpriority="high"',
                   'width="420"', 'height="420"', 'View Projects', 'Get In Touch',
                   'id="owner-tagline"', 'id="experience"', 'id="experience-list"']:
        fail(marker in index, f"index.html: hero/content marker missing: {marker}")
    fail('<title>Mufaddal Battiwala | Full Stack Developer in Kolkata</title>' in index, "index.html: SEO title mismatch")
    fail('id="about"' in index and 'About <span class="gradient-text">Mufaddal Battiwala.' in index, "index.html: visible identity section missing")
    structured_match = re.search(r'<script type="application/ld\+json" id="seo-structured-data">(.*?)</script>', index, re.S)
    fail(bool(structured_match), "index.html: JSON-LD missing")
    structured = json.loads(structured_match.group(1))
    fail(structured.get("@context") == "https://schema.org", "index.html: JSON-LD context invalid")
    structured_text = json.dumps(structured)
    for value in ["ProfilePage", "Person", "Mufaddal Battiwala", "mufaddalbatti53", SOURCE_SITE]:
        fail(value in structured_text, f"index.html: JSON-LD missing {value}")
    fail("sha256-" in index.split('Content-Security-Policy', 1)[1].split('>', 1)[0], "index.html: JSON-LD CSP hash missing")
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
    fail((assets / "mufaddal-battiwala-full-stack-developer.jpg").stat().st_size > 10_000, "SEO portrait: missing/empty")

    robots = read(FRONTEND / "robots.txt")
    fail("User-agent: *" in robots and "Allow: /" in robots, "robots.txt: crawl policy missing")
    fail(f"Sitemap: {SOURCE_SITE}/sitemap.xml" in robots, "robots.txt: sitemap URL mismatch")
    sitemap = read(FRONTEND / "sitemap.xml")
    fail(f"<loc>{SOURCE_SITE}/</loc>" in sitemap, "sitemap.xml: canonical homepage missing")
    fail("privacy.html" not in sitemap and "terms.html" not in sitemap, "sitemap.xml: noindex legal URLs included")

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
```

## `tests/test_build_site.py`

```python
import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts' / 'build_site.py'
spec = importlib.util.spec_from_file_location('build_site', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SiteBuildTests(unittest.TestCase):
    def test_requires_valid_base_url(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(SystemExit):
                module.config('production')

    def test_rejects_http_for_production(self):
        with patch.dict(os.environ, {'SITE_BASE_URL': 'http://portfolio.example.net'}, clear=True):
            with self.assertRaises(SystemExit):
                module.config('production')

    def test_rejects_unowned_github_pages_root(self):
        with patch.dict(os.environ, {'SITE_BASE_URL': 'https://github.io'}, clear=True):
            with self.assertRaises(SystemExit):
                module.config('production')

    def test_accepts_github_pages_project_subpath(self):
        with patch.dict(os.environ, {
            'SITE_BASE_URL': 'https://mufaddalbatti53.github.io/MLB-Portfolio'
        }, clear=True):
            base_url, _ = module.config('production')
            self.assertEqual('https://mufaddalbatti53.github.io/MLB-Portfolio', base_url)

    def test_build_output_and_admin_noindex(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {
                'SITE_BASE_URL': 'https://portfolio.example.net/work'
            }, clear=True):
                module.build('production', Path(directory) / 'release')
            build = Path(directory) / 'release'
            index = (build / 'index.html').read_text()
            self.assertIn('https://portfolio.example.net/work/assets/og-preview.png', index)
            self.assertIn('Mufaddal Battiwala', index)
            self.assertIn('ProfilePage', index)
            self.assertIn('https://portfolio.example.net/work/#mufaddal-battiwala', index)
            self.assertRegex(index, r"script-src 'self' 'sha256-[A-Za-z0-9+/=]+'")
            self.assertIn('href="./assets/favicon.svg"', index)
            self.assertIn('noindex,nofollow', (build / 'admin.html').read_text())
            self.assertTrue((build / 'assets/logo.svg').exists())

    def test_html_escapes_optional_overrides(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {
                'SITE_BASE_URL': 'https://portfolio.example.net',
                'SITE_DISPLAY_NAME': '<script>alert(1)</script>'
            }, clear=True):
                module.build('production', Path(directory) / 'release')
            page = (Path(directory) / 'release' / 'index.html').read_text()
            self.assertNotIn('<script>alert(1)</script>', page)
            self.assertIn('&lt;script&gt;', page)


if __name__ == '__main__':
    unittest.main()
```

