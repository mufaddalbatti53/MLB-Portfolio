# MLB Portfolio — What I Need To Do Before Going Live

This is the owner execution checklist for Mufaddal Battiwala. Complete the steps in order; do not skip a verification step just because a workflow is green.

## 1. Create or confirm the GitHub repository

1. Sign in to the GitHub account `mufaddalbatti53`.
2. Create or confirm a repository named `MLB-Portfolio`.
3. Keep the default branch named `main` because both workflows target `main`.
4. Upload/push the complete audited repository, including `.github/workflows`, `frontend`, `backend`, `scripts`, and the root documentation.
5. Confirm the Actions tab shows both **MLB Portfolio CI** and **Deploy frontend to GitHub Pages**.

If starting from an empty local Git repository:

```bash
git init
git branch -M main
git add .
git commit -m "Release MLB portfolio"
git remote add origin https://github.com/mufaddalbatti53/MLB-Portfolio.git
git push -u origin main
```

## 2. Generate the backend secrets locally

Run:

```bash
openssl rand -hex 48
openssl rand -hex 32
```

Store the first value privately as `JWT_SECRET` and the second as `RATE_LIMIT_HMAC_SECRET`. Never paste either value into source files, screenshots, issues, commits, or public chat messages.

Generate the admin BCrypt password hash:

```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 admin
```

Enter the admin password only when prompted. The command prints `admin:<hash>`; copy only the hash after the first colon and store it privately as `ADMIN_PASSWORD_HASH`.

Choose a private `ADMIN_USERNAME` that matches 3–64 characters from letters, numbers, `_`, `.`, and `-`. Do not use the public email password or reuse the MongoDB password.

## 3. Create MongoDB Atlas storage

1. Sign in to MongoDB Atlas.
2. Create a project for the portfolio.
3. Create a MongoDB cluster suitable for the chosen Atlas plan.
4. Under **Database Access**, create a dedicated database user with a unique strong password.
5. Under **Network Access**, add the outbound IP ranges used by the Render region where the backend will run. Use the narrowest practical rules instead of opening database access to the whole Internet.
6. Open **Connect → Drivers** and select the Java driver instructions.
7. Copy the `mongodb+srv://` connection URI.
8. Insert the dedicated database username and URL-encoded password.
9. Set the database name to `mlb_portfolio` in the URI.
10. Save the complete URI privately as `MONGODB_URI`.

Do not put `MONGODB_URI` in GitHub Pages variables because the browser must never receive database credentials.

## 4. Create the Render backend

1. Sign in to Render and create a **Web Service** from the GitHub repository.
2. Choose the Docker deployment option.
3. Set the Dockerfile to `backend/Dockerfile`.
4. If Render asks for a build context, use `backend`.
5. Add these required environment variables exactly:

```text
MONGODB_URI=<Atlas connection URI>
JWT_SECRET=<48-byte random secret output>
ADMIN_USERNAME=<private admin username>
ADMIN_PASSWORD_HASH=<BCrypt hash only>
RATE_LIMIT_HMAC_SECRET=<32-byte random HMAC secret output>
CORS_ALLOWED_ORIGINS=https://mufaddalbatti53.github.io
```

6. Add these operational values:

```text
SPRING_PROFILES_ACTIVE=production
SEED_INITIAL_DATA=true
MAIL_ENABLED=false
MANAGEMENT_HEALTH_MAIL_ENABLED=false
```

7. Do not manually set `PORT` unless Render specifically requires it; the service reads Render's assigned `PORT` and falls back to `8080` outside Render.
8. Deploy the service.
9. Wait for the service to become healthy.
10. Copy the exact public Render origin. It will be in the form `https://<service-name>.onrender.com`.
11. Open:

```text
https://<service-name>.onrender.com/actuator/health
```

12. Confirm the response is exactly/minimally equivalent to:

```json
{"status":"UP"}
```

13. Confirm these management paths are not publicly usable:

```text
/actuator/env
/actuator/beans
/actuator/metrics
```

## 5. Connect GitHub Pages to the exact Render backend

1. In GitHub, open **MLB-Portfolio → Settings → Secrets and variables → Actions → Variables**.
2. Create a repository variable named `MLB_API_ORIGIN`.
3. Set it to the exact Render origin copied in Step 4, with no extra path. Example shape: `https://<service-name>.onrender.com`.
4. Open **Settings → Pages**.
5. Set **Source** to **GitHub Actions**.
6. Open the **Actions** tab and run **Deploy frontend to GitHub Pages**, or push a frontend change to `main`.
7. Wait for the deployment job to complete.
8. Open:

```text
https://mufaddalbatti53.github.io/MLB-Portfolio/
```

The deployment workflow injects the Render API origin into the built HTML and keeps the project-site subpath intact.

## 6. Verify the public website on desktop and mobile

On the actual GitHub Pages URL, verify all of the following:

- The MLB SVG logo is sharp in the header and footer.
- The uploaded portrait appears in the hero with no layout jump.
- The dark theme loads correctly on first visit.
- The Light/Dusk/Dark theme control works and survives a page reload through `mlb-theme`.
- `Ctrl+K` or `Cmd+K` opens search.
- Search finds projects, work experience, skills, and blog entries and the result links work inside `/MLB-Portfolio/`.
- Escape closes the native search dialog.
- The Feedback dialog opens, accepts a 1–5 rating/category/comment, and submits successfully.
- Each project card opens **Read Case Study** and the URL hash changes to `#project-<slug>`.
- Refreshing a project hash reopens the matching case study after the project API data loads.
- Privacy, Terms, Thank You, 404, favicon, manifest, logo, and portrait all load without a 404.
- Browser DevTools shows no CSP errors, mixed-content errors, or failed root-level `/assets`, `/css`, or `/js` requests.

