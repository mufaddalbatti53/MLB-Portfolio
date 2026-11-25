# Engineering Workspace release

Version: 20261125-engineer-v7

## Changes

- Shared editor-style design across public pages, admin screens, dialogs, project cards, experience, skills, education, articles, contact and footer.
- Original portrait retained in a stepped polygon frame. No new portrait asset or external font dependency.
- Three distinct backgrounds: Light #F5F7FA, Dusk #AEB7C5, Dark #0D1117. One header switch remains.
- Finite editor caret animation, small hover transitions and one-time section-heading motion; reduced-motion support.
- Real project data and public profile content retained. No invented professional credentials or portfolio content added to the shipped website.
- Frontend asset references versioned; backend and deployment files unchanged.

## Verification

- Source audit passed; JavaScript syntax and theme cycle regression passed; Python build tests 6/6 passed.
- GitHub Pages preparation step completed with a test API origin and the project site URL.
- Isolated Chromium: 504/504 recorded checks across 66 page/theme/viewport combinations. Seven widths from 280 to 1440; six pages checked at 280, 390 and 1440 pixels. Measured rendered background, viewport and scroll width, switch bounds and label, duplicate controls, portrait bounds and polygon clipping. Raw results in ENGINEERING_WORKSPACE_BROWSER_RESULTS.json.
- Visual inspection of desktop and mobile screenshot renders.

## Scope limits

Network and local file navigation were blocked. The browser test loaded generated page markup, inlined local assets, removed CSP for isolated execution and used simulated local storage. Dynamic cards were populated with representative test fixtures, not the live database. Live asset fetching, production CSP enforcement, physical devices, native Safari, actual storage persistence and backend runtime were not verified. No GitHub repository writes were made.

## Deploy

Extract the archive and copy the contents of mlb-portfolio into your repository, preserving .git and local environment settings. Review the diff, commit, push main, and confirm the Pages workflow succeeds. The build uses the existing MLB_API_ORIGIN repository variable. Confirm the live HTML contains 20261125-engineer-v7 before assessing the new visuals.
