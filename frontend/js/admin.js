import { initTheme } from './theme.js?v=20261124-theme-visible-v4';
import {
  createAdminItem,
  createExperience,
  currentUser,
  deleteAdminItem,
  deleteExperience,
  getAdminExperiences,
  getAdminList,
  getDashboard,
  getProfile,
  login,
  logout,
  patchAdminItem,
  putAdminItem,
  retryNotification,
  saveProfile,
  updateExperience
} from './admin-api.js';

const byId = (id) => document.getElementById(id);

function element(name, className = '', text = '') {
  const node = document.createElement(name);
  if (className) node.className = className;
  node.textContent = String(text);
  return node;
}

function status(message) {
  byId('admin-status').textContent = message;
}

function loginStatus(message) {
  byId('login-status').textContent = message;
}

const tabs = ['dashboard', 'projects', 'experiences', 'blog', 'messages', 'feedback', 'profile'];
const titles = {
  dashboard: 'Dashboard Overview',
  projects: 'Project Manager',
  experiences: 'Work Experience',
  blog: 'Blog Posts Manager',
  messages: 'Contact Inbox',
  feedback: 'Site Feedback',
  profile: 'Public Profile'
};
const resourceNames = {
  projects: 'project',
  experiences: 'experience',
  blog: 'article',
  messages: 'message',
  feedback: 'feedback entry'
};

let activeTab = 'dashboard';
let currentPage = 0;
let cache = [];
let editorSubmitController = null;

function setAuthenticated(value) {
  byId('login-panel').hidden = value;
  byId('dashboard-panel').hidden = !value;
  if (!value) {
    byId('admin-editor-panel').hidden = true;
    byId('admin-collection').replaceChildren();
  }
}

function button(label, action, variant = '') {
  const control = element('button', variant, label);
  control.type = 'button';
  control.addEventListener('click', action);
  return control;
}

function pillCard(title, description, detail = '') {
  const card = element('article', 'admin-card');
  card.append(element('h3', '', title), element('p', '', description));
  if (detail) card.append(element('small', '', detail));
  return card;
}

function toolbar(actionTitle, handler) {
  const bar = element('div', 'admin-toolbar');
  bar.append(element('span', '', `Page ${currentPage + 1}`));
  if (handler) bar.append(button(actionTitle, handler, 'button button--primary'));
  byId('admin-collection').append(bar);
}

function experiencePayload(item, overrides = {}) {
  return {
    role: item.role,
    company: item.company,
    employmentType: item.employmentType,
    workMode: item.workMode,
    location: item.location,
    startDate: item.startDate,
    endDate: item.current ? null : item.endDate,
    current: Boolean(item.current),
    summary: item.summary,
    highlights: Array.isArray(item.highlights) ? item.highlights : [],
    technologies: Array.isArray(item.technologies) ? item.technologies : [],
    sortOrder: Number(item.sortOrder ?? 0),
    published: Boolean(item.published),
    ...overrides
  };
}

async function listResponse(type) {
  if (type === 'experiences') return getAdminExperiences(currentPage);
  return getAdminList(type, currentPage);
}

async function deleteItem(type, id) {
  if (type === 'experiences') return deleteExperience(id);
  return deleteAdminItem(type, id);
}

async function toggleExperience(item) {
  await updateExperience(item.id, experiencePayload(item, { published: !item.published }));
}

