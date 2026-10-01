import * as api from './api.js';
import { ApiError } from './api.js';
import { renderLogin, renderApp, snippet } from './render.js';

const appRoot = document.getElementById('app');

const state = {
  mode: 'login', // 'login' | 'app'
  view: 'discussion', // 'discussion' | 'admin'
  user: null,
  book: null,
  messages: [],
  selectedTopicId: null,
  replyTo: null,
  editingMessageId: null,
  profileOpen: false,
  loginError: null,
  actionError: null,
  admin: {
    users: [],
    books: [],
    selectedBookId: null,
    topics: [],
    newUserCode: null,
    userError: null,
    newBookError: null,
    newTopicError: null,
  },
};

function render() {
  appRoot.innerHTML = state.mode === 'login' ? renderLogin({ error: state.loginError }) : renderApp(state);
  if (state.mode === 'login') appRoot.querySelector('#access-code')?.focus();
}

async function refreshMessages() {
  state.messages = state.selectedTopicId != null ? await api.getMessages(state.selectedTopicId) : [];
}

async function loadBook() {
  try {
    state.book = await api.getCurrentBook();
    state.selectedTopicId = state.book.topics[0]?.id ?? null;
    await refreshMessages();
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      state.book = null;
      state.selectedTopicId = null;
      state.messages = [];
    } else {
      throw err;
    }
  }
}

async function enterApp(user) {
  state.mode = 'app';
  state.view = 'discussion';
  state.user = user;
  state.replyTo = null;
  state.editingMessageId = null;
  state.loginError = null;
  state.actionError = null;
  await loadBook();
  render();
}

function loginErrorMessage(err) {
  if (err instanceof ApiError && err.status === 401) return 'Invalid access code.';
  if (err instanceof ApiError) return `Login failed (status ${err.status}).`;
  return 'Could not reach the server — is the backend running?';
}

async function handleLoginSubmit(form) {
  const accessCode = form.elements.accessCode.value;
  state.loginError = null;
  try {
    const user = await api.login(accessCode);
    await enterApp(user);
  } catch (err) {
    state.loginError = loginErrorMessage(err);
    render();
  }
}

async function handleLogout() {
  try {
    await api.logout();
  } catch {
    // Best-effort: still clear the local view even if the network call fails.
  }
  state.mode = 'login';
  state.view = 'discussion';
  state.user = null;
  state.book = null;
  state.messages = [];
  state.selectedTopicId = null;
  state.replyTo = null;
  state.editingMessageId = null;
  state.profileOpen = false;
  state.loginError = null;
  state.actionError = null;
  render();
}

async function selectTopic(topicId) {
  state.selectedTopicId = topicId;
  state.replyTo = null;
  state.editingMessageId = null;
  state.actionError = null;
  await refreshMessages();
  render();
}

async function handlePostMessage(form) {
  const body = form.elements.body.value.trim();
  if (!body) return;
  const topicId = state.selectedTopicId;
  state.actionError = null;
  try {
    await api.postMessage(topicId, { body, parentId: state.replyTo?.id ?? null });
    state.replyTo = null;
    await refreshMessages();
    render();
  } catch {
    state.actionError = 'Could not post that message.';
    render();
  }
}

function handleProfileSubmit(form) {
  const displayName = form.elements.displayName.value.trim();
  const avatarKey = form.querySelector('input[name="avatarKey"]:checked')?.value ?? state.user.avatarKey;
  if (!displayName) return;
  api
    .updateMe({ displayName, avatarKey })
    .then((user) => {
      state.user = user;
      state.profileOpen = false;
      render();
    })
    .catch(() => {
      state.actionError = 'Could not update your profile.';
      state.profileOpen = false;
      render();
    });
}

function handleReplyTo(messageId) {
  const target = state.messages.find((m) => m.id === messageId);
  if (!target || target.deletedAt) return;
  state.replyTo = { id: target.id, authorName: target.authorName, snippet: snippet(target.body) };
  render();
  appRoot.querySelector('[data-form="post-message"] textarea')?.focus();
}

