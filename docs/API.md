# API Contract

All request/response bodies are JSON (Jackson + `jackson-module-kotlin`). All endpoints except `POST /api/session` and static assets require a valid session cookie (`SESSIONID`, `HttpOnly`, `SameSite=Lax`); missing/invalid session → `401 Unauthorized`. Admin-only endpoints additionally require the session user's `role == "admin"`; otherwise → `403 Forbidden`.

Timestamps are ISO-8601 strings (UTC). IDs are integers unless noted.

## Auth & session

### `POST /api/session`
Log in with an access code.

Request:
```json
{ "accessCode": "string" }
```
Response `200 OK` (also sets the session cookie):
```json
{ "id": 1, "displayName": "Jane", "avatarKey": "fox", "role": "member" }
```
`401 Unauthorized` if the code doesn't match any user.

### `DELETE /api/session`
Log out (invalidates the session server-side, clears the cookie). → `204 No Content`.

### `GET /api/me`
Current user. → `200 OK` with the same shape as the login response. `401` if not logged in.

### `PATCH /api/me`
Update your own profile.

Request (all fields optional):
```json
{ "displayName": "string", "avatarKey": "string" }
```
`avatarKey` must be one of the fixed preset set (see `docs/AVATARS.md`). Invalid key → `400 Bad Request`. Response `200 OK` with the updated user, same shape as above.

## Book & topics

### `GET /api/book/current`
Response `200 OK`:
```json
{
  "id": 1,
  "title": "string",
  "author": "string | null",
  "topics": [
    { "id": 1, "title": "Chapters 1-5", "position": 0, "isClosed": false }
  ]
}
```
`404 Not Found` if no book has been activated yet.

## Messages

### `GET /api/topics/{topicId}/messages`
Flat list, ordered by `createdAt` ascending, replies included inline (client groups by `parentId`).

Response `200 OK`:
```json
[
  {
    "id": 10,
    "topicId": 1,
    "authorId": 3,
    "authorName": "Jane",
    "authorAvatar": "fox",
    "parentId": null,
    "body": "raw markdown or null if deleted",
    "bodyHtml": "sanitized rendered HTML or null if deleted",
    "createdAt": "2026-09-18T20:00:00Z",
    "editedAt": null,
    "deletedAt": null
  }
]
```
A soft-deleted message has `deletedAt` set and `body`/`bodyHtml` both `null`; the client renders it as `[deleted]`. `404` if the topic doesn't exist.

### `POST /api/topics/{topicId}/messages`
Request:
```json
{ "body": "string", "parentId": 10 }
```
`parentId` is optional (omit/null for a top-level message). If present, it must reference a message in the same topic whose own `parentId` is `null` — otherwise `400 Bad Request` (one-level threading only). If the topic `isClosed` → `409 Conflict`. Response `201 Created` with the created message (same shape as above).

### `PATCH /api/messages/{id}`
Edit your own message.

Request:
```json
{ "body": "string" }
```
Sets `editedAt`. `403 Forbidden` if not the author. `409 Conflict` if the message is already soft-deleted. Response `200 OK` with the updated message.

### `DELETE /api/messages/{id}`
Soft-deletes your own message (sets `deletedAt`, clears `body`). `403 Forbidden` if not the author. → `204 No Content`.

## Admin — Users

### `GET /api/admin/users`
_Added in Wave 2 — the admin UI needs a way to list existing users, which the original contract omitted._

Response `200 OK`:
```json
[ { "id": 1, "displayName": "Admin", "avatarKey": "default", "role": "admin" } ]
```
Never includes access codes or hashes.

### `POST /api/admin/users`
Request (`avatarKey` must be one of the presets in `docs/AVATARS.md`, `role` one of `member`/`admin` — invalid values → `400`):
```json
{ "displayName": "string", "avatarKey": "string", "role": "member" }
```
Response `201 Created`:
```json
{ "id": 5, "displayName": "New Person", "avatarKey": "owl", "role": "member", "accessCode": "one-time-plaintext-code" }
```
`accessCode` is returned **only in this response** — it is never retrievable again (only the hash is stored).

### `DELETE /api/admin/users/{id}`
→ `204 No Content`. Also invalidates that user's sessions.

### `POST /api/admin/users/{id}/reset-code`
Generates a new access code, invalidating the old one and all of that user's active sessions.

Response `200 OK`:
```json
{ "id": 5, "accessCode": "new-one-time-plaintext-code" }
```

## Admin — Books

### `GET /api/admin/books`
_Added in Wave 2 — lists every book (not just the current one) so the admin UI can switch between them._

Response `200 OK`:
```json
[ { "id": 1, "title": "The Hobbit", "author": "J.R.R. Tolkien", "isCurrent": true, "createdAt": "2026-09-19 02:00:22" } ]
```
Ordered newest-created first.

### `POST /api/admin/books`
Request:
```json
{ "title": "string", "author": "string | null" }
```
Response `201 Created` with the created book (not activated by default — `isCurrent` implied false; use activate below).

### `POST /api/admin/books/{id}/activate`
Sets this book's `is_current = 1` and every other book's `is_current = 0` in the same operation. → `200 OK` with the activated book.

## Admin — Topics

### `GET /api/admin/books/{bookId}/topics`
_Added in Wave 2 — lets the admin UI list a specific book's topics (including one that isn't currently active) to manage them._

Response `200 OK`:
```json
[ { "id": 1, "bookId": 1, "title": "Chapters 1-5", "position": 0, "isClosed": false, "createdAt": "2026-09-19 02:00:22" } ]
```
Ordered by `position`. `404 Not Found` if the book doesn't exist.

### `POST /api/admin/topics`
Request:
```json
{ "bookId": 1, "title": "Chapters 6-10", "position": 1 }
```
Response `201 Created` with the created topic.

### `PATCH /api/admin/topics/{id}`
Request (all fields optional):
```json
{ "title": "string", "position": 2, "isClosed": true }
```
Response `200 OK` with the updated topic.

## Status code summary

| Code | Meaning |
|---|---|
| 200 | Success (read or update) |
| 201 | Resource created |
| 204 | Success, no body (logout, delete) |
| 400 | Malformed request or invalid reference (e.g. reply-to-a-reply, bad avatarKey) |
| 401 | No/invalid session |
| 403 | Authenticated but not authorized (not the author, not an admin) |
| 404 | Resource doesn't exist |
| 409 | Conflict with current state (posting to a closed topic, editing a deleted message) |
