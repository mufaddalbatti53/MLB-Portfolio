import { getProject } from './api.js';

const create = (tag, className = '', text = '') => {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== '') el.textContent = String(text);
  return el;
};

const append = (parent, ...children) => {
  parent.append(...children);
  return parent;
};

function validatedUrl(value) {
  if (typeof value !== 'string') return null;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
}

function externalLink(url, text, className = '') {
  const target = validatedUrl(url);
  if (!target) return null;
  const anchor = create('a', className, text);
  anchor.href = target;
  anchor.target = '_blank';
  anchor.rel = 'noopener noreferrer';
  return anchor;
}

export function renderSkeletons(container, count) {
  container.replaceChildren();
  for (let i = 0; i < count; i += 1) {
    const card = create('div', 'skeleton-card');
    card.setAttribute('aria-hidden', 'true');
    append(card,
      create('div', 'skeleton-block skeleton-card__image'),
      create('div', 'skeleton-block skeleton-card__line'),
      create('div', 'skeleton-block skeleton-card__line skeleton-card__line--short'));
    container.append(card);
  }
  container.setAttribute('aria-busy', 'true');
}

export function renderEmpty(container, heading, detail, glyph = '{}') {
  const panel = create('div', 'empty-state');
  append(panel,
    create('span', 'empty-state__icon', glyph),
    create('h3', '', heading),
    create('p', '', detail));
  container.replaceChildren(panel);
  container.removeAttribute('aria-busy');
}

function tagsRow(values) {
  const row = create('div', 'tags');
  (Array.isArray(values) ? values : []).slice(0, 15).forEach((value) => {
    row.append(create('span', 'tag', value));
  });
  return row;
}

