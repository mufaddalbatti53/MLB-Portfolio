# Mobile browser execution — 2026-11-24

## Defect confirmed
On the iPhone SE 320 × 568 CSS viewport, the Dusk button's center was at approximately (160,457). `document.elementFromPoint()` returned the cookie banner's Decline button, not the theme button. The banner's z-index was 80 and the header's was 40. The original release also failed menu touches at iPhone 13 dimensions.

## Changes
- Mobile theme panel placed first visually in the open menu.
- Open mobile menu/header stacking level raised to 90, above the consent banner.
- Narrow-screen header controls kept at 44 × 44 CSS pixels.
- Updated main.css cache version in all six pages and regression assertion.
- SEO, theme controller, balanced Dusk palette and backend files unchanged.

## Browser test
Playwright drove Chromium 144 with touch-enabled mobile device descriptors for Pixel 7, iPhone 13, iPhone SE. All 25 checks per device passed (75/75), including repeated real touch taps, menu opening and selection, 44px header targets, selected ARIA state, computed backgrounds, browser theme-color metadata, no horizontal overflow, and admin header switching.

The environment refused all page navigations with ERR_BLOCKED_BY_ADMINISTRATOR, including localhost. For this test the extracted HTML, CSS and JS modules were loaded into an actual Chromium document with set_content; local assets were embedded and CSP removed only in the test document. A storage shim was used to carry the selected theme into a fresh admin document, so real-origin localStorage/reload, deployed CSP/network/cache, physical phones, and Safari/WebKit were not verified. The test does not exercise authenticated admin or live API requests. The shipping files retain their original CSP and deployment configuration.

Static source audit, six Python build tests, Node theme regression and production static build passed.