async function loadList(type) {
  const result = await listResponse(type);
  cache = Array.isArray(result.content) ? result.content : [];
  const totalPages = Number(result.totalPages || 0);

  if (type === 'projects') toolbar('Add project', () => showEditor('projects'));
  else if (type === 'experiences') toolbar('Add experience', () => showEditor('experiences'));
  else if (type === 'blog') toolbar('Add article', () => showEditor('blog'));
  else toolbar('', null);

  const items = element('div', 'admin-items');
  if (!cache.length) items.append(pillCard('No items found', 'This collection is empty.'));

  for (const item of cache) {
    const heading = item.title || item.role || item.subject || `Rating ${item.rating} / 5`;
    const description = item.summary || item.excerpt || item.message || item.comment || 'No additional details';
    let detail = '';

    if (type === 'projects') {
      detail = `${item.slug} • Order ${item.sortOrder} • ${item.published ? 'Published' : 'Draft'} • ${item.featured ? 'Featured' : 'Standard'}`;
    } else if (type === 'experiences') {
      detail = `${item.company} • ${item.employmentType} • ${item.startDate} → ${item.current ? 'Present' : item.endDate} • ${item.published ? 'Published' : 'Draft'}`;
    } else if (type === 'blog') {
      detail = `${item.slug} • ${item.published ? 'Published' : 'Draft'}`;
    } else if (type === 'messages') {
      detail = `${item.name} · ${item.email} · ${item.inboxStatus} · Mail ${item.notificationStatus}`;
    } else {
      detail = `${item.category} · ${new Date(item.createdAt).toLocaleDateString()}`;
    }

    const card = pillCard(heading, description, detail);
    const actions = element('div', 'admin-card__actions');

    if (type === 'projects') {
      actions.append(
        button('Edit', () => showEditor('projects', item)),
        button(item.published ? 'Unpublish' : 'Publish', () => mutate(type, item.id, { published: !item.published })),
        button(item.featured ? 'Unfeature' : 'Feature', () => mutate(type, item.id, { featured: !item.featured })),
        button('↑', () => mutate(type, item.id, { sortOrder: Math.max(0, item.sortOrder - 10) })),
        button('↓', () => mutate(type, item.id, { sortOrder: item.sortOrder + 10 }))
      );
    } else if (type === 'experiences') {
      actions.append(
        button('Edit', () => showEditor('experiences', item)),
        button(item.published ? 'Unpublish' : 'Publish', async () => {
          try {
            await toggleExperience(item);
            status('Changes saved.');
            await switchTab(type, false);
          } catch (error) {
            status(error.message);
          }
        })
      );
    } else if (type === 'blog') {
      actions.append(
        button('Edit', () => showEditor('blog', item)),
        button(item.published ? 'Unpublish' : 'Publish', () => mutate(type, item.id, { published: !item.published }))
      );
    } else if (type === 'messages') {
      for (const state of ['READ', 'ARCHIVED', 'SPAM']) {
        actions.append(button(state, () => mutate(type, item.id, { inboxStatus: state })));
      }
      if (item.notificationStatus === 'FAILED') {
        actions.append(button('Retry email', async () => {
          try {
            await retryNotification(item.id);
            status('Email notification queued.');
            await switchTab(type);
          } catch (error) {
            status(error.message);
          }
        }));
      }
    }

    actions.append(button('Delete', async () => {
      const name = resourceNames[type] || 'item';
      if (!confirm(`Delete this ${name}? This cannot be undone.`)) return;
      try {
        await deleteItem(type, item.id);
        status('Deleted successfully.');
        await switchTab(type);
      } catch (error) {
        status(error.message);
      }
    }));

    card.append(actions);
    items.append(card);
  }

  byId('admin-collection').append(items);

  if (totalPages > 1) {
    const pager = element('div', 'admin-pagination');
    if (currentPage > 0) {
      pager.append(button('Previous page', async () => {
        currentPage -= 1;
        await switchTab(type, false);
      }));
    }
    if (currentPage + 1 < totalPages) {
      pager.append(button('Next page', async () => {
        currentPage += 1;
        await switchTab(type, false);
      }));
    }
    byId('admin-collection').append(pager);
  }
}

async function mutate(type, id, patch) {
  try {
    await patchAdminItem(type, id, patch);
    status('Changes saved.');
    await switchTab(type, false);
  } catch (error) {
    status(error.message);
  }
}