function parseDate(value) {
  if (typeof value !== 'string') return null;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function monthLabel(value) {
  const date = parseDate(value);
  return date
    ? new Intl.DateTimeFormat('en', { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(date)
    : '';
}

function durationMonths(startValue, endValue, current) {
  const start = parseDate(startValue);
  const end = current ? new Date() : parseDate(endValue);
  if (!start || !end || end < start) return '';
  const months = (end.getUTCFullYear() - start.getUTCFullYear()) * 12
    + end.getUTCMonth() - start.getUTCMonth() + 1;
  return `${months} mo${months === 1 ? '' : 's'}`;
}

function employmentLabel(value) {
  if (value === 'Internship · Part-Time') return 'Part-Time Internship';
  return typeof value === 'string' ? value.replace(' · ', ' ') : '';
}

function workModeLabel(value) {
  if (typeof value !== 'string') return '';
  const match = value.match(/\(([^)]+)\)/);
  return match ? match[1] : value;
}

export function renderExperiences(container, experiences) {
  if (!Array.isArray(experiences) || experiences.length === 0) {
    renderEmpty(container, 'Experience details coming soon',
      'Published work experience will appear here when available.', '↗');
    return;
  }

  const timeline = create('div', 'experience-timeline');
  experiences.forEach((experience) => {
    const card = create('article', 'experience-card');
    const marker = create('span', 'experience-card__marker');
    marker.setAttribute('aria-hidden', 'true');

    const header = create('div', 'experience-card__header');
    const heading = create('div');
    append(heading,
      create('h3', '', experience.role || 'Role'),
      create('span', 'experience-company', experience.company || 'Company'));
    header.append(heading);

    const meta = create('div', 'experience-meta');
    [employmentLabel(experience.employmentType), workModeLabel(experience.workMode), experience.location]
      .filter(Boolean)
      .forEach((value) => meta.append(create('span', 'experience-pill', value)));

    const start = monthLabel(experience.startDate);
    const end = experience.current ? 'Present' : monthLabel(experience.endDate);
    const months = durationMonths(experience.startDate, experience.endDate, experience.current);
    const period = [start && end ? `${start} – ${end}` : start, months].filter(Boolean).join(' • ');
    if (period) meta.append(create('span', 'experience-pill experience-pill--date', period));

    card.append(marker, header, meta);
    if (experience.summary) card.append(create('p', 'experience-summary', experience.summary));

    const highlights = Array.isArray(experience.highlights)
      ? experience.highlights.filter((value) => typeof value === 'string' && value.trim()).slice(0, 8)
      : [];
    if (highlights.length) {
      const list = create('ul', 'experience-highlights');
      highlights.forEach((value) => list.append(create('li', '', value)));
      card.append(list);
    }

    card.append(tagsRow(experience.technologies));
    timeline.append(card);
  });

  container.replaceChildren(timeline);
  container.removeAttribute('aria-busy');
}

const caseStudyProjects = new Map();
let caseStudyListenersBound = false;
let caseStudyTrigger = null;

function caseStudyHash(slug) {
  return `#project-${slug}`;
}

function projectSlugFromHash() {
  const match = location.hash.match(/^#project-([a-z0-9]+(?:-[a-z0-9]+)*)$/);
  return match ? match[1] : null;
}

function caseStudySection(title, text) {
  if (typeof text !== 'string' || !text.trim()) return null;
  const section = create('section', 'case-study-section');
  append(section, create('h3', '', title), create('p', '', text));
  return section;
}

function architectureFlow(values) {
  const steps = Array.isArray(values) ? values.filter((value) => typeof value === 'string' && value.trim()).slice(0, 8) : [];
  if (!steps.length) return null;

  const section = create('section', 'case-study-section');
  section.append(create('h3', '', 'Architecture Flow'));
  const list = create('ol', 'architecture-flow');
  steps.forEach((step, index) => {
    const item = create('li', 'architecture-flow__step');
    const marker = create('span', 'architecture-flow__marker', String(index + 1).padStart(2, '0'));
    marker.setAttribute('aria-hidden', 'true');
    item.append(marker, create('span', 'architecture-flow__text', step));
    list.append(item);
  });
  section.append(list);
  return section;
}

function metricGrid(values) {
  const metrics = Array.isArray(values) ? values.filter((value) => typeof value === 'string' && value.trim()).slice(0, 4) : [];
  if (!metrics.length) return null;

  const section = create('section', 'case-study-section');
  section.append(create('h3', '', 'Key Metrics'));
  const grid = create('div', 'case-study-metrics');
  metrics.forEach((metric) => grid.append(create('span', 'case-study-metric', metric)));
  section.append(grid);
  return section;
}

function renderCaseStudy(project) {
  const content = document.getElementById('case-study-content');
  const title = document.getElementById('case-study-title');
  if (!content || !title) return;

  title.textContent = project.title || 'Project case study';
  content.replaceChildren();

  const intro = create('div', 'case-study-intro');
  intro.append(create('p', 'case-study-kicker', 'Engineering Case Study'));
  intro.append(create('p', 'case-study-summary', project.summary || 'Project overview'));
  content.append(intro);

  const problem = caseStudySection('Problem', project.problemStatement || project.description);
  if (problem) content.append(problem);

  const architecture = caseStudySection('Architecture & Solution', project.architectureOverview || project.description);
  if (architecture) content.append(architecture);

  const flow = architectureFlow(project.architectureFlow);
  if (flow) content.append(flow);

  const metrics = metricGrid(project.keyMetrics);
  if (metrics) content.append(metrics);

  const stack = Array.isArray(project.technologies) && project.technologies.length
    ? append(create('section', 'case-study-section'), create('h3', '', 'Tech Stack'), tagsRow(project.technologies))
    : null;
  if (stack) content.append(stack);

  const actions = create('div', 'case-study-actions');
  const live = externalLink(project.liveUrl, 'Open Live Demo ↗', 'button button--primary');
  const github = externalLink(project.githubUrl, 'View GitHub ↗', 'button button--secondary');
  if (live) actions.append(live);
  if (github) actions.append(github);
  if (actions.childElementCount) content.append(actions);
}

function openCaseStudy(project, updateHash = true, trigger = null) {
  const dialog = document.getElementById('project-case-study');
  if (!dialog) return;
  caseStudyTrigger = trigger || document.activeElement;
  renderCaseStudy(project);
  if (updateHash && location.hash !== caseStudyHash(project.slug)) {
    history.pushState(null, '', caseStudyHash(project.slug));
  }
  if (!dialog.open) dialog.showModal();
}

async function openCaseStudyFromHash() {
  const dialog = document.getElementById('project-case-study');
  const slug = projectSlugFromHash();
  if (!slug) {
    if (dialog?.open) dialog.close();
    return;
  }
  let project = caseStudyProjects.get(slug);
  if (!project) {
    try {
      project = await getProject(slug);
      caseStudyProjects.set(slug, project);
    } catch {
      return;
    }
  }
  openCaseStudy(project, false);
}

function bindCaseStudyDialog() {
  if (caseStudyListenersBound) return;
  const dialog = document.getElementById('project-case-study');
  if (!dialog) return;
  caseStudyListenersBound = true;

  document.querySelectorAll('[data-close-case-study]').forEach((button) => {
    button.addEventListener('click', () => dialog.close());
  });
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });
  dialog.addEventListener('close', () => {
    if (projectSlugFromHash()) {
      history.replaceState(null, '', `${location.pathname}${location.search}`);
    }
    if (caseStudyTrigger?.isConnected) caseStudyTrigger.focus();
    caseStudyTrigger = null;
  });
  window.addEventListener('hashchange', openCaseStudyFromHash);
}

export function renderProjects(container, projects) {
  if (!Array.isArray(projects) || projects.length === 0) {
    renderEmpty(container, 'The next project is taking shape',
      'Featured work will appear here as soon as it is published. Check back soon.');
    return;
  }

  bindCaseStudyDialog();
  caseStudyProjects.clear();
  const fragment = document.createDocumentFragment();

  projects.forEach((project) => {
    if (typeof project.slug === 'string') caseStudyProjects.set(project.slug, project);

    const card = create('article', 'project-card');
    const visual = create('div', 'project-card__visual');
    const imageUrl = validatedUrl(project.imageUrl);
    if (imageUrl && typeof project.imageAlt === 'string' && project.imageAlt.trim()) {
      const image = create('img');
      image.src = imageUrl;
      image.alt = project.imageAlt;
      image.loading = 'lazy';
      image.decoding = 'async';
      visual.append(image);
    } else {
      const glyph = create('span', 'project-card__glyph', '</>');
      glyph.setAttribute('aria-hidden', 'true');
      visual.append(glyph);
    }

    const body = create('div', 'project-card__body');
    append(body,
      create('h3', '', project.title || 'Untitled project'),
      create('p', '', project.summary || 'Project overview unavailable.'),
      tagsRow(project.technologies));

    const links = create('div', 'card-links');
    const caseStudy = create('button', 'case-study-trigger', 'Read Case Study');
    caseStudy.type = 'button';
    caseStudy.addEventListener('click', () => openCaseStudy(project, true, caseStudy));
    links.append(caseStudy);

    const live = externalLink(project.liveUrl, 'Live site ↗');
    const code = externalLink(project.githubUrl, 'Source code ↗');
    if (live) links.append(live);
    if (code) links.append(code);
    body.append(links);

    append(card, visual, body);
    fragment.append(card);
  });

  container.replaceChildren(fragment);
  container.removeAttribute('aria-busy');
  openCaseStudyFromHash();
}

export function renderBlogPosts(container, posts) {
  if (!Array.isArray(posts) || posts.length === 0) {
    renderEmpty(container, 'Articles are on their way',
      'Technical notes and development insights will appear here when published.', '✎');
    return;
  }

  const fragment = document.createDocumentFragment();
  posts.forEach((post) => {
    const card = create('article', 'blog-card');
    const date = post.publishedAt ? new Date(post.publishedAt) : null;
    const dateText = date && !Number.isNaN(date.getTime())
      ? new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
      : 'Article';
    append(card,
      create('span', 'blog-card__date', dateText),
      create('h3', '', post.title || 'Untitled article'),
      create('p', '', post.excerpt || ''),
      tagsRow(post.tags));
    const link = externalLink(post.articleUrl, 'Read article ↗', 'blog-card__link');
    if (link) card.append(link);
    fragment.append(card);
  });

  container.replaceChildren(fragment);
  container.removeAttribute('aria-busy');
}

export function initCookieConsent(onChange) {
  const banner = document.getElementById('cookie-banner');
  if (!banner) return;

  let existing = null;
  try {
    existing = localStorage.getItem('mlb-analytics-consent');
  } catch {
    existing = null;
  }

  if (existing === 'accepted' || existing === 'rejected') {
    onChange(existing === 'accepted');
    return;
  }

  banner.hidden = false;
  for (const button of banner.querySelectorAll('[data-consent]')) {
    button.addEventListener('click', () => {
      const accepted = button.dataset.consent === 'accepted';
      try {
        localStorage.setItem('mlb-analytics-consent', accepted ? 'accepted' : 'rejected');
      } catch {
      }
      banner.hidden = true;
      onChange(accepted);
    });
  }
}
