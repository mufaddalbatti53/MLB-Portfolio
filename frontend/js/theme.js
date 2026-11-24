const THEME_IDS = ['light', 'dusk', 'dark'];

export function initTheme() {
  if (!THEME_IDS.includes(document.documentElement.dataset.theme)) {
    document.documentElement.dataset.theme = 'dark';
  }
  window.MLBTheme?.sync();
}
