(() => {
  const MODES = [
    { id: 'light', label: 'Light', icon: '☀', scheme: 'light', color: '#F5F7FA' },
    { id: 'dusk', label: 'Dusk', icon: '◐', scheme: 'light', color: '#AEB7C5' },
    { id: 'dark', label: 'Dark', icon: '☾', scheme: 'dark', color: '#0D1117' }
  ];

  const modeFor = (value) => MODES.find((mode) => mode.id === value) || MODES[2];
  const nextMode = (value) => {
    const index = MODES.findIndex((mode) => mode.id === value);
    return MODES[(index + 1 + MODES.length) % MODES.length];
  };

  const sync = () => {
    const current = modeFor(document.documentElement.dataset.theme);
    const next = nextMode(current.id);
    const themeColor = document.querySelector('meta[name="theme-color"]');
    if (themeColor) themeColor.content = current.color;

    document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
      button.dataset.themeCurrent = current.id;
      button.setAttribute('aria-label', `Theme: ${current.label}. Switch to ${next.label}.`);
      button.title = `Theme: ${current.label} · Next: ${next.label}`;
      const icon = button.querySelector('[data-theme-icon]');
      const caption = button.querySelector('[data-theme-caption]');
      if (icon) icon.textContent = current.icon;
      if (caption) caption.textContent = current.label;
    });

  };

  const apply = (value, persist = false) => {
    const current = modeFor(value);
    document.documentElement.dataset.theme = current.id;
    if (persist) {
      try { localStorage.setItem('mlb-theme', current.id); } catch { }
    }
    sync();
  };

  let preference = null;
  try { preference = localStorage.getItem('mlb-theme'); } catch { }
  const known = MODES.some((mode) => mode.id === preference);
  const preferred = known
    ? preference
    : (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
  apply(preferred);

  document.addEventListener('click', (event) => {
    const toggle = event.target.closest?.('[data-theme-toggle]');
    if (toggle) {
      apply(nextMode(document.documentElement.dataset.theme).id, true);
      return;
    }

  });

  document.addEventListener('DOMContentLoaded', sync, { once: true });
  window.MLBTheme = { apply, sync };
})();
