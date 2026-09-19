export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `Request failed with status ${status}`);
    this.status = status;
    this.body = body;
  }
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
    ...options,
  });
  if (response.status === 204) return null;

  const contentType = response.headers.get('content-type') ?? '';
  const data = contentType.includes('application/json') ? await response.json() : null;
  if (!response.ok) throw new ApiError(response.status, data);
  return data;
}

export const login = (accessCode) =>
  request('/api/session', { method: 'POST', body: JSON.stringify({ accessCode }) });

export const logout = () => request('/api/session', { method: 'DELETE' });

export const getMe = () => request('/api/me');

export const updateMe = (patch) =>
  request('/api/me', { method: 'PATCH', body: JSON.stringify(patch) });

export const getCurrentBook = () => request('/api/book/current');

export const getMessages = (topicId) => request(`/api/topics/${topicId}/messages`);

export const postMessage = (topicId, payload) =>
  request(`/api/topics/${topicId}/messages`, { method: 'POST', body: JSON.stringify(payload) });

export const editMessage = (id, body) =>
  request(`/api/messages/${id}`, { method: 'PATCH', body: JSON.stringify({ body }) });

export const deleteMessage = (id) => request(`/api/messages/${id}`, { method: 'DELETE' });

export const adminListUsers = () => request('/api/admin/users');

export const adminCreateUser = (payload) =>
  request('/api/admin/users', { method: 'POST', body: JSON.stringify(payload) });

export const adminDeleteUser = (id) => request(`/api/admin/users/${id}`, { method: 'DELETE' });

export const adminResetUserCode = (id) => request(`/api/admin/users/${id}/reset-code`, { method: 'POST' });

export const adminListBooks = () => request('/api/admin/books');

export const adminCreateBook = (payload) =>
  request('/api/admin/books', { method: 'POST', body: JSON.stringify(payload) });

export const adminActivateBook = (id) => request(`/api/admin/books/${id}/activate`, { method: 'POST' });

export const adminListTopics = (bookId) => request(`/api/admin/books/${bookId}/topics`);

export const adminCreateTopic = (payload) =>
  request('/api/admin/topics', { method: 'POST', body: JSON.stringify(payload) });

export const adminUpdateTopic = (id, patch) =>
  request(`/api/admin/topics/${id}`, { method: 'PATCH', body: JSON.stringify(patch) });