const specs = {
  projects: [
    ['slug', 'text', true],
    ['title', 'text', true],
    ['summary', 'textarea', true],
    ['description', 'textarea', false],
    ['problemStatement', 'textarea', false],
    ['architectureOverview', 'textarea', false],
    ['architectureFlow', 'lines', false],
    ['keyMetrics', 'lines', false],
    ['technologies', 'csv', true],
    ['githubUrl', 'url', false],
    ['liveUrl', 'url', false],
    ['imageUrl', 'url', false],
    ['imageAlt', 'text', false],
    ['sortOrder', 'number', true],
    ['published', 'checkbox', false],
    ['featured', 'checkbox', false]
  ],
  experiences: [
    ['role', 'text', true],
    ['company', 'text', true],
    ['employmentType', 'text', true],
    ['workMode', 'text', true],
    ['location', 'text', true],
    ['startDate', 'date', true],
    ['endDate', 'date', false],
    ['current', 'checkbox', false],
    ['summary', 'textarea', true],
    ['highlights', 'lines', true],
    ['technologies', 'csv', true],
    ['sortOrder', 'number', true],
    ['published', 'checkbox', false]
  ],
  blog: [
    ['slug', 'text', true],
    ['title', 'text', true],
    ['excerpt', 'textarea', true],
    ['articleUrl', 'url', true],
    ['tags', 'csv', true],
    ['coverImageUrl', 'url', false],
    ['coverImageAlt', 'text', false],
    ['published', 'checkbox', false]
  ]
};

const limits = {
  projects: {
    slug: 100,
    title: 120,
    summary: 300,
    description: 5000,
    problemStatement: 1000,
    architectureOverview: 2000,
    imageAlt: 180
  },
  experiences: {
    role: 120,
    company: 120,
    employmentType: 80,
    workMode: 80,
    location: 120,
    summary: 1500
  },
  blog: {
    slug: 100,
    title: 150,
    excerpt: 400,
    coverImageAlt: 180
  },
  profile: {
    fullName: 100,
    brand: 20,
    tagline: 180,
    publicEmail: 254,
    phone: 30,
    location: 120,
    instagramHandle: 40
  }
};

function field(type, name, kind, required, value) {
  const label = element('label', kind === 'checkbox' ? 'admin-check' : '');
  const pretty = name.replace(/([A-Z])/g, ' $1').replace(/^./, (letter) => letter.toUpperCase());
  const usesTextarea = kind === 'textarea' || kind === 'lines';
  const control = element(usesTextarea ? 'textarea' : 'input');
  control.name = name;

  if (!usesTextarea) {
    control.type = kind === 'checkbox' ? 'checkbox' : kind === 'csv' ? 'text' : kind;
  }

  if (kind === 'checkbox') {
    control.checked = Boolean(value);
  } else if (kind === 'csv') {
    control.value = Array.isArray(value) ? value.join(', ') : '';
  } else if (kind === 'lines') {
    control.value = Array.isArray(value) ? value.join('\n') : '';
    control.rows = name === 'architectureFlow' || name === 'highlights' ? 7 : 5;
  } else {
    control.value = value ?? '';
  }

  if (required && kind !== 'checkbox') control.required = true;
  if (name === 'sortOrder') {
    control.min = '0';
    control.max = '100000';
  }
  if (name === 'slug') control.pattern = '[a-z0-9]+(?:-[a-z0-9]+)*';
  if (limits[type]?.[name]) control.maxLength = limits[type][name];

  label.append(element('span', '', pretty), control);
  if (name === 'architectureFlow') {
    label.append(element('small', 'admin-field-hint', 'One step per line. Maximum 8 steps, 160 characters each.'));
  } else if (name === 'keyMetrics') {
    label.append(element('small', 'admin-field-hint', 'One metric per line. Maximum 4 metrics, 120 characters each.'));
  } else if (name === 'highlights') {
    label.append(element('small', 'admin-field-hint', 'One contribution per line. Maximum 8 items, 300 characters each.'));
  } else if (name === 'technologies' && type === 'experiences') {
    label.append(element('small', 'admin-field-hint', 'Comma-separated. Maximum 15 technologies, 60 characters each.'));
  }
  return label;
}

function parseLineList(input) {
  return input.value
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
}

function validateProject(values) {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug)) return 'Use a lowercase hyphenated slug.';
  if (values.architectureFlow.length > 8) return 'Architecture flow supports up to 8 steps.';
  if (values.architectureFlow.some((step) => step.length > 160)) return 'Each architecture flow step must be 160 characters or fewer.';
  if (values.keyMetrics.length > 4) return 'Key metrics supports up to 4 badges.';
  if (values.keyMetrics.some((metric) => metric.length > 120)) return 'Each key metric must be 120 characters or fewer.';
  if (values.technologies.length > 15) return 'Technologies supports up to 15 items.';
  if (values.technologies.some((technology) => technology.length > 60)) return 'Each technology must be 60 characters or fewer.';
  for (const key of ['githubUrl', 'liveUrl', 'imageUrl']) {
    if (!isHttpsUrl(values[key])) return `${key} must use HTTPS.`;
  }
  return '';
}

