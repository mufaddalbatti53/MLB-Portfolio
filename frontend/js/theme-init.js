(() => {
  let preference = null;
  try { preference = localStorage.getItem('mlb-theme'); } catch { }
  const preferred = preference === 'dark' || preference === 'light' ? preference
    : (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
  document.documentElement.dataset.theme = preferred;
  document.documentElement.style.colorScheme = preferred;
})();
