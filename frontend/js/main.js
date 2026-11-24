import { ApiError, getBlogPosts, getExperiences, getProjects, getPublicProfile, sendContact } from './api.js';
import { initCookieConsent, renderBlogPosts, renderEmpty, renderExperiences, renderProjects, renderSkeletons } from './ui.js';
import { setAnalyticsConsent, trackPageView } from './analytics.js';
import { initTheme } from './theme.js?v=20261124-theme-visible-v4';
import { initSearch } from './search.js';
import { initFeedback } from './feedback.js';


async function populateProfile() {
  try {
    const owner = await getPublicProfile();
    const name = document.getElementById('owner-name');
    const tagline = document.getElementById('owner-tagline');
    if (name && owner.fullName) name.textContent = owner.fullName;
    if (tagline && owner.tagline) tagline.textContent = owner.tagline;
    document.querySelectorAll('[data-profile-email]').forEach(link => {
      if (typeof owner.publicEmail === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(owner.publicEmail)) {
        link.textContent = owner.publicEmail; link.href = `mailto:${owner.publicEmail}`;
      }
    });
    document.querySelectorAll('[data-profile-location]').forEach(node => {
      if (owner.location) node.textContent = owner.location;
    });
    document.querySelectorAll('[data-profile-phone]').forEach(link => {
      if (typeof owner.phone === 'string' && /^[+0-9 ()-]{7,30}$/.test(owner.phone)) {
        link.textContent = owner.phone; link.href = `tel:${owner.phone.replace(/[^+0-9]/g,'')}`;
      }
    });
    const applyHttpsProfileLink = (selector, value) => {
      if (typeof value !== 'string') return;
      try {
        const url = new URL(value);
        if (url.protocol !== 'https:' || url.username || url.password) return;
        document.querySelectorAll(selector).forEach((link) => { link.href = url.href; });
      } catch {
      }
    };
    applyHttpsProfileLink('[data-profile-github]', owner.githubUrl);
    applyHttpsProfileLink('[data-profile-linkedin]', owner.linkedinUrl);
    const education = document.getElementById('education-list');
    if (education && Array.isArray(owner.education) && owner.education.length) {
      const fragment = document.createDocumentFragment();
      owner.education.slice(0,12).forEach(entry => {
        const card = document.createElement('article'); card.className='skill-card';
        const heading=document.createElement('h3');heading.textContent=entry.qualification || 'Education';
        const body=document.createElement('p');body.textContent=[entry.institution,entry.location,entry.score].filter(Boolean).join(' · ');
        card.append(heading,body);fragment.append(card);
      }); education.replaceChildren(fragment);
    }
  } catch {  }
}

function initNavigation() {
  const toggle = document.getElementById('nav-toggle');
  const nav = document.getElementById('site-nav');
  if (!toggle || !nav) return;
  const close = () => { toggle.setAttribute('aria-expanded', 'false'); nav.classList.remove('is-open'); };
  toggle.addEventListener('click', () => {
    const open = toggle.getAttribute('aria-expanded') !== 'true';
    toggle.setAttribute('aria-expanded', String(open));
    nav.classList.toggle('is-open', open);
  });
  nav.querySelectorAll('a').forEach((link) => link.addEventListener('click', close));
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') close(); });
  document.addEventListener('click', (event) => {
    if (!nav.contains(event.target) && !toggle.contains(event.target)) close();
  });
}

async function populateSections() {
  const projects = document.getElementById('projects-list');
  const experience = document.getElementById('experience-list');
  const blog = document.getElementById('blog-list');
  if (projects) {
    renderSkeletons(projects, 3);
    getProjects().then((data) => renderProjects(projects, data))
      .catch(() => renderEmpty(projects, 'Unable to load projects',
        'The project feed is temporarily unavailable. Please try again later.', '!'));
  }
  if (experience) {
    renderSkeletons(experience, 1);
    getExperiences().then((data) => renderExperiences(experience, data))
      .catch(() => renderEmpty(experience, 'Unable to load experience',
        'The experience feed is temporarily unavailable. Please try again later.', '!'));
  }
  if (blog) {
    renderSkeletons(blog, 3);
    getBlogPosts().then((data) => renderBlogPosts(blog, data))
      .catch(() => renderEmpty(blog, 'Unable to load articles',
        'The article feed is temporarily unavailable. Please check again later.', '!'));
  }
}

