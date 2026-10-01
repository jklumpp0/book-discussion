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
      </div>
    </div>
  `;
}

function renderEditForm(message) {
  return `
    <form class="edit-message-form" data-form="edit-message" data-message-id="${message.id}">
      <textarea name="body" required>${escapeHtml(message.body)}</textarea>
      <div class="composer-actions">
        <button type="button" data-action="cancel-edit">Cancel</button>
        <button type="submit">Save</button>
      </div>
    </form>
  `;
}

function renderMessage(message, { isTopLevel, currentUserId, editingMessageId }) {
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

  const isOwn = message.authorId === currentUserId;
  const isEditing = message.id === editingMessageId;

  const actions = [];
  if (isTopLevel) {
    actions.push('<button class="reply-button" type="button" data-action="reply-to" data-message-id="' + message.id + '">Reply</button>');
  }
  if (isOwn && !isEditing) {
    actions.push('<button class="edit-button" type="button" data-action="edit-message" data-message-id="' + message.id + '">Edit</button>');
    actions.push('<button class="delete-button" type="button" data-action="delete-message" data-message-id="' + message.id + '">Delete</button>');
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
        ${isEditing ? renderEditForm(message) : `<div class="message-body">${message.bodyHtml}</div>`}
        ${!isEditing && actions.length ? `<div class="message-actions">${actions.join('')}</div>` : ''}
      </div>
    </article>
  `;
}