function isHttpsUrl(value, required = false) {
  if (!value) return !required;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && !url.hash && !url.port;
  } catch {
    return false;
  }
}

function validateBlog(values) {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug)) return 'Use a lowercase hyphenated slug.';
  if (values.tags.length > 12) return 'Tags supports up to 12 items.';
  if (values.tags.some((tag) => tag.length > 50)) return 'Each tag must be 50 characters or fewer.';
  if (!isHttpsUrl(values.articleUrl, true)) return 'Article URL must use HTTPS.';
  if (!isHttpsUrl(values.coverImageUrl)) return 'Cover image URL must use HTTPS.';
  return '';
}

function validateExperience(values) {
  if (values.current && values.endDate) return 'Current experience must not have an end date.';
  if (!values.current && !values.endDate) return 'End date is required for past experience.';
  if (values.endDate && values.startDate && values.endDate < values.startDate) return 'End date cannot precede start date.';
  if (values.highlights.length > 8) return 'Highlights supports up to 8 items.';
  if (values.highlights.some((highlight) => highlight.length > 300)) return 'Each highlight must be 300 characters or fewer.';
  if (values.technologies.length > 15) return 'Technologies supports up to 15 items.';
  if (values.technologies.some((technology) => technology.length > 60)) return 'Each technology must be 60 characters or fewer.';
  return '';
}

function editorTitle(type, editing) {
  const label = type === 'projects' ? 'project' : type === 'experiences' ? 'experience' : 'article';
  return `${editing ? 'Edit' : 'Create'} ${label}`;
}

function editorHelp(type) {
  if (type === 'projects') return 'Case-study fields power the recruiter-facing project drawer.';
  if (type === 'experiences') return 'Experience fields power the public work timeline and live search.';
  return 'Published article metadata appears in the public writing section.';
}

function formValues(type, form) {
  const values = {};
  for (const [name, kind] of specs[type]) {
    const input = form.elements.namedItem(name);
    values[name] = kind === 'checkbox'
      ? input.checked
      : kind === 'number'
        ? Number(input.value)
        : kind === 'csv'
          ? input.value.split(',').map((value) => value.trim()).filter(Boolean)
          : kind === 'lines'
            ? parseLineList(input)
            : input.value;
  }
  if (type === 'experiences' && values.current) values.endDate = null;
  return values;
}

function showEditor(type, item = null) {
  const panel = byId('admin-editor-panel');
  const form = byId('admin-editor');
  byId('admin-editor-title').textContent = editorTitle(type, Boolean(item));
  byId('admin-editor-help').textContent = editorHelp(type);

  form.replaceChildren();
  form.className = 'admin-editor';
  for (const [name, kind, required] of specs[type]) {
    form.append(field(type, name, kind, required, item?.[name]));
  }

  const actions = element('div', 'admin-editor-actions');
  const save = element('button', 'button button--primary', 'Save');
  save.type = 'submit';
  actions.append(save, button('Cancel', () => { panel.hidden = true; }));
  form.append(actions);
  panel.hidden = false;

  editorSubmitController?.abort();
  editorSubmitController = new AbortController();
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const values = formValues(type, form);

    const validationError = type === 'projects'
      ? validateProject(values)
      : type === 'experiences'
        ? validateExperience(values)
        : type === 'blog'
          ? validateBlog(values)
          : '';
    if (validationError) {
      status(validationError);
      return;
    }

    if (type === 'blog') values.publishedAt = item?.publishedAt || new Date().toISOString();

    save.disabled = true;
    try {
      if (type === 'experiences') {
        if (item) await updateExperience(item.id, values);
        else await createExperience(values);
      } else if (item) {
        await putAdminItem(type, item.id, values);
      } else {
        await createAdminItem(type, values);
      }
      panel.hidden = true;
      status('Saved successfully.');
      await switchTab(type, false);
    } catch (error) {
      status(error.message);
    } finally {
      save.disabled = false;
    }
  }, { signal: editorSubmitController.signal });

  panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function renderDashboard() {
  const overview = await getDashboard();
  const grid = element('div', 'admin-stats');
  for (const [key, label] of Object.entries({
    totalProjects: 'Total projects',
    publishedBlogs: 'Published blogs',
    unreadMessages: 'Unread messages',
    activeRateLimitBlocks: 'Active throttle blocks'
  })) {
    const stat = element('article', 'admin-stat');
    stat.append(element('strong', '', String(overview[key] ?? 0)), element('span', '', label));
    grid.append(stat);
  }
  byId('admin-collection').append(grid);
}

