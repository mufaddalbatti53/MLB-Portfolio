# MLB Portfolio Synchronization Log

This audit was run against the final pre-production repository and then repeated after the Work Experience feature was added.

## Fixed contradictions

| Area | Finding | Resolution |
|---|---|---|
| Hero profile selector | `main.js` queried `#owner-tagline`, but the Hero subtitle had no matching ID. | Added `id="owner-tagline"` to the Hero subtitle and kept the public profile response field `tagline`. |
| Contact honeypot | `ContactRequest.website` allowed at most 200 characters while the HTML honeypot had no matching browser limit. | Added `maxlength="200"` to the contact honeypot. |
| Feedback honeypot | `FeedbackRequest.website` allowed at most 200 characters while public feedback forms had no matching browser limit. | Added `maxlength="200"` on every public feedback form. |
| Admin field bounds | Several existing admin controls did not mirror DTO length/list limits for project, blog, and profile updates. | Added matching input limits and list validation in `admin.js`. |
| Compose settings | `application.yml` supported `SPRING_PROFILES_ACTIVE`, `SEED_INITIAL_DATA`, and `MAIL_POLL_INTERVAL_MS`, but the main Compose service did not forward all three. | Added the three settings to `compose.yaml` and `.env.example`. |
| Comment policy | Generated files contained long tutorial-style engineering comments. | Removed redundant comments repository-wide and retained only short security comments of five words or fewer. |
| CI comment block | `ci.yml` retained a large commented deployment example. | Replaced it with an executable deployment gate message. |
| Brand generator | The asset generator recreated long HTML comments inside generated SVG files. | Removed comment generation so regenerated assets remain clean. |

## Experience synchronization

The following feature was added as one synchronized contract rather than separate frontend/backend implementations:

- MongoDB collection: `experiences`
- Public API: `GET /api/v1/experiences`
- Admin API: `GET /api/v1/admin/experiences`
- Admin API: `GET /api/v1/admin/experiences/{id}`
- Admin API: `POST /api/v1/admin/experiences`
- Admin API: `PUT /api/v1/admin/experiences/{id}`
- Admin API: `DELETE /api/v1/admin/experiences/{id}`
- Public navigation target: `./index.html#experience`
- Public section ID: `experience`
- Public render target: `experience-list`
- Admin tab key: `experiences`
- Search type: `Experience`

The admin publish toggle intentionally sends a complete `PUT` payload because the Experience controller exposes `PUT`, not `PATCH`.

## Seed record

The seeded experience is exactly the supplied Web Graphics Studio internship: Software Developer Intern, Internship · Part-Time, Remote (Work From Home), January 1 through June 30 2026, Remote · India, with the two supplied contributions and five supplied technologies.

## Final result

After the fixes above, the deterministic source audit reports zero remaining synchronization mismatches across the checked API routes, DTO fields, selectors, local paths, environment settings, comments, assets, and deployment configuration.
