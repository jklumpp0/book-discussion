import { AVATAR_KEYS, avatarGlyph } from './avatars.js';

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const escapeHtml = (str) => String(str).replace(/[&<>"']/g, (c) => ESCAPES[c]);

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});
export const formatTime = (iso) => timeFormatter.format(new Date(iso));

export const snippet = (text, maxLength = 60) => {
  const plain = text ?? '';
  return plain.length > maxLength ? `${plain.slice(0, maxLength)}…` : plain;
};

export function renderLogin({ error } = {}) {
  return `
    <div class="login-screen">
      <div class="login-card">
        <h1>October Discussion</h1>
        <p class="tagline">A cozy place to talk about the book.</p>
        <form data-form="login">
          <label for="access-code">Access code</label>
          <input id="access-code" name="accessCode" type="password" autocomplete="current-password" required />
          <button type="submit">Log in</button>
        </form>
        ${error ? `<p class="form-error">${escapeHtml(error)}</p>` : ''}
        <button class="link-button" type="button" data-action="demo-login">View demo (no backend needed)</button>
      </div>
    </div>
  `;
}

function renderMessage(message, { isTopLevel }) {
  if (message.deletedAt) {
    return `
      <article class="message message-deleted" data-message-id="${message.id}">
        <div class="message-avatar">🍂</div>
        <div class="message-content">
          <div class="message-meta">
            <span class="message-author">${escapeHtml(message.authorName)}</span>
            <time class="message-time">${formatTime(message.createdAt)}</time>
          </div>
          <p class="message-body deleted-placeholder">[deleted]</p>
        </div>
      </article>
    `;
  }

  return `
    <article class="message" data-message-id="${message.id}">
      <div class="message-avatar">${avatarGlyph(message.authorAvatar)}</div>
      <div class="message-content">
        <div class="message-meta">
          <span class="message-author">${escapeHtml(message.authorName)}</span>
          <time class="message-time">${formatTime(message.createdAt)}</time>
          ${message.editedAt ? '<span class="message-edited">(edited)</span>' : ''}
        </div>
        <div class="message-body">${message.bodyHtml}</div>
        ${isTopLevel ? `<button class="reply-button" type="button" data-action="reply-to" data-message-id="${message.id}">Reply</button>` : ''}
      </div>
    </article>
  `;
}

export function renderMessageFeed(messages, { isClosed } = {}) {
  if (messages.length === 0) {
    return `<p class="empty-feed">${isClosed ? 'No messages in this topic.' : 'No messages yet — be the first to post.'}</p>`;
  }

  const topLevel = messages
    .filter((m) => !m.parentId)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

  return topLevel
    .map((top) => {
      const replies = messages
        .filter((m) => m.parentId === top.id)
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      const repliesHtml = replies.length
        ? `<div class="replies">${replies.map((r) => renderMessage(r, { isTopLevel: false })).join('')}</div>`
        : '';
      return `<div class="message-thread">${renderMessage(top, { isTopLevel: true })}${repliesHtml}</div>`;
    })
    .join('');
}

export function renderTopicList(topics, selectedTopicId) {
  return `
    <ul class="topic-list">
      ${topics
        .map(
          (topic) => `
        <li>
          <button
            type="button"
            class="topic-item ${topic.id === selectedTopicId ? 'active' : ''} ${topic.isClosed ? 'closed' : ''}"
            data-action="select-topic"
            data-topic-id="${topic.id}"
          >
            <span class="topic-title">${escapeHtml(topic.title)}</span>
            ${topic.isClosed ? '<span class="closed-badge" title="Closed to new messages">🔒</span>' : ''}
          </button>
        </li>
      `,
        )
        .join('')}
    </ul>
  `;
}

export function renderComposer({ topic, replyTo }) {
  if (topic.isClosed) {
    return '<div class="composer closed-note">This topic is closed to new messages.</div>';
  }

  return `
    <form class="composer" data-form="post-message">
      ${
        replyTo
          ? `<div class="reply-banner">
              Replying to <strong>${escapeHtml(replyTo.authorName)}</strong>:
              <span class="reply-snippet">${escapeHtml(replyTo.snippet)}</span>
              <button type="button" class="cancel-reply" data-action="cancel-reply">✕</button>
            </div>`
          : ''
      }
      <textarea
        name="body"
        placeholder="Write a message… supports **bold**, _italic_, [links](url), &gt; quotes"
        required
      ></textarea>
      <div class="composer-actions">
        <span class="markdown-hint">**bold** &nbsp; _italic_ &nbsp; [text](url) &nbsp; &gt; quote</span>
        <button type="submit">Post</button>
      </div>
    </form>
  `;
}

export function renderProfileModal(user) {
  return `
    <div class="modal-backdrop" data-action="close-profile-backdrop">
      <div class="modal profile-modal">
        <h2>Edit profile</h2>
        <form data-form="profile">
          <label for="profile-display-name">Display name</label>
          <input id="profile-display-name" name="displayName" value="${escapeHtml(user.displayName)}" required />
          <fieldset class="avatar-picker">
            <legend>Avatar</legend>
            ${AVATAR_KEYS.map(
              (key) => `
              <label class="avatar-option ${key === user.avatarKey ? 'selected' : ''}">
                <input type="radio" name="avatarKey" value="${key}" ${key === user.avatarKey ? 'checked' : ''} />
                <span class="avatar-glyph">${avatarGlyph(key)}</span>
                <span class="avatar-label">${key}</span>
              </label>
            `,
            ).join('')}
          </fieldset>
          <div class="modal-actions">
            <button type="button" data-action="close-profile">Cancel</button>
            <button type="submit">Save</button>
          </div>
        </form>
      </div>
    </div>
  `;
}

export function renderApp(state) {
  const { user, book, selectedTopicId, replyTo } = state;
  const topic = book.topics.find((t) => t.id === selectedTopicId);
  const messages = state.messagesByTopic[selectedTopicId] ?? [];

  return `
    <div class="app-shell">
      <header class="app-header">
        <div class="book-info">
          <h1>${escapeHtml(book.title)}</h1>
          ${book.author ? `<span class="book-author">${escapeHtml(book.author)}</span>` : ''}
        </div>
        <div class="user-info">
          <button class="avatar-button" type="button" data-action="open-profile">
            <span class="avatar-glyph">${avatarGlyph(user.avatarKey)}</span> ${escapeHtml(user.displayName)}
          </button>
          <button type="button" data-action="logout">Log out</button>
        </div>
      </header>
      <div class="app-body">
        <nav class="topic-sidebar">${renderTopicList(book.topics, selectedTopicId)}</nav>
        <main class="message-pane">
          <div class="message-feed">${renderMessageFeed(messages, { isClosed: topic?.isClosed })}</div>
          ${topic ? renderComposer({ topic, replyTo }) : ''}
        </main>
      </div>
    </div>
    ${state.profileOpen ? renderProfileModal(user) : ''}
  `;
}