async function renderProfile() {
  const profile = await getProfile();
  const form = element('form', 'admin-editor');
  const fields = [
    'fullName',
    'brand',
    'tagline',
    'publicEmail',
    'phone',
    'location',
    'websiteUrl',
    'githubUrl',
    'linkedinUrl',
    'instagramHandle'
  ];

  fields.forEach((key) => form.append(field(
    'profile',
    key,
    key.endsWith('Url') ? 'url' : key === 'publicEmail' ? 'email' : 'text',
    key === 'fullName' || key === 'publicEmail' || key === 'location',
    profile[key]
  )));

  const education = field('profile', 'education', 'textarea', false, JSON.stringify(profile.education || [], null, 2));
  education.querySelector('textarea').rows = 7;
  form.append(education);

  const submit = element('button', 'button button--primary', 'Save profile');
  submit.type = 'submit';
  form.append(submit);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const body = {};
    fields.forEach((key) => { body[key] = form.elements.namedItem(key).value; });
    try {
      body.education = JSON.parse(form.elements.namedItem('education').value);
      if (!Array.isArray(body.education)) throw new Error('Education must be an array');
      if (body.education.length > 12) throw new Error('Education supports up to 12 entries');
      for (const key of ['websiteUrl', 'githubUrl', 'linkedinUrl']) {
        if (!isHttpsUrl(body[key])) throw new Error(`${key} must use HTTPS`);
      }
      await saveProfile(body);
      status('Profile saved.');
    } catch (error) {
      status(error.message);
    }
  });
  byId('admin-collection').append(form);
}

async function switchTab(next, resetPage = true) {
  if (!tabs.includes(next)) return;
  activeTab = next;
  if (resetPage) currentPage = 0;

  document.querySelectorAll('[data-tab]').forEach((control) => {
    control.setAttribute('aria-current', control.dataset.tab === activeTab ? 'page' : 'false');
  });
  byId('admin-section-title').textContent = titles[activeTab];
  byId('admin-editor-panel').hidden = true;
  byId('admin-collection').replaceChildren(element('p', '', 'Loading admin data…'));

  try {
    byId('admin-collection').replaceChildren();
    if (activeTab === 'dashboard') await renderDashboard();
    else if (activeTab === 'profile') await renderProfile();
    else await loadList(activeTab);
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      setAuthenticated(false);
      loginStatus('Your admin session expired. Please sign in again.');
      return;
    }
    status(error.message);
    byId('admin-collection').replaceChildren(pillCard('Data unavailable', 'Try refreshing the dashboard.'));
  }
}

async function start() {
  initTheme();

  byId('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    loginStatus('Signing in…');
    const data = new FormData(event.currentTarget);
    try {
      await login(String(data.get('username')), String(data.get('password')));
      event.currentTarget.reset();
      setAuthenticated(true);
      loginStatus('');
      await switchTab('dashboard');
    } catch (error) {
      loginStatus(error.status === 429
        ? 'Too many attempts. Try again later.'
        : 'Unable to sign in. Check credentials and connection.');
    }
  });

  byId('admin-logout').addEventListener('click', async () => {
    try {
      await logout();
      setAuthenticated(false);
      loginStatus('Signed out.');
    } catch {
      setAuthenticated(false);
      loginStatus('Sign-out could not be confirmed. Close this browser tab and revoke the session if necessary.');
    }
  });

  byId('admin-refresh').addEventListener('click', () => switchTab(activeTab, false));
  document.querySelectorAll('[data-tab]').forEach((control) => {
    control.addEventListener('click', () => switchTab(control.dataset.tab));
  });

  try {
    await currentUser();
    setAuthenticated(true);
    await switchTab('dashboard');
  } catch {
    setAuthenticated(false);
  }
}

start();