## 7. Verify contact and feedback persistence

1. Submit the contact form using a real test email.
2. Confirm the browser reaches `thank-you.html` under `/MLB-Portfolio/`.
3. Submit one feedback entry.
4. Open Atlas Collections and confirm the expected records appear in `contact_messages` and `site_feedback`.
5. Confirm the anonymous responses do not reveal private MongoDB IDs or internal notification details.
6. Send repeated requests only enough to verify throttling in a controlled test; do not disable the production rate limits.

## 8. Verify admin authentication and CRUD

1. Open:

```text
https://mufaddalbatti53.github.io/MLB-Portfolio/admin.html
```

2. Sign in with `ADMIN_USERNAME` and the plaintext password whose BCrypt hash was stored on Render.
3. In browser DevTools → Network, confirm authenticated admin requests contain an `Authorization: Bearer ...` request header.
4. In DevTools → Application → Session Storage, confirm the admin token is under the current Pages origin and disappears when the tab/session storage is cleared.
5. Verify the dashboard statistics load.
6. Edit a project title or case-study field and save it.
7. Toggle a project published/featured state, then restore the desired state.
8. Edit the Web Graphics Studio experience, toggle its published state, restore it, and confirm the public timeline updates.
9. Create/edit/delete a test blog entry.
10. Mark a contact message READ and ARCHIVED.
11. Verify the Feedback tab can display and delete the test feedback entry.
12. Edit the public profile only if a real detail needs changing.
13. Click **Sign out** and confirm subsequent protected requests receive an authorization failure until signing in again.

## 9. Verify the seeded personal data

Confirm the production UI/API shows:

```text
Mufaddal Battiwala
Full Stack Developer | Java + Spring Boot & Modern Web Developer
mufaddal.techpro@gmail.com
Kolkata, India
GitHub: https://github.com/mufaddalbatti53
LinkedIn: https://www.linkedin.com/in/mufaddal-tech/
B.Tech (CSE) — Netaji Subhash Engineering College, Kolkata
Complisca Prototype
WGS Order Processing
Software Developer Intern — Web Graphics Studio — Jan 2026 to Jun 2026
```

If the Atlas database was used with an earlier release, restart once with `SEED_INITIAL_DATA=true`. The versioned bootstrap migration updates only known legacy generic owner URLs while preserving unrelated admin-managed profile data.

## 10. Configure UptimeRobot or another uptime monitor

Create an HTTP monitor for:

```text
https://<service-name>.onrender.com/actuator/health
```

Use `GET` and treat HTTP 200 with `{"status":"UP"}` as healthy. Do not monitor `/actuator/env`, `/actuator/metrics`, or another management endpoint.

## 11. Optional email notification configuration

The contact message itself is stored in MongoDB even when mail notifications are disabled. If you later enable email, create dedicated SMTP credentials and configure only Render environment variables:

```text
MAIL_ENABLED=true
SMTP_HOST=<provider host>
SMTP_PORT=<provider port>
SMTP_USERNAME=<provider username>
SMTP_PASSWORD=<provider secret>
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM=<verified sender>
CONTACT_NOTIFY_EMAIL=mufaddal.techpro@gmail.com
```

After enabling mail, submit one contact message and confirm notification status reaches `SENT` in the admin inbox. If the selected hosting plan or mail provider blocks the required network route, keep `MAIL_ENABLED=false` until you choose a supported delivery method.

## 12. Run the release checks locally when possible

From the repository root:

```bash
python3 scripts/validate_source.py
python3 -m unittest tests/test_build_site.py -v
```

With Java 21, Maven 3.9+, and MongoDB available:

```bash
cd backend
./mvnw -B test
```

Push only after the tests are green. GitHub Actions will repeat the backend and frontend release gates on `main` and on pull requests to `main`.

## 13. Final security checks before sharing the URL

- Confirm Git history contains no Atlas URI, JWT secret, HMAC secret, admin plaintext password, SMTP password, or private API key.
- Confirm `CORS_ALLOWED_ORIGINS` is exactly `https://mufaddalbatti53.github.io` for this Pages deployment.
- Confirm `MLB_API_ORIGIN` is the exact Render service origin, not a platform root and not a path URL.
- Confirm `admin.html` includes `noindex,nofollow,noarchive`.
- Confirm all admin API calls fail without a valid Bearer token.
- Confirm `/actuator/health` works anonymously and other `/actuator/**` paths are inaccessible.
- Confirm the browser console has no CSP, CORS, module-import, favicon, portrait, or mixed-content errors.
- Rotate any secret immediately if it was ever copied into a public location.

## 14. Launch

After every item above is verified, share:

```text
https://mufaddalbatti53.github.io/MLB-Portfolio/
```

Keep the GitHub Actions CI workflow enabled, keep Atlas/Render security alerts enabled where available, and periodically review the admin contact/feedback inbox and dependency updates.

> **Engineering note:** This checklist separates actions that require the repository owner or hosting dashboards from checks automated by CI, so deployment never depends on hidden assumptions. Secrets stay server-side, the browser receives only the API origin, and every launch-critical cross-origin path is explicitly verified on the real production URLs.
