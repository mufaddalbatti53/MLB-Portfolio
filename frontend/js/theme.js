const apply = (mode) => {
  document.documentElement.dataset.theme = mode;
  document.documentElement.style.colorScheme = mode;
  for (const button of document.querySelectorAll('[data-theme-toggle]')) {
    button.setAttribute('aria-label', `Switch to ${mode === 'dark' ? 'light' : 'dark'} theme`);
    button.setAttribute('aria-pressed', String(mode === 'light'));
    const caption = button.querySelector('[data-theme-caption]');
    if (caption) caption.textContent = mode === 'dark' ? 'Light mode' : 'Dark mode';
  }
};
export function initTheme() {
  apply(document.documentElement.dataset.theme === 'light' ? 'light' : 'dark');
  document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
    button.addEventListener('click', () => {
      const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      apply(next);
      try { localStorage.setItem('mlb-theme', next); } catch { }
    });
  });
}