function startEditMessage(messageId) {
  state.editingMessageId = messageId;
  render();
  appRoot.querySelector(`[data-form="edit-message"][data-message-id="${messageId}"] textarea`)?.focus();
}

function cancelEditMessage() {
  state.editingMessageId = null;
  render();
}

async function submitEditMessage(form) {
  const messageId = Number(form.dataset.messageId);
  const body = form.elements.body.value.trim();
  if (!body) return;
  state.actionError = null;
  try {
    await api.editMessage(messageId, body);
    state.editingMessageId = null;
    await refreshMessages();
    render();
  } catch {
    state.actionError = 'Could not save your edit.';
    render();
  }
}

async function handleDeleteMessage(messageId) {
  if (!confirm('Delete this message?')) return;
  state.actionError = null;
  try {
    await api.deleteMessage(messageId);
    await refreshMessages();
    render();
  } catch {
    state.actionError = 'Could not delete that message.';
    render();
  }
}

async function refreshAdminUsers() {
  state.admin.users = await api.adminListUsers();
}

async function refreshAdminBooks() {
  state.admin.books = await api.adminListBooks();
  if (state.admin.selectedBookId == null || !state.admin.books.some((b) => b.id === state.admin.selectedBookId)) {
    state.admin.selectedBookId = state.admin.books.find((b) => b.isCurrent)?.id ?? state.admin.books[0]?.id ?? null;
  }
}

async function refreshAdminTopics(bookId) {
  state.admin.topics = bookId != null ? await api.adminListTopics(bookId) : [];
}

async function openAdmin() {
  state.view = 'admin';
  state.admin.userError = null;
  state.admin.newBookError = null;
  state.admin.newTopicError = null;
  await refreshAdminUsers();
  await refreshAdminBooks();
  await refreshAdminTopics(state.admin.selectedBookId);
  render();
}

function closeAdmin() {
  state.view = 'discussion';
  render();
}

async function selectAdminBook(bookId) {
  state.admin.selectedBookId = bookId;
  await refreshAdminTopics(bookId);
  render();
}

async function handleCreateUser(form) {
  const displayName = form.elements.displayName.value.trim();
  const avatarKey = form.elements.avatarKey.value;
  const role = form.elements.role.value;
  const accessCode = form.elements.accessCode.value.trim() || undefined;
  if (!displayName) return;
  try {
    const created = await api.adminCreateUser({ displayName, avatarKey, role, accessCode });
    state.admin.newUserCode = { displayName: created.displayName, accessCode: created.accessCode };
    state.admin.userError = null;
    await refreshAdminUsers();
    render();
  } catch (err) {
    state.admin.userError = accessCodeError(err) ?? 'Could not create that user.';
    render();
  }
}

const accessCodeError = (err) =>
  err?.status === 409 ? 'That access code is already used by another user.' : err?.status === 400 ? 'That access code is too long.' : null;

async function handleDeleteUser(userId) {
  if (!confirm('Delete this user? This cannot be undone.')) return;
  await api.adminDeleteUser(userId);
  await refreshAdminUsers();
  render();
}

async function handleResetUserCode(userId) {
  const accessCode = document.querySelector(`[data-code-for="${userId}"]`)?.value.trim() || undefined;
  try {
    const result = await api.adminResetUserCode(userId, accessCode);
    const user = state.admin.users.find((u) => u.id === userId);
    state.admin.newUserCode = { displayName: user?.displayName ?? `User #${userId}`, accessCode: result.accessCode };
    state.admin.userError = null;
  } catch (err) {
    state.admin.userError = accessCodeError(err) ?? 'Could not reset that code.';
  }
  render();
}

