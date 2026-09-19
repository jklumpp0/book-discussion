# October Discussion — Build Checklist

See `plans/ARCHITECTURE.md` for the full architecture, data model, API contract, and locked decisions this checklist implements. Check items off as they land; each item's acceptance criteria (AC) is how to verify it's actually done, not just started.

## Wave 0 — Foundation (serial, blocks everything else)
- [x] Gradle Kotlin DSL skeleton + `gradle/libs.versions.toml` + Spring Boot (WebFlux + coroutines) hello-world; ktlint + JUnit5 (MockK, not Mockito) wired
      AC: `./gradlew test` passes; `./gradlew ktlintCheck` passes; server starts and responds 200 on a health route. — Verified: wrapper pinned to Gradle 8.11.1 (JDK 21 toolchain), `./gradlew ktlintCheck test` green, `./gradlew bootRun` serves `GET /health` → `200 OK`.
- [x] `src/main/resources/schema.sql` written in full (user, session, book, topic, message)
      AC: starting the app against an empty DB file has Spring Boot's SQL init auto-create all 5 tables with no errors (no custom bootstrap code required for this part). — Verified: fresh `bootRun` created `data/october-discussion.db` with all 5 tables present.
- [x] `docs/API.md` written in full (every endpoint, method, path, request/response JSON, status codes)
      AC: every endpoint referenced by Waves 1-2 below is documented before those waves start.
- [x] Commit `plans/ARCHITECTURE.md` and this `plans/CHECKLIST.md` to the repo.

## Wave 1 — Parallel (start once Wave 0 is merged) — ✅ COMPLETE, merged to `main`

All five groups were implemented in parallel (isolated git worktrees), merged with zero file conflicts, and the full suite (41 tests) is green post-merge. Additionally verified live: booted the real app, logged in via the seeded admin code through the actual browser UI, exercised the real API end-to-end with `curl` (create/activate book, create topic, post/list messages), and confirmed the XSS payload is escaped in `bodyHtml`.

### Group A — Auth & Users — ✅ done (commits `9e85a88`, `f4a9755`)
- [x] `POST /api/session` (access code login) + `DELETE /api/session` (logout) + auth plugin
      AC met: valid code → 200 + `Set-Cookie` (HttpOnly, SameSite=Lax) — verified live in Chrome; invalid code → 401; protected route without cookie → 401 (tested).
- [x] `GET /api/me`, `PATCH /api/me` (display name + preset avatar)
      AC met: `PATCH` persists across re-`GET`. Preset avatar list: `fox, owl, deer, bear, raccoon, hedgehog`.
- [x] Admin: create user (+ one-time code), delete user, reset code
      AC met: reset code invalidates prior sessions immediately (tested).
- ~~Known gap: `docs/AVATARS.md` dangling reference~~ — **resolved**: `docs/AVATARS.md` created, documenting the canonical list and the `avatar_key` column's `'default'` fallback; `docs/API.md`'s `PATCH /api/me` section now points to it correctly.
- Known gap (still open, low priority): bootstrap-seeded/pre-feature users get `avatarKey: "default"`, which is intentionally *not* in the preset list. The frontend's `avatarGlyph()` already falls back to a generic glyph for any unrecognized key (confirmed in `js/avatars.js`), so this renders fine — but such a user has no obvious visual cue prompting them to pick a real avatar. Cosmetic only.

### Group B — Book & Topic — ✅ done (commits `8b9c338`, `cddc107`)
- [x] `GET /api/book/current` (+ its topics)
      AC met: verified live via `curl` — returns exactly the current book, topics ordered by `position`.
- [x] Admin: create book, activate book (single current book enforced)
      AC met: activation uses a `TransactionTemplate`-wrapped transaction — no window with two/zero current books.
- [x] Topic CRUD scoped to current book, open/close
      AC met: closed topic still readable (verified live — shows lock icon, "closed" state).
- Known gap: `POST /api/admin/topics` does not validate that `bookId` refers to an existing book (SQLite FK enforcement is off; `docs/API.md` specifies no status code for this case). Low priority given admin-only usage at this scale.

### Group C — Messages & Threading — ✅ done (commits `25fe5a7`, `292630d`)
- [x] Create/list messages per topic with one-level `parent_id`
      AC met: reply-to-a-reply rejected with 400 (tested).
- [x] Edit/soft-delete own messages only
      AC met: 403 for non-authors (tested); soft-deleted message shows `[deleted]`, body/bodyHtml null in the API response.
- [x] Markdown rendering, sanitized
      AC met and independently re-verified live via `curl`: posting `<img src=x onerror=alert(1)>` produces `bodyHtml` with `&lt;img src=x onerror=alert(1)&gt;` — confirmed inert, not executable.
- Note: `bodyHtml` is computed on read/write, not persisted (schema has no `body_html` column) — fine at this scale, flagged for awareness.

### Group D — Frontend shell — ✅ done (commit `90f5d88`)
- [x] Static layout: topic sidebar + flat feed with indented one-level replies — verified live in Chrome, including a reply nested under a soft-deleted parent.
- [x] Theme CSS from `docs/theme.png` palette — verified live, matches the autumn palette.
- [x] Login screen + profile editor (name + preset avatar picker) — verified live; avatar list matches Group A's exactly.
- [x] Composer with reply-to affordance — verified live (reply banner + cancel works).
      AC met: renders correctly against the static fixture with zero backend running (demo mode), and separately confirmed against the real backend for login/logout.
