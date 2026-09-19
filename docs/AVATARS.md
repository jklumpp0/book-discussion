# Preset Avatars

Users pick from a small fixed set of preset avatars — no image upload, no external URL (see `plans/ARCHITECTURE.md`'s locked decisions). The canonical list of valid `avatarKey` values is:

| Key | Glyph |
|---|---|
| `fox` | 🦊 |
| `owl` | 🦉 |
| `deer` | 🦌 |
| `bear` | 🐻 |
| `raccoon` | 🦝 |
| `hedgehog` | 🦔 |

This list must stay in sync in exactly two places:
- Backend: `src/main/kotlin/com/octoberdiscussion/auth/Avatars.kt` (`AVATAR_KEYS`) — the source of truth for validation. `PATCH /api/me` and `POST /api/admin/users` both reject any `avatarKey` not in this list with `400 Bad Request`.
- Frontend: `src/main/resources/static/js/avatars.js` (`AVATAR_KEYS`, `avatarGlyph`) — drives the profile editor's picker and renders each user's glyph next to their messages.

## The `"default"` fallback

`schema.sql`'s `user.avatar_key` column defaults to `'default'`, which is **not** one of the six preset keys above — it exists only so a row can never have a null avatar. Any user created before this feature existed, or seeded by `AdminBootstrapRunner` on first boot, will have `avatarKey: "default"` until they (or an admin, via `PATCH`) set a real preset. The frontend's `avatarGlyph()` falls back to 🍂 for any unrecognized key, so this renders sensibly without needing special-casing.