async function handleCreateBook(form) {
  const title = form.elements.title.value.trim();
  const author = form.elements.author.value.trim() || null;
  if (!title) return;
  try {
    await api.adminCreateBook({ title, author });
    state.admin.newBookError = null;
    await refreshAdminBooks();
    await refreshAdminTopics(state.admin.selectedBookId);
    render();
  } catch {
    state.admin.newBookError = 'Could not create that book.';
    render();
  }
}

async function handleActivateBook(bookId) {
  await api.adminActivateBook(bookId);
  await refreshAdminBooks();
  if (state.mode === 'app') await loadBook();
  render();
}

async function handleCreateTopic(form) {
  const bookId = Number(form.elements.bookId.value);
  const title = form.elements.title.value.trim();
  const position = Number(form.elements.position.value) || 0;
  if (!title) return;
  try {
    await api.adminCreateTopic({ bookId, title, position });
    state.admin.newTopicError = null;
    await refreshAdminTopics(bookId);
    if (state.book?.id === bookId) await loadBook();
    render();
  } catch {
    state.admin.newTopicError = 'Could not create that topic.';
    render();
  }
}

async function handleToggleTopicClosed(topicId, currentlyClosed) {
  await api.adminUpdateTopic(topicId, { isClosed: !currentlyClosed });
  await refreshAdminTopics(state.admin.selectedBookId);
  if (state.book) await loadBook();
  render();
}

function dismissUserCode() {
  state.admin.newUserCode = null;
  render();
}

appRoot.addEventListener('click', (event) => {
  if (event.target.classList.contains('modal-backdrop')) {
    state.profileOpen = false;
    render();
    return;
  }

  const actionEl = event.target.closest('[data-action]');
  if (!actionEl) return;

  switch (actionEl.dataset.action) {
    case 'select-topic':
      selectTopic(Number(actionEl.dataset.topicId));
      break;
    case 'reply-to':
      handleReplyTo(Number(actionEl.dataset.messageId));
      break;
    case 'cancel-reply':
      state.replyTo = null;
      render();
      break;
    case 'edit-message':
      startEditMessage(Number(actionEl.dataset.messageId));
      break;
    case 'cancel-edit':
      cancelEditMessage();
      break;
    case 'delete-message':
      handleDeleteMessage(Number(actionEl.dataset.messageId));
      break;
    case 'open-profile':
      state.profileOpen = true;
      render();
      break;
    case 'close-profile':
      state.profileOpen = false;
      render();
      break;
    case 'logout':
      handleLogout();
      break;
    case 'open-admin':
      openAdmin();
      break;
    case 'open-discussion':
      closeAdmin();
      break;
    case 'select-admin-book':
      selectAdminBook(Number(actionEl.dataset.bookId));
      break;
    case 'activate-book':
      handleActivateBook(Number(actionEl.dataset.bookId));
      break;
    case 'delete-user':
      handleDeleteUser(Number(actionEl.dataset.userId));
      break;
    case 'reset-user-code':
      handleResetUserCode(Number(actionEl.dataset.userId));
      break;
    case 'toggle-topic-closed':
      handleToggleTopicClosed(Number(actionEl.dataset.topicId), actionEl.dataset.closed === 'true');
      break;
    case 'dismiss-user-code':
      dismissUserCode();
      break;
  }
});

appRoot.addEventListener('submit', (event) => {
  event.preventDefault();
  const form = event.target.closest('form');
  if (!form) return;

  if (form.dataset.form === 'login') handleLoginSubmit(form);
  else if (form.dataset.form === 'post-message') handlePostMessage(form);
  else if (form.dataset.form === 'edit-message') submitEditMessage(form);
  else if (form.dataset.form === 'profile') handleProfileSubmit(form);
  else if (form.dataset.form === 'create-user') handleCreateUser(form);
  else if (form.dataset.form === 'create-book') handleCreateBook(form);
  else if (form.dataset.form === 'create-topic') handleCreateTopic(form);
});

async function init() {
  try {
    const user = await api.getMe();
    await enterApp(user);
  } catch {
    render();
  }
}

init();
