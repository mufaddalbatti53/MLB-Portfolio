(() => {
  const modes = new Set(['light', 'dusk', 'dark']);
  let preference = null;
  try { preference = localStorage.getItem('mlb-theme'); } catch { }
  const preferred = modes.has(preference)
    ? preference
    : (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
  document.documentElement.dataset.theme = preferred;
  document.documentElement.style.colorScheme = preferred === 'light' ? 'light' : 'dark';
  const themeColor = document.querySelector('meta[name="theme-color"]');
  if (themeColor) themeColor.content = preferred === 'light' ? '#F4F7FB' : (preferred === 'dusk' ? '#171824' : '#0A0E17');
})();
