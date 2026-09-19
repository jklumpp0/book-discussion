import * as api from './api.js';
import { ApiError } from './api.js';
import * as fixture from './fixture.js';
import { renderLogin, renderApp, escapeHtml, snippet } from './render.js';

const appRoot = document.getElementById('app');

const state = {
  mode: 'login',
  demo: false,
  user: null,
  book: null,
  messagesByTopic: {},
  selectedTopicId: null,
  replyTo: null,
  profileOpen: false,
  loginError: null,
};

let nextMessageId = 100000;

function render() {
  appRoot.innerHTML = state.mode === 'login' ? renderLogin({ error: state.loginError }) : renderApp(state);
  if (state.mode === 'login') appRoot.querySelector('#access-code')?.focus();
}

// The fixture stands in for Group B/C's endpoints until Wave 2 wires the real API;
// only login/logout hit the network in this build.
async function enterApp(user, { demo }) {
  state.mode = 'app';
  state.demo = demo;
  state.user = user;
  state.book = JSON.parse(JSON.stringify(fixture.book));
  state.messagesByTopic = JSON.parse(JSON.stringify(fixture.messagesByTopic));
  state.selectedTopicId = state.book.topics[0]?.id ?? null;
  state.replyTo = null;
  state.loginError = null;
  render();
}

function loginErrorMessage(err) {
  if (err instanceof ApiError && err.status === 401) return 'Invalid access code.';
  if (err instanceof ApiError) return `Login failed (status ${err.status}).`;
  return 'Could not reach the server — is the backend running? Try the demo instead.';
}

async function handleLoginSubmit(form) {
  const accessCode = form.elements.accessCode.value;
  state.loginError = null;
  try {
    const user = await api.login(accessCode);
    await enterApp(user, { demo: false });
  } catch (err) {
    state.loginError = loginErrorMessage(err);
    render();
  }
}

async function handleLogout() {
  if (!state.demo) {
    try {
      await api.logout();
    } catch {
      // No backend yet in Wave 1 — logging out of the local view still succeeds.
    }
  }
  state.mode = 'login';
  state.demo = false;
  state.user = null;
  state.book = null;
  state.messagesByTopic = {};
  state.selectedTopicId = null;
  state.replyTo = null;
  state.profileOpen = false;
  state.loginError = null;
  render();
}

function handlePostMessage(form) {
  const body = form.elements.body.value.trim();
  if (!body) return;

  const topicId = state.selectedTopicId;
  const topic = state.book.topics.find((t) => t.id === topicId);
  if (!topic || topic.isClosed) return;

  const newMessage = {
    id: nextMessageId++,
    topicId,
    authorId: state.user.id,
    authorName: state.user.displayName,
    authorAvatar: state.user.avatarKey,
    parentId: state.replyTo?.id ?? null,
    body,
    bodyHtml: `<p>${escapeHtml(body)}</p>`,
    createdAt: new Date().toISOString(),
    editedAt: null,
    deletedAt: null,
  };

  state.messagesByTopic[topicId] = [...(state.messagesByTopic[topicId] ?? []), newMessage];
  state.replyTo = null;
  render();
}

function handleProfileSubmit(form) {
  const displayName = form.elements.displayName.value.trim();
  const avatarKey = form.querySelector('input[name="avatarKey"]:checked')?.value ?? state.user.avatarKey;
  if (!displayName) return;
  state.user = { ...state.user, displayName, avatarKey };
  state.profileOpen = false;
  render();
}

function handleReplyTo(messageId) {
  const messages = state.messagesByTopic[state.selectedTopicId] ?? [];
  const target = messages.find((m) => m.id === messageId);
  if (!target || target.deletedAt) return;
  state.replyTo = { id: target.id, authorName: target.authorName, snippet: snippet(target.body) };
  render();
  appRoot.querySelector('[data-form="post-message"] textarea')?.focus();
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
    case 'demo-login':
      enterApp(fixture.demoUser, { demo: true });
      break;
    case 'select-topic':
      state.selectedTopicId = Number(actionEl.dataset.topicId);
      state.replyTo = null;
      render();
      break;
    case 'reply-to':
      handleReplyTo(Number(actionEl.dataset.messageId));
      break;
    case 'cancel-reply':
      state.replyTo = null;
      render();
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
  }
});

appRoot.addEventListener('submit', (event) => {
  event.preventDefault();
  const form = event.target.closest('form');
  if (!form) return;

  if (form.dataset.form === 'login') handleLoginSubmit(form);
  else if (form.dataset.form === 'post-message') handlePostMessage(form);
  else if (form.dataset.form === 'profile') handleProfileSubmit(form);
});

async function init() {
  try {
    const user = await api.getMe();
    await enterApp(user, { demo: false });
  } catch {
    render();
  }
}

init();
