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
