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
- Known gap: `docs/API.md` references a `docs/AVATARS.md` file "once Group D defines it" — that file was never created; the canonical avatar list currently lives only in `Avatars.kt` (backend) and `js/avatars.js` (frontend, independently matching). Consider adding `docs/AVATARS.md` or removing the dangling reference.
- Known gap: bootstrap-seeded/pre-feature users get `avatarKey: "default"`, which is intentionally *not* in the preset list — frontend must render a fallback for unrecognized keys (confirmed Group D's picker doesn't yet handle this case explicitly).

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

## Wave 2 — Integration (current focus)
- [ ] Wire Group D frontend to real API; remove fixture
      Specifically: replace the fixture-loading in `enterApp()` with real calls to `getCurrentBook()`, `getMessages()`, `postMessage()`, `updateMe()` (all already written in `js/api.js`, just unused); remove `js/fixture.js` import and the "View demo (no backend needed)" button from the login screen (per Group D's report, that button should not ship past Wave 1).
- [ ] Admin web UI: users/codes, current book, topics
      AC: every admin action from Group A/B endpoints (`POST/DELETE /api/admin/users`, `.../reset-code`, `POST /api/admin/books`, `.../activate`, `POST/PATCH /api/admin/topics`) is reachable through the UI, no direct DB/API access needed.
- [ ] Manual end-to-end pass: 2 users log in, post/reply/edit/delete, admin switches books
      AC: full flow completes with no console errors and correct data after a page refresh.
- [ ] Resolve the `docs/AVATARS.md` dangling reference (either create the file or update `docs/API.md`'s pointer).

## Wave 3 — Hardening & acceptance
- [x] Security pass: hashed codes at rest, cookie flags, admin routes role-gated, XSS test re-verified — done as part of Wave 1 verification (BCrypt hashing, HttpOnly/SameSite cookie, `requireAdmin()` gating, live XSS re-check all confirmed above); re-run once more after Wave 2 changes land.
- [x] `./gradlew ktlintFormat` clean, `./gradlew test` green — currently true on `main` (41/41 tests); re-verify after each Wave 2 change.
- [ ] Reboot test: restart the host (or `docker compose restart`) — site comes back up automatically with prior data intact. **Not yet run for real** (see Group E's known gap above) — requires an environment with a Docker daemon.

## Final verification (run once Wave 3 is checked off)
1. `./gradlew test` and `./gradlew ktlintFormat` clean.
2. Exercise every `docs/API.md` endpoint with `curl`, including the 401/403/400 paths, not just the happy path.
3. `docker compose up`, register via the seeded admin code, create a book + topics, post/reply/edit/delete as two different logged-in users (two browser sessions/incognito).
4. Data-durability check: `docker compose down` (no `-v`) then `up` again — discussions still present. Then `docker compose build` then `up` — still present, proving the DB lives on the volume, not the image layer.
5. XSS check: post a message body containing `<img src=x onerror=alert(1)>` and confirm it renders as literal text, not a script execution.
