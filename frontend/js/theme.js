const MODES = [
  { id: 'light', label: 'Light', icon: '☀', scheme: 'light' },
  { id: 'dusk', label: 'Dusk', icon: '◐', scheme: 'dark' },
  { id: 'dark', label: 'Dark', icon: '☾', scheme: 'dark' }
];

const modeFor = (value) => MODES.find((mode) => mode.id === value) || MODES[2];

const nextMode = (value) => {
  const index = MODES.findIndex((mode) => mode.id === value);
  return MODES[(index + 1 + MODES.length) % MODES.length];
};

const apply = (value) => {
  const current = modeFor(value);
  const next = nextMode(current.id);
  document.documentElement.dataset.theme = current.id;
  document.documentElement.style.colorScheme = current.scheme;
  const themeColor = document.querySelector('meta[name="theme-color"]');
  if (themeColor) themeColor.content = current.id === 'light' ? '#F4F7FB' : (current.id === 'dusk' ? '#171824' : '#0A0E17');

  for (const button of document.querySelectorAll('[data-theme-toggle]')) {
    button.dataset.themeCurrent = current.id;
    button.setAttribute('aria-label', `Theme: ${current.label}. Switch to ${next.label}.`);
    button.title = `Theme: ${current.label} · Next: ${next.label}`;
    const icon = button.querySelector('[data-theme-icon]');
    const caption = button.querySelector('[data-theme-caption]');
    if (icon) icon.textContent = current.icon;
    if (caption) caption.textContent = current.label;
  }
};

export function initTheme() {
  apply(document.documentElement.dataset.theme);
  document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
    button.addEventListener('click', () => {
      const next = nextMode(document.documentElement.dataset.theme);
      apply(next.id);
      try { localStorage.setItem('mlb-theme', next.id); } catch { }
    });
  });
}
