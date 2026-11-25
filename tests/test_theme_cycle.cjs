const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.join(__dirname, '..', 'frontend');
const source = fs.readFileSync(path.join(root, 'js/theme-init.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'css/variables.css'), 'utf8');
const storage = new Map();
function createController(prefersLight) {
  const callbacks = {};
  const element = { dataset: {}, style: {} };
  const meta = {};
  function button(data) {
    return { dataset: data, attributes: {}, icon: {}, caption: {}, classList: { toggle() {} },
      setAttribute(key, value) { this.attributes[key] = value; },
      querySelector(selector) { return selector === '[data-theme-icon]' ? this.icon : this.caption; },
      closest(selector) { return (selector === '[data-theme-toggle]' && 'themeCurrent' in this.dataset) || (selector === '[data-theme-choice]' && 'themeChoice' in this.dataset) ? this : null; } };
  }
  const toggle = button({ themeCurrent: 'dark' });
  const choices = ['light', 'dusk', 'dark'].map(themeChoice => button({ themeChoice }));
  const document = { documentElement: element, querySelector() { return meta; },
    querySelectorAll(selector) { return selector === '[data-theme-toggle]' ? [toggle] : choices; },
    addEventListener(event, callback) { callbacks[event] = callback; } };
  vm.runInNewContext(source, { document, window: {}, matchMedia: () => ({ matches: prefersLight }),
    localStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value) } });
  callbacks.DOMContentLoaded();
  return { element, meta, toggle, choices, click: target => callbacks.click({ target }) };
}
const colors = { light: '#F4F7FB', dusk: '#AEB7C5', dark: '#0A0E17' };
let controller = createController(true);
for (const mode of ['light', 'dusk', 'dark', 'light', 'dusk', 'dark', 'light']) {
  assert.equal(controller.element.dataset.theme, mode);
  assert.equal(controller.meta.content, colors[mode]);
  assert.equal(controller.toggle.dataset.themeCurrent, mode);
  controller.click(controller.toggle);
}
assert.equal(storage.get('mlb-theme'), 'dusk');
controller = createController(false);
assert.equal(controller.element.dataset.theme, 'dusk');
for (const mode of ['dusk','dark','light','dusk']) {
  assert.equal(controller.element.dataset.theme, mode);
  assert.equal(controller.toggle.caption.textContent, mode[0].toUpperCase()+mode.slice(1));
  controller.click(controller.toggle);
}
assert.match(css, /\[data-theme="dusk"\]\s*\{[^}]*--surface-base:\s*#AEB7C5/i);
for (const page of fs.readdirSync(root).filter(name => name.endsWith('.html'))) {
  const html = fs.readFileSync(path.join(root, page), 'utf8');
  assert.match(html, /theme-init\.js\?v=20261125-developer-v6/);
  assert.match(html, /main\.css\?v=20261125-developer-v6/);
  assert.match(html, /data-theme-toggle/);
  assert.equal((html.match(/data-theme-toggle/g) || []).length, 1, `${page}: expected one switch`);
  assert.ok(!html.includes("data-theme-choice") && !html.includes("theme-bar"), `${page}: duplicate controls remain`);
}

console.log('PASS: repeated theme cycle, Dusk persistence, single-switch markup, palette and versioned references on every page.');
