import { getBlogPosts, getExperiences, getProjects } from './api.js';

const staticSkills = [
  'Java',
  'Spring Boot',
  'REST APIs',
  'MongoDB',
  'JavaScript',
  'HTML',
  'CSS',
  'UI/UX',
  'Full Stack Development',
  'Order Management Systems',
  'Computer Science Engineering',
  'NSEC'
];

function safeExternal(url) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' && !parsed.username && !parsed.password ? parsed.href : null;
  } catch {
    return null;
  }
}

function element(tag, className = '', text = '') {
  const node = document.createElement(tag);
  if (className) node.className = className;
  node.textContent = text;
  return node;
}

function markText(parent, text, needle) {
  const source = String(text ?? '');
  const normalized = source.toLocaleLowerCase();
  let cursor = 0;
  let match = normalized.indexOf(needle);

  while (match >= 0) {
    parent.append(document.createTextNode(source.slice(cursor, match)));
    parent.append(element('mark', '', source.slice(match, match + needle.length)));
    cursor = match + needle.length;
    match = normalized.indexOf(needle, cursor);
  }

  parent.append(document.createTextNode(source.slice(cursor)));
}

function experienceDetail(experience) {
  return [
    experience.company,
    experience.employmentType,
    experience.workMode,
    experience.location,
    ...(Array.isArray(experience.technologies) ? experience.technologies : [])
  ].filter(Boolean).join(' · ');
}

export function initSearch() {
  const modal = document.getElementById('site-search');
  const input = document.getElementById('search-input');
  const list = document.getElementById('search-results');
  if (!modal || !input || !list) return;

  let index = null;
  let lastTrigger = null;

  const load = async () => {
    if (index) return index;
    const [projects, experiences, blog] = await Promise.allSettled([
      getProjects(24),
      getExperiences(),
      getBlogPosts(24)
    ]);

    index = [
      ...(projects.status === 'fulfilled' ? projects.value : []).map((project) => ({
        type: 'Project',
        label: project.title,
        detail: [project.summary, ...(project.technologies || [])].filter(Boolean).join(' · '),
        href: typeof project.slug === 'string'
          ? `./index.html#project-${project.slug}`
          : './index.html#projects'
      })),
      ...(experiences.status === 'fulfilled' ? experiences.value : []).map((experience) => ({
        type: 'Experience',
        label: experience.role,
        detail: experienceDetail(experience),
        href: './index.html#experience'
      })),
      ...staticSkills.map((skill) => ({
        type: 'Skill',
        label: skill,
        detail: 'Technical expertise',
        href: './index.html#skills'
      })),
      ...(blog.status === 'fulfilled' ? blog.value : []).map((article) => ({
        type: 'Article',
        label: article.title,
        detail: article.excerpt,
        href: safeExternal(article.articleUrl) || './index.html#blog'
      }))
    ];

    return index;
  };

  const render = () => {
    const needle = input.value.trim().toLocaleLowerCase();
    list.replaceChildren();

    if (!needle) {
      list.append(element('p', 'search-empty', 'Search projects, experience, skills, and published articles.'));
      return;
    }

    const matches = (index || [])
      .filter((item) => `${item.label} ${item.detail}`.toLocaleLowerCase().includes(needle))
      .slice(0, 25);

    if (!matches.length) {
      list.append(element('p', 'search-empty', 'No matching items found.'));
      return;
    }

    matches.forEach((item) => {
      const link = element('a', 'search-result');
      link.href = item.href;
      link.append(element('span', 'search-result__type', item.type));

      const title = element('strong');
      markText(title, item.label, needle);
      link.append(title);

      const detail = element('small');
      markText(detail, item.detail, needle);
      link.append(detail);
      list.append(link);
    });
  };

  const open = async (trigger) => {
    if (!modal.open) {
      lastTrigger = trigger || document.activeElement;
      modal.showModal();
      input.focus();
    }

    list.replaceChildren(element('p', 'search-empty', 'Searching portfolio content…'));
    try {
      await load();
      render();
    } catch {
      list.replaceChildren(element('p', 'search-empty', 'Search is temporarily unavailable.'));
    }
  };

  document.querySelectorAll('[data-open-search]').forEach((button) => {
    button.addEventListener('click', () => open(button));
  });
  document.querySelectorAll('[data-close-search]').forEach((button) => {
    button.addEventListener('click', () => modal.close());
  });
  document.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      open();
    }
  });
  modal.addEventListener('close', () => {
    if (lastTrigger?.isConnected) lastTrigger.focus();
  });
  modal.addEventListener('click', (event) => {
    if (event.target === modal) modal.close();
  });
  input.addEventListener('input', render);
}
