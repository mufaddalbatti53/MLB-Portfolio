# Theme visibility review — release 20261124-theme-visible-v4

## Findings
- GitHub main and the prior archive contained all three theme definitions. The controller, variables, main CSS, six page files, main/admin modules, and Pages workflow were checked against the GitHub source. The previous desktop design exposed only a cycle button; direct options were inside the mobile-only navigation, and the narrow header hid the current name.
- All six pages now expose a permanent, labelled Light / Dusk / Dark selector, with a separate repeating cycle control. Tablet navigation switches to a compact menu at 1024px, avoiding the overflow found at 820px. Corrected two undefined CSS tokens in the SEO profile section. The theme controller relies on CSS for native color scheme. All theme asset references have a new cache version.
- Dusk retains the medium slate background #AEB7C5; Light #F4F7FB; Dark #0A0E17.

## Executed checks
- Chromium layout and touch/click tests: 613/613 assertions across 320, 375, 390, 412, 768, 820, 1024 and 1440px. All six HTML pages tested at 320 and 1440px; homepage tested at all widths. Three visible choices, touch target size, cycle order, selected state, computed body background colors, menu interaction, storage write and horizontal overflow checked. Raw results supplied separately.
- JavaScript regression test passed; 6 Python build tests passed; source audit passed; all frontend JS syntax checked; zero undefined CSS tokens; GitHub Pages production transform executed with a test API origin.
- Direct network and file URL navigation were blocked in this environment. Browser tests used Chromium with page content supplied from the generated Pages build, inlined resources, CSP removed for this isolated test and a storage shim. Actual deployed CSP/resource fetching, native real-origin reload persistence and physical iOS/Android devices were not tested. The live site could not be reached by the available browser or web tools. No GitHub repository writes were made.

## Deployment
Extract the update in the existing repository root. Review git diff before committing. The patch includes frontend and tests/test_theme_cycle.cjs only plus this report. Keep existing backend, environment variables and GitHub Actions configuration. Confirm the Pages deployment completes successfully and inspect the live homepage for all three named controls.
