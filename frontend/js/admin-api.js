import { ApiError, apiUrl } from './api.js';

const TOKEN_KEY = 'mlb-admin-token';

function readToken() {
  return sessionStorage.getItem(TOKEN_KEY) || '';
}

function writeToken(token) {
  if (typeof token === 'string' && token.length > 20) sessionStorage.setItem(TOKEN_KEY, token);
}

function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

async function send(path, { method = 'GET', data, timeout = 12000, authenticated = true } = {}) {
  const abort = new AbortController();
  const clock = setTimeout(() => abort.abort(), timeout);
  try {
    const token = authenticated ? readToken() : '';
    const response = await fetch(apiUrl(path), {
      method,
      credentials: 'omit',
      cache: 'no-store',
      signal: abort.signal,
      headers: {
        Accept: 'application/json',
        ...(data !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      ...(data !== undefined ? { body: JSON.stringify(data) } : {})
    });
    const type = response.headers.get('content-type') || '';
    const body = type.includes('json') ? await response.json() : null;
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) clearToken();
      throw new ApiError(body?.detail || `Request failed (${response.status})`, response.status, body?.errors || {});
    }
    return body;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error.name === 'AbortError') throw new ApiError('The admin request timed out');
    throw new ApiError('Unable to connect to the administration API');
  } finally {
    clearTimeout(clock);
  }
}


export async function login(username, password) {
  const body = await send('/auth/login', {
    method: 'POST',
    data: { username, password },
    authenticated: false
  });
  if (!body?.token) throw new ApiError('Authentication token was not returned by the server');
  writeToken(body.token);
  return { status: body.status || 'authenticated' };
}

export const currentUser = () => send('/auth/me');

export async function logout() {
  try {
    return await send('/auth/logout', { method: 'POST' });
  } finally {
    clearToken();
  }
}

export const getDashboard = () => send('/admin/dashboard');

export const getAdminExperiences = (page = 0) => send(`/admin/experiences?page=${encodeURIComponent(page)}&size=20`);
export const createExperience = (body) => send('/admin/experiences', { method: 'POST', data: body });
export const updateExperience = (id, body) => send(`/admin/experiences/${encodeURIComponent(id)}`, { method: 'PUT', data: body });
export const deleteExperience = (id) => send(`/admin/experiences/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const getAdminList = (resource, page = 0) => send(`/admin/${resource}?page=${encodeURIComponent(page)}&size=20`);
export const createAdminItem = (resource, body) => send(`/admin/${resource}`, { method: 'POST', data: body });
export const putAdminItem = (resource, id, body) => send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'PUT', data: body });
export const patchAdminItem = (resource, id, body) => send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'PATCH', data: body });
export const deleteAdminItem = (resource, id) => send(`/admin/${resource}/${encodeURIComponent(id)}`, { method: 'DELETE' });
export const retryNotification = (id) => send(`/admin/messages/${encodeURIComponent(id)}/retry`, { method: 'POST' });
export const getProfile = () => send('/admin/profile');
export const saveProfile = (body) => send('/admin/profile', { method: 'PUT', data: body });