- **Important carry-forward for Wave 2**: only `POST/DELETE /api/session` are wired to the real API. `GET /api/book/current`, `GET/POST /api/topics/{id}/messages`, and `PATCH /api/me` are implemented in `js/api.js` but never called — `enterApp()` always loads fixture data regardless of real login. This is the exact Wave 2 swap-in point (see below).
- Follow-up fix (post-merge, user-reported): the header's "Admin" and "Log out" buttons had mismatched heights (the avatar emoji's larger font-size inflated line-height). Fixed in commit `dcb41e2` with an explicit shared button height; a UI-conventions rule was added to `CLAUDE.md` (equal-height buttons in a group, prefer wider over taller).

### Group E — Deployment — ✅ done (commit `0f592d8`)
- [x] `Dockerfile` (multi-stage: JDK build stage, JRE runtime stage)
- [x] `docker-compose.yml`: named volume for the SQLite file, `restart: unless-stopped`
- [x] First-run admin bootstrap (seed admin + print one-time access code to logs when `user` table is empty)
      AC met: verified live — fresh boot logged a usable code, second boot did not reseed. Also independently re-verified in this session with a fresh DB.
- **Known gap**: Docker itself was never actually built/run (no Docker daemon available in the agent's sandbox) — Group E instead verified the equivalent native `bootJar` + `java -jar` flow. **The `docker compose up`/durability/reboot acceptance criteria in Wave 3 and Final Verification below are still unverified and must be run for real**, ideally on the target host or any machine with Docker available.

## Wave 2 — Integration — ✅ COMPLETE (commits `30f0eb6`, `2a2fd13`)

Also added `GET /api/admin/users`, `GET /api/admin/books`, `GET /api/admin/books/{bookId}/topics` (missing from the original contract — an admin UI can't manage what it can't list) and the message edit/delete UI, which wasn't explicitly scoped to any Wave 1 group but is required by this wave's own "post/reply/edit/delete" acceptance criterion.

- [x] Wire Group D frontend to real API; remove fixture
      AC met: `js/fixture.js` deleted, demo-mode button removed, `enterApp()` now calls the real `getCurrentBook()`/`getMessages()`/`postMessage()`/`updateMe()`. Gracefully handles the "no book created yet" 404 with an empty-state screen instead of assuming a book always exists.
- [x] Admin web UI: users/codes, current book, topics
      AC met: verified live — created a user (one-time code shown), created and activated a book, created two topics, closed/reopened a topic (composer correctly disabled/enabled), reset a user's code (old code + old session both correctly killed), deleted a user. Every admin action is reachable through the UI.
- [x] Manual end-to-end pass: 2 users log in, post/reply/edit/delete, admin switches books
      AC met: verified live in two separate logged-in browser tabs (Admin + a real second user "Priya") — posted, replied (one-level threading rendered correctly indented), edited (with "(edited)" marker), and soft-deleted (shows "[deleted]") messages; confirmed Priya sees no Edit/Delete on Admin's messages (UI-hidden) and confirmed via direct `curl` with her session cookie that the server also rejects it with `403` (not just UI hiding); zero console errors throughout.
      Bonus verification beyond the stated AC: re-ran the XSS payload (`<img src=x onerror=alert(1)>`) through the real posting UI — rendered as inert text, no alert fired.
- **Gotcha hit and noted for future browser-automation sessions**: the delete-message/delete-user handlers use native `confirm()`. Clicking Delete via Chrome automation froze the tab (native dialogs block CDP input/eval) until manually dismissed. Not an app bug — real users clicking normally are unaffected — but avoid scripting clicks on `confirm()`-guarded buttons in future automated passes; verify those via `curl` instead (as done here for delete-user/reset-code).
- [x] Resolve the `docs/AVATARS.md` dangling reference — created the file; `docs/API.md` updated to point to it.

## Wave 3 — Hardening & acceptance
- [x] Security pass: hashed codes at rest, cookie flags, admin routes role-gated, XSS test re-verified — re-verified after Wave 2 landed: server-side `403` confirmed via `curl` (not just UI hiding) for both non-author edit/delete and non-admin admin-route access; XSS payload re-checked through the real posting UI end-to-end.
- [x] `./gradlew ktlintFormat` clean, `./gradlew test` green — currently true on `main` (47/47 tests, up from 41 after Wave 2's new admin-list-endpoint tests).
- [ ] Reboot test: restart the host (or `docker compose restart`) — site comes back up automatically with prior data intact. **Still not run for real** — no Docker daemon has been available in any environment used so far this session either. This is the one remaining item before the project can be called fully done; needs a machine with Docker.

## Final verification (run once Wave 3 is checked off)
1. `./gradlew test` and `./gradlew ktlintFormat` clean. — ✅ done, 47/47 passing.
2. Exercise every `docs/API.md` endpoint with `curl`, including the 401/403/400 paths, not just the happy path. — ✅ done across Wave 1 and Wave 2 verification passes (see above); every endpoint has been curled at least once including its error paths.
3. `docker compose up`, register via the seeded admin code, create a book + topics, post/reply/edit/delete as two different logged-in users (two browser sessions/incognito). — ✅ functionally done via `bootRun` + two real Chrome tabs (not literally through `docker compose`, since Docker wasn't available — see item 4).
4. Data-durability check: `docker compose down` (no `-v`) then `up` again — discussions still present. Then `docker compose build` then `up` — still present, proving the DB lives on the volume, not the image layer. — ⬜ **not done** — requires an actual Docker daemon, unavailable in every environment used so far.
5. XSS check: post a message body containing `<img src=x onerror=alert(1)>` and confirm it renders as literal text, not a script execution. — ✅ done, twice (curl and live browser UI).

**Bottom line: the only remaining work on this checklist is the Docker-dependent durability/reboot verification (Wave 3 item 3, Final Verification item 4) — everything else is implemented, merged, and verified.** Run those on a machine with Docker available.
