# Developer theme release

Based on MLB-Portfolio-Mobile-Fit-Final. The single header theme switch, three background palettes, mobile sizing, SEO metadata, backend source and deployment workflow are retained. The homepage now includes a JShell profile example, a theme-aware editor panel, technical grid, syntax tokens and finite decorative animation. Reduced-motion users get a static editor and grid. No external font or animation libraries were introduced. Asset version: 20261125-developer-v6.

## Executed checks
- Node theme regression: passed, including one switch per page and persistence in the test harness.
- Python static site build tests: 6/6 passed.
- JavaScript syntax: passed for all frontend JavaScript files.
- Static production build: passed.
- Isolated Chromium: 201/201 assertions passed at 280, 320, 390, 768, 820, 1024 and 1440px. Homepage tested at every width, admin page at 320 and 1440px. Verified cycle, background colors, viewport fit, editor fit and reduced motion. See DEVELOPER_THEME_BROWSER_RESULTS.json.

## Limits
Chromium direct navigation was blocked. Browser checks used built HTML with inline styles and theme controller, data-encoded images, a storage shim and CSP removed for isolation. The full module graph, real-origin persistence, live GitHub Pages deployment, physical phones and Java backend runtime were not executed. The backend and deployment configuration were not changed. The code panel is a static illustrative JShell snippet, not an interactive IDE.
