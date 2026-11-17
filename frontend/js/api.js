const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1']);

export class ApiError extends Error {
  constructor(message, status = 0, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function configuredProductionOrigin() {
  const value = document.querySelector('meta[name="mlb-api-origin"]')?.content?.trim() || '';
  if (!value) return '';
  try {
    const url = new URL(value);
    const validHost = url.hostname.endsWith('.onrender.com');
    const exactOrigin = url.pathname === '/' && !url.search && !url.hash && !url.username && !url.password;
    return url.protocol === 'https:' && validHost && exactOrigin ? url.origin : '';
  } catch {
    return '';
  }
}

function resolveApiOrigin() {
  if (LOCAL_HOSTS.has(window.location.hostname)) return 'http://localhost:8080';
  const configured = configuredProductionOrigin();
  if (configured) return configured;
  if (window.location.hostname.endsWith('.github.io')) return '';
  return window.location.origin;
}

export const API_BASE_URL = (() => {
  const origin = resolveApiOrigin();
  return origin ? `${origin}/api/v1` : '';
})();

export function apiUrl(path) {
  if (!API_BASE_URL) {
    throw new ApiError('Production API origin is not configured for this deployment.');
  }
  const raw = String(path || '');
  const withoutPrefix = raw.startsWith('/api/v1') ? raw.slice('/api/v1'.length) : raw;
  const normalized = withoutPrefix.startsWith('/') ? withoutPrefix : `/${withoutPrefix}`;
  return `${API_BASE_URL}${normalized}`;
}

async function request(path, { method = 'GET', data, timeoutMs = 10000 } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(apiUrl(path), {
      method,
      credentials: 'omit',
      cache: 'no-store',
      headers: { Accept: 'application/json', ...(data ? { 'Content-Type': 'application/json' } : {}) },
      body: data ? JSON.stringify(data) : undefined,
      signal: controller.signal
    });
    const contentType = response.headers.get('content-type') || '';
    const payload = contentType.includes('json') ? await response.json() : null;
    if (!response.ok) {
      const fields = payload?.errors && typeof payload.errors === 'object' ? payload.errors : {};
      throw new ApiError(payload?.detail || 'The request could not be completed.', response.status, fields);
    }
    return payload;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error.name === 'AbortError') throw new ApiError('The server took too long. Please try again.');
    throw new ApiError('Unable to connect. Please check your internet connection.');
  } finally {
    clearTimeout(timer);
  }
}

export const getPublicProfile = () => request('/profile');
export const getProjects = (limit = 6) => request(`/projects?limit=${encodeURIComponent(limit)}`);
export const getProject = (slug) => request(`/projects/${encodeURIComponent(slug)}`);
export const getBlogPosts = (limit = 3) => request(`/blog?limit=${encodeURIComponent(limit)}`);
export const getExperiences = () => request('/experiences');
export const sendFeedback = (data) => request('/feedback', { method: 'POST', data, timeoutMs: 15000 });
export const sendContact = (data) => request('/contact', { method: 'POST', data, timeoutMs: 15000 });
