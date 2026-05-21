// Lightweight API client with optional SSE support and a graceful offline fallback.
//
// Uses a relative URL by default so the Vite dev proxy can forward /api to the
// Spring Boot backend on localhost:8080. To override (e.g. when running the
// built frontend on a different host), set VITE_API_ROOT in a .env file.
export const API_ROOT = (import.meta.env && import.meta.env.VITE_API_ROOT) || '/api';

let actorHeader = null;
export const setActor = (username) => { actorHeader = username; };

async function request(path, opts = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(actorHeader ? { 'X-Actor': actorHeader } : {}),
    ...(opts.headers || {}),
  };
  let res;
  try {
    res = await fetch(`${API_ROOT}${path}`, { ...opts, headers });
  } catch (networkErr) {
    const message = `Unable to reach backend at ${API_ROOT}. Is the Spring Boot server running on port 8080?`;
    const err = new Error(message);
    err.network = true;
    err.cause = networkErr;
    throw err;
  }
  if (!res.ok) {
    let body = { error: `Request failed (${res.status})` };
    try { body = { ...body, ...(await res.json()) }; } catch (_) {}
    const err = new Error(body.error || body.message || `Request failed (${res.status})`);
    err.status = res.status;
    err.payload = body;
    throw err;
  }
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  // Auth
  login: (username, password) => request('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  register: (payload) => request('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),

  // Students
  list: ({ q = '', status = '', sortBy = 'id', order = 'asc' } = {}) => {
    const params = new URLSearchParams({ q, status, sortBy, order });
    return request(`/students?${params.toString()}`);
  },
  get: (id) => request(`/students/${id}`),
  create: (payload) => request('/students', { method: 'POST', body: JSON.stringify(payload) }),
  update: (id, payload) => request(`/students/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  remove: (id) => request(`/students/${id}`, { method: 'DELETE' }),
  bulkDelete: (ids) => request('/students/bulk-delete', { method: 'POST', body: JSON.stringify({ ids }) }),
  attendance: (id, attendance) => request(`/students/${id}/attendance`, { method: 'PATCH', body: JSON.stringify({ attendance }) }),
  grade: (id, semester, gpa) => request(`/students/${id}/grade`, { method: 'PATCH', body: JSON.stringify({ semester, gpa }) }),

  analytics: () => request('/students/analytics'),
  top: (limit = 10) => request(`/students/top?limit=${limit}`),
  importData: () => request('/students/import-department-data', { method: 'POST' }),
  profile: (id, payload) => request(`/students/profile/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  activity: (limit = 25) => request(`/students/activity?limit=${limit}`),
  notifications: (user = '*') => request(`/students/notifications?user=${encodeURIComponent(user)}`),
  notifRead: (id) => request(`/students/notifications/${id}/read`, { method: 'POST' }),
  notifReadAll: (user = '*') => request(`/students/notifications/read-all?user=${encodeURIComponent(user)}`, { method: 'POST' }),

  // Announcements
  announcements: () => request('/students/announcements'),
  postAnnouncement: (payload) => request('/students/announcements', { method: 'POST', body: JSON.stringify(payload) }),
  deleteAnnouncement: (id) => request(`/students/announcements/${id}`, { method: 'DELETE' }),
  pinAnnouncement: (id, pinned) => request(`/students/announcements/${id}/pin`, { method: 'POST', body: JSON.stringify({ pinned }) }),

  // Predictor / Achievements / Compare
  predict: (payload) => request('/students/predict', { method: 'POST', body: JSON.stringify(payload) }),
  achievements: (id) => request(`/students/${id}/achievements`),
  compare: (ids) => request('/students/compare', { method: 'POST', body: JSON.stringify({ ids }) }),

  health: () => request('/students/health'),
};

// Quick health probe: resolves to true if the backend is reachable.
export async function probeBackend() {
  try {
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), 2500);
    const res = await fetch(`${API_ROOT}/students/health`, { signal: ctrl.signal });
    clearTimeout(t);
    return res.ok;
  } catch (_) {
    return false;
  }
}

// ----- Server-sent events -----
export function subscribeStream({ onEvent, onError } = {}) {
  let es;
  try {
    es = new EventSource(`${API_ROOT}/students/stream`);
  } catch (e) {
    onError && onError(e);
    return () => {};
  }
  const handlers = ['hello', 'ping', 'activity', 'notification', 'students-changed'];
  handlers.forEach((name) => {
    es.addEventListener(name, (e) => {
      let data = e.data;
      try { data = JSON.parse(e.data); } catch (_) {}
      onEvent && onEvent(name, data);
    });
  });
  es.onerror = (e) => { onError && onError(e); };
  return () => es.close();
}
