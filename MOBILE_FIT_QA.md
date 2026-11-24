# Mobile fit and single-switch release — v5

- All six pages retain only one header theme switch. The switch cycles Light, Dusk, Dark repeatedly. A compact icon and current-mode caption remain visible on mobile; no secondary theme row is present.
- Fluid portrait width, shrinkable grid tracks, wrapped headings and status pills, compact header and tablet navigation, and mobile card and form constraints address narrow screens. No global horizontal clipping was added to hide overflow.
- Chromium emulation: 1017 assertions passed, 0 failed. Homepage tested at 280, 320, 360, 375, 390, 412, 430, 768, 820, 1024, 1200, 1201 and 1440px; all six pages tested at 280, 390 and 1440px. Verified actual client width, visual viewport, scroll width and key element bounds, mode captions, computed backgrounds, touch/click cycling, storage writes and menu interaction.
- Node theme regression and six Python build tests passed. See MOBILE_FIT_BROWSER_RESULTS.json for browser results.
- Browser checks used isolated Chromium markup with inlined local assets and simulated storage; CSP and direct live URL loading were not exercised. Physical phones and live GitHub Pages remain unverified. Backend source, SEO data and existing deployment workflow unchanged; backend tests were not rerun.
- Release asset version: 20261124-mobile-fit-v5. The old data-theme-choice diagnostic is no longer applicable because the duplicate controls were intentionally removed. Confirm one data-theme-toggle on the live page and cycle through all modes.