export function renderMessageFeed(messages, { isClosed, currentUserId, editingMessageId } = {}) {
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
      const opts = { currentUserId, editingMessageId };
      const repliesHtml = replies.length
        ? `<div class="replies">${replies.map((r) => renderMessage(r, { ...opts, isTopLevel: false })).join('')}</div>`
        : '';
      return `<div class="message-thread">${renderMessage(top, { ...opts, isTopLevel: true })}${repliesHtml}</div>`;
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
              <label class="avatar-option">
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

export function renderAdminPanel(admin) {
  const { users, books, selectedBookId, topics, newUserCode, userError, newBookError, newTopicError } = admin;
  const selectedBook = books.find((b) => b.id === selectedBookId);

  return `
    <div class="admin-panel">
      <section class="admin-section">
        <h2>Users</h2>
        ${
          newUserCode
            ? `<p class="one-time-code">
                New access code for <strong>${escapeHtml(newUserCode.displayName)}</strong>: <code>${escapeHtml(newUserCode.accessCode)}</code>
                — save this now, it won't be shown again.
                <button type="button" data-action="dismiss-user-code">Dismiss</button>
              </p>`
            : ''
        }
        <form class="admin-inline-form" data-form="create-user">
          <input name="displayName" placeholder="Display name" required />
          <select name="avatarKey">${AVATAR_KEYS.map((key) => `<option value="${key}">${key}</option>`).join('')}</select>
          <select name="role">
            <option value="member">member</option>
            <option value="admin">admin</option>
          </select>
          <input name="accessCode" placeholder="Access code (optional)" autocomplete="off" />
          <button type="submit">Add user</button>
        </form>
        ${userError ? `<p class="form-error">${escapeHtml(userError)}</p>` : ''}
        <ul class="admin-list">
          ${users
            .map(
              (u) => `
            <li>
              <span class="admin-item-avatar">${avatarGlyph(u.avatarKey)}</span>
              <span class="admin-item-name">${escapeHtml(u.displayName)}</span>
              <span class="admin-item-role">${escapeHtml(u.role)}</span>
              <input class="admin-code-input" data-code-for="${u.id}" placeholder="New code (optional)" autocomplete="off" />
              <button type="button" data-action="reset-user-code" data-user-id="${u.id}">Reset code</button>
              <button type="button" data-action="delete-user" data-user-id="${u.id}">Delete</button>
            </li>
          `,
            )
            .join('')}
        </ul>
      </section>

      <section class="admin-section">
        <h2>Books</h2>
        <form class="admin-inline-form" data-form="create-book">
          <input name="title" placeholder="Book title" required />
          <input name="author" placeholder="Author (optional)" />
          <button type="submit">Add book</button>
        </form>
        ${newBookError ? `<p class="form-error">${escapeHtml(newBookError)}</p>` : ''}
        <ul class="admin-list">
          ${books
            .map(
              (b) => `
            <li class="${b.id === selectedBookId ? 'selected' : ''}">
              <button type="button" class="admin-list-select" data-action="select-admin-book" data-book-id="${b.id}">
                ${escapeHtml(b.title)} ${b.isCurrent ? '<span class="current-badge">current</span>' : ''}
              </button>
              ${!b.isCurrent ? `<button type="button" data-action="activate-book" data-book-id="${b.id}">Make current</button>` : ''}
            </li>
          `,
            )
            .join('')}
        </ul>
      </section>

      ${
        selectedBook
          ? `
        <section class="admin-section">
          <h2>Topics for "${escapeHtml(selectedBook.title)}"</h2>
          <form class="admin-inline-form" data-form="create-topic">
            <input type="hidden" name="bookId" value="${selectedBook.id}" />
            <input name="title" placeholder="Topic title" required />
            <input name="position" type="number" value="${topics.length}" />
            <button type="submit">Add topic</button>
          </form>
          ${newTopicError ? `<p class="form-error">${escapeHtml(newTopicError)}</p>` : ''}
          <ul class="admin-list">
            ${topics
              .map(
                (t) => `
              <li>
                <span>${escapeHtml(t.title)} (position ${t.position})</span>
                <button type="button" data-action="toggle-topic-closed" data-topic-id="${t.id}" data-closed="${t.isClosed}">
                  ${t.isClosed ? 'Reopen' : 'Close'}
                </button>
              </li>
            `,
              )
              .join('')}
          </ul>
        </section>
      `
          : ''
      }
    </div>
  `;
}

export function renderApp(state) {
  const { user, book, selectedTopicId, replyTo, editingMessageId, view, admin, actionError } = state;
  const topic = book?.topics.find((t) => t.id === selectedTopicId);
  const messages = state.messages;

  const mainContent = !book
    ? `<div class="empty-state">
        <p>No book has been set up yet.</p>
        <p>${user.role === 'admin' ? 'Use the admin panel to create one.' : 'Ask an admin to set one up.'}</p>
      </div>`
    : `<div class="app-body">
        <nav class="topic-sidebar">${renderTopicList(book.topics, selectedTopicId)}</nav>
        <main class="message-pane">
          ${actionError ? `<p class="form-error">${escapeHtml(actionError)}</p>` : ''}
          <div class="message-feed">${renderMessageFeed(messages, { isClosed: topic?.isClosed, currentUserId: user.id, editingMessageId })}</div>
          ${topic ? renderComposer({ topic, replyTo }) : ''}
        </main>
      </div>`;

  return `
    <div class="app-shell">
      <header class="app-header">
        <div class="book-info">
          <h1>${book ? escapeHtml(book.title) : 'October Discussion'}</h1>
          ${book?.author ? `<span class="book-author">${escapeHtml(book.author)}</span>` : ''}
        </div>
        <div class="user-info">
          ${
            user.role === 'admin'
              ? `<button type="button" data-action="${view === 'admin' ? 'open-discussion' : 'open-admin'}">${
                  view === 'admin' ? 'Back to discussion' : 'Admin panel'
                }</button>`
              : ''
          }
          <button class="avatar-button" type="button" data-action="open-profile">
            <span class="avatar-glyph">${avatarGlyph(user.avatarKey)}</span> ${escapeHtml(user.displayName)}
          </button>
          <button type="button" data-action="logout">Log out</button>
        </div>
      </header>
      ${view === 'admin' ? renderAdminPanel(admin) : mainContent}
    </div>
    ${state.profileOpen ? renderProfileModal(user) : ''}
  `;
}
