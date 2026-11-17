# MLB Portfolio Validation

## Executed checks

| Check | Result |
|---|---|
| Repository synchronization validator | PASS |
| JavaScript syntax via `node --check` | PASS |
| Python build tests | PASS — 6/6 |
| Python script compilation | PASS |
| GitHub Actions YAML parsing | PASS |
| Compose YAML parsing | PASS |
| Spring YAML parsing | PASS |
| Maven POM XML parsing | PASS |
| Sitemap and SVG XML parsing | PASS |
| Web manifest JSON parsing | PASS |
| Nginx syntax in HTTP context | PASS |
| GitHub Pages production build | PASS |
| Root-leading local URL scan | PASS |
| Safe DOM API scan | PASS |
| Selector-to-HTML audit | PASS |
| Dynamic class-to-CSS audit | PASS |
| Comment-length policy | PASS |
| Experience API/DTO/UI contract | PASS |

## Backend runtime limitation

Java 21 is available in the audit environment, but Maven and Docker are not installed. The Java/JUnit/MongoDB runtime suite therefore remains a required GitHub Actions gate; `.github/workflows/ci.yml` runs `./mvnw -B test` with a MongoDB 7 service before deployment work can proceed.

## Nginx validation

`deploy/nginx.conf` is an HTTP-context include. It passed `nginx -t` inside a temporary HTTP wrapper with the private `backend` hostname substituted by loopback solely for local name resolution; the deployed Compose network supplies the real `backend` service name.
