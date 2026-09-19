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

## Wave 1 — Parallel (start once Wave 0 is merged)

### Group A — Auth & Users
- [ ] `POST /api/session` (access code login) + `DELETE /api/session` (logout) + auth plugin
      AC: valid code → 200 + `Set-Cookie` (HttpOnly, SameSite=Lax); invalid code → 401; protected route without cookie → 401.
- [ ] `GET /api/me`, `PATCH /api/me` (display name + preset avatar)
      AC: `PATCH` updates persist across a re-`GET`; other users' names are never editable via this route.
- [ ] Admin: create user (+ one-time code), delete user, reset code
      AC: newly created code logs into a fresh session; reset code invalidates the old one immediately.

### Group B — Book & Topic
- [ ] `GET /api/book/current` (+ its topics)
      AC: returns exactly the book with `is_current = 1` and its topics ordered by `position`.
- [ ] Admin: create book, activate book (single current book enforced)
      AC: activating book B flips book A's `is_current` to 0 in the same operation (no window with two current books).
- [ ] Topic CRUD scoped to current book, open/close
      AC: closed topics are still readable but reject new `POST /messages`.

### Group C — Messages & Threading
- [ ] Create/list messages per topic with one-level `parent_id`
      AC: posting a reply whose `parentId` itself has a `parent_id` is rejected (400), not silently nested.
- [ ] Edit/soft-delete own messages only
      AC: editing/deleting another user's message → 403; soft-deleted message shows `[deleted]` client-side, body cleared server-side.
- [ ] Markdown rendering, sanitized
      AC: a message body of `<img src=x onerror=alert(1)>` renders as inert visible text in the client, never executes.

### Group D — Frontend shell (built against a fixture JSON matching docs/API.md)
- [ ] Static layout: topic sidebar + flat feed with indented one-level replies
- [ ] Theme CSS from `docs/theme.png` palette (cream bg, burnt orange/amber accents, warm brown, olive, charcoal text)
- [ ] Login screen + profile editor (name + preset avatar picker)
- [ ] Composer with reply-to affordance
      AC: renders correctly against the static fixture with zero backend running.

### Group E — Deployment
- [ ] `Dockerfile` (single-stage, JRE runtime only)
- [ ] `docker-compose.yml`: named volume for the SQLite file, `restart: unless-stopped`
      AC: `docker compose down && docker compose up` (no volume removal) preserves all data; `docker compose build` followed by `up` also preserves data.
- [ ] First-run admin bootstrap (seed admin + print one-time access code to logs when `user` table is empty)
      AC: fresh volume → container logs contain a usable access code on first boot; second boot does not reseed.

## Wave 2 — Integration
- [ ] Wire Group D frontend to real API; remove fixture
- [ ] Admin web UI: users/codes, current book, topics
      AC: every admin action from Group A/B endpoints is reachable through the UI, no direct DB/API access needed.
- [ ] Manual end-to-end pass: 2 users log in, post/reply/edit/delete, admin switches books
      AC: full flow completes with no console errors and correct data after a page refresh.

## Wave 3 — Hardening & acceptance
- [ ] Security pass: hashed codes at rest, cookie flags, admin routes role-gated, XSS test re-verified
- [ ] `./gradlew ktlintFormat` clean, `./gradlew test` green
- [ ] Reboot test: restart the host (or `docker compose restart`) — site comes back up automatically with prior data intact

## Final verification (run once Wave 3 is checked off)
1. `./gradlew test` and `./gradlew ktlintFormat` clean.
2. Exercise every `docs/API.md` endpoint with `curl`, including the 401/403/400 paths, not just the happy path.
3. `docker compose up`, register via the seeded admin code, create a book + topics, post/reply/edit/delete as two different logged-in users (two browser sessions/incognito).
4. Data-durability check: `docker compose down` (no `-v`) then `up` again — discussions still present. Then `docker compose build` then `up` — still present, proving the DB lives on the volume, not the image layer.
5. XSS check: post a message body containing `<img src=x onerror=alert(1)>` and confirm it renders as literal text, not a script execution.