function initForm() {
  const form = document.getElementById('contact-form');
  if (!form) return;
  const status = document.getElementById('form-status');
  const submit = form.querySelector('[type="submit"]');
  const keys = ['name', 'email', 'subject', 'message', 'privacyAccepted'];
  const errorFor = (key) => document.getElementById(`${key}-error`);
  const showError = (key, message) => {
    const field = form.elements.namedItem(key);
    if (field) field.setAttribute('aria-invalid', message ? 'true' : 'false');
    const output = errorFor(key);
    if (output) output.textContent = message || '';
  };
  const validate = (values) => {
    const errors = {};
    if (!values.name.trim() || values.name.length > 100) errors.name = 'Enter your name (100 characters maximum).';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email) || values.email.length > 254)
      errors.email = 'Enter a valid email address.';
    if (!values.subject.trim() || values.subject.length > 150) errors.subject = 'Enter a subject (150 characters maximum).';
    if (!values.message.trim() || values.message.length > 5000) errors.message = 'Enter a message (5,000 characters maximum).';
    if (!values.privacyAccepted) errors.privacyAccepted = 'Please accept the privacy policy.';
    return errors;
  };
  const valuesFromForm = () => ({
    name: form.elements.namedItem('name').value,
    email: form.elements.namedItem('email').value,
    subject: form.elements.namedItem('subject').value,
    message: form.elements.namedItem('message').value,
    website: form.elements.namedItem('website').value,
    privacyAccepted: form.elements.namedItem('privacyAccepted').checked
  });
  keys.forEach((key) => {
    const field = form.elements.namedItem(key);
    field.addEventListener(key === 'privacyAccepted' ? 'change' : 'input', () => showError(key, ''));
  });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    status.textContent = '';
    status.className = 'form-status';
    keys.forEach((key) => showError(key, ''));
    const data = valuesFromForm();
    const errors = validate(data);
    if (Object.keys(errors).length) {
      Object.entries(errors).forEach(([key, message]) => showError(key, message));
      status.textContent = 'Please correct the highlighted fields.';
      status.classList.add('form-status--error');
      form.elements.namedItem(Object.keys(errors)[0]).focus();
      return;
    }
    submit.disabled = true;
    submit.textContent = 'Sending message…';
    try {
      const response = await sendContact(data);
      const reference = typeof response?.referenceId === 'string' ? response.referenceId : '';
      const url = new URL('./thank-you.html', window.location.href);
      if (reference) url.searchParams.set('reference', reference);
      location.assign(url.href);
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        Object.entries(error.fieldErrors).forEach(([key, message]) => {
          if (keys.includes(key)) showError(key, message);
        });
      }
      status.textContent = error.status === 429
        ? 'Too many messages right now. Please try again later.'
        : (error.message || 'Unable to submit your message.');
      status.classList.add('form-status--error');
    } finally {
      submit.disabled = false;
      submit.textContent = 'Send message';
    }
  });
}

function initReceipt() {
  const receipt = document.getElementById('receipt-reference');
  if (!receipt) return;
  const reference = new URLSearchParams(location.search).get('reference');
  if (reference && /^[0-9a-f-]{36}$/i.test(reference)) {
    receipt.textContent = reference;
    receipt.parentElement.hidden = false;
  }
}

initTheme();
initSearch();
initFeedback();
initNavigation();
populateSections();
populateProfile();
initForm();
initReceipt();
initCookieConsent((accepted) => { setAnalyticsConsent(accepted); trackPageView(); });
const year = document.getElementById('copyright-year');
if (year) year.textContent = String(new Date().getFullYear());
