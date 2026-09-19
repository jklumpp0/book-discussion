# Book Club Discussion Site — Architecture & Decisions

## Context

A small, self-hosted discussion site for a book club: one book "active" at a time, discussed in topics (e.g. "Chapters 1-5", "Epilogue"). Each topic is a single flat discussion feed with one level of threaded replies, fully visible (no collapsed threads) — matching `docs/channel-discussion-layout.png` (Slack-style: sidebar of topics, main pane of messages with indented replies). Visual theme draws from `docs/theme.png`: cream background, burnt orange/amber accents, warm browns, muted olive, dark charcoal text.

Scale is 10s of users, fixed/admin-managed membership, no self-signup. Priorities are simplicity to run/maintain and durability across reboots, not scale or real-time chat feel.

## Locked decisions

| Decision | Choice |
|---|---|
| Deployment | Docker / docker-compose, `restart: unless-stopped`, SQLite file on a named volume |
| Login | Simple shared access code per user (not WebAuthn), hashed at rest, opaque session cookie |
| Live updates | None — manual page refresh |
| Avatar | Small preset avatar set (no upload, no external URL) |
| Thread depth | One level only — a message's `parent_id` may only point at a top-level message |
| Edit/delete | Both allowed, own messages only; delete is soft-delete (`[deleted]`) |
| Admin workflow | In-app admin web UI (manage users/access codes, set current book, add/close topics) |
| Message formatting | Basic Markdown (bold/italic/links/blockquote), rendered safely (no raw HTML injection) |
| Frontend | Plain JS + ES modules + CSS, served as static files directly by Spring Boot — no Node/npm build step |
| Web framework | Spring Boot with Spring WebFlux + Kotlin coroutines (not Ktor — user override of CLAUDE.md's default; WebFlux keeps the coroutine/non-blocking-handler rule intact) |
| JSON serialization | Jackson + `jackson-module-kotlin` (Spring's own auto-configured default; deliberate departure from CLAUDE.md's kotlinx.serialization default, since fighting Spring's default converter wiring buys nothing at this scale) |
| Data access | Plain JDBC via `JdbcTemplate`/`R2dbcEntityTemplate`-free hand-written SQL against `schema.sql` (not Spring Data JPA/Hibernate — keeps the 5-table schema as the single source of truth, matches CLAUDE.md's anti-ORM guidance) |

Stack defaults (per `CLAUDE.md`, with the Spring Boot substitutions above): coroutines throughout (no blocking I/O in handlers — DB calls via `Dispatchers.IO`, which matters even more under WebFlux since blocking a Netty/Reactor event-loop thread stalls the whole server, not just one request), kotlin-logging (SLF4J wrapper, compatible with Spring Boot's default Logback), kotlin.test + JUnit5 (Spring Boot's `spring-boot-starter-test` bundles JUnit5 already; exclude its default Mockito and add MockK per CLAUDE.md), feature-first packages under `com.octoberdiscussion.<feature>`.

**Note:** Spring Boot auto-executes `src/main/resources/schema.sql` against the configured datasource on startup (via its default SQL init behavior) — no custom bootstrap code needed to create tables, which fits neatly with keeping `schema.sql` as the single canonical source of the data model.

## Data model (`src/main/resources/schema.sql`)

```sql
CREATE TABLE IF NOT EXISTS user (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  display_name TEXT NOT NULL,
  avatar_key TEXT NOT NULL DEFAULT 'default',
  access_code_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('member', 'admin')),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS session (
  id TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES user(id),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  expires_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS book (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  author TEXT,
  is_current INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS topic (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  book_id INTEGER NOT NULL REFERENCES book(id),
  title TEXT NOT NULL,
  position INTEGER NOT NULL DEFAULT 0,
  is_closed INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS message (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  topic_id INTEGER NOT NULL REFERENCES topic(id),
  author_id INTEGER NOT NULL REFERENCES user(id),
  parent_id INTEGER REFERENCES message(id),
  body TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  edited_at TEXT,
  deleted_at TEXT
);
```

Only one `book` row should have `is_current = 1` at a time (enforced in application code when the admin switches books). `parent_id` must reference a message whose own `parent_id` is NULL — enforced in application code, not the DB, since SQLite `CHECK` can't do that lookup.

## API contract

Wave 0 must produce the full contract as `docs/API.md` — every endpoint, method, path, request/response JSON shape, and status codes — before any other work starts, since it's what lets groups A–D build in parallel without reconciliation. High-level shape to expand there:

- `POST /api/session` (body: `{accessCode}`) → 200 + `Set-Cookie` + user JSON, or 401
- `DELETE /api/session` → 204, clears cookie
- `GET /api/me` → current user JSON (401 if not logged in)
- `PATCH /api/me` (body: `{displayName?, avatarKey?}`) → updated user JSON
- `GET /api/book/current` → current book + its topics (id, title, position, isClosed)
- `GET /api/topics/{topicId}/messages` → flat list, each with `id, authorId, authorName, authorAvatar, parentId, body, bodyHtml, createdAt, editedAt, deletedAt`
- `POST /api/topics/{topicId}/messages` (body: `{body, parentId?}`) → created message
- `PATCH /api/messages/{id}` (body: `{body}`) → 200 (own messages only) or 403
- `DELETE /api/messages/{id}` → soft-delete, 204 (own messages only) or 403
- Admin-only (role check): `POST/DELETE /api/admin/users`, `POST /api/admin/users/{id}/reset-code`, `POST /api/admin/books`, `POST /api/admin/books/{id}/activate`, `POST/PATCH /api/admin/topics`

## Security notes (apply throughout, not a separate bolt-on)

- Access codes hashed at rest (e.g. BCrypt); never logged or returned after creation except the one-time value shown to the admin when generated/reset.
- Session cookie: `HttpOnly`, `SameSite=Lax`, random opaque ID (not a JWT — no need here).
- Admin routes gated by `role = 'admin'` check in a WebFilter/handler-filter-function, not by hiding the URL.
- Markdown rendering must escape/sanitize raw HTML — a message body of `<img src=x onerror=alert(1)>` must render as inert text, not execute. Verify explicitly (see acceptance criteria).
- All DB access wrapped in `withContext(Dispatchers.IO)` — never block a WebFlux coroutine handler on JDBC directly (blocking JDBC on a Reactor/Netty event-loop thread is worse than in a thread-per-request model, since it can stall unrelated in-flight requests too).

## Deployment

- Single-stage `Dockerfile`: the Spring Boot Gradle plugin's `bootJar` task produces an executable fat jar; runtime image just needs a JRE. No Node stage needed since the frontend has no build step.
- `docker-compose.yml`: one service, named volume mounted at the SQLite file's directory, `restart: unless-stopped`.
- First-run bootstrap: on startup, if `user` table is empty, seed one admin user with a randomly generated access code printed to container logs once (an `ApplicationRunner`/`CommandLineRunner` bean, or a check inside the WebFlux startup path).

## Toolchain notes (environment-specific, recorded for continuity)

- Local machine has JDK 26 (Homebrew default), JDK 26.0.1 (Oracle), and Corretto 20 installed, plus now JDK 21 (Homebrew, keg-only at `/opt/homebrew/opt/openjdk@21`) installed specifically for this project's build stability — Kotlin/Gradle/Spring Boot tooling support lags behind bleeding-edge JDKs like 26. JDK 21 (LTS) is also Spring Boot 3.x's best-supported baseline.
- Gradle itself was not installed; installed via `brew install gradle` (9.7.1) solely to bootstrap the project's Gradle wrapper. Once `./gradlew` exists, use that exclusively — do not depend on a system Gradle install.
- Wrapper should be generated pinned to Gradle 8.11.1 (not 9.x) for wider Kotlin Gradle plugin and Spring Boot Gradle plugin compatibility, run under `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- The Gradle build itself should declare a Java toolchain (`kotlin { jvmToolchain(21) }` / `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`) so the build is reproducible regardless of which JDK is the shell default on a given machine.
- Build plugins needed beyond the Kotlin ones: `org.springframework.boot` (provides `bootJar`/`bootRun`) and `io.spring.dependency-management` (or Gradle's native platform BOM import) to pull in Spring Boot's curated dependency versions — pin a current Spring Boot 3.x version compatible with Kotlin 2.x and JDK 21.
- **Gotcha (resolved):** `org.jlleitschuh.gradle.ktlint` 12.1.2 fails against Kotlin 2.1.20 with `Class org.jetbrains.kotlin.lexer.KtTokens does not have member field ... HEADER_KEYWORD` (a bundled-ktlint/Kotlin-PSI version mismatch), regardless of whether an explicit `ktlint { version.set(...) }` is set. Fixed by bumping the plugin to **14.2.0** (latest as of this build) and not pinning an explicit ktlint engine version — let the plugin use its own bundled-compatible version.
- **Gotcha (resolved):** the `org.xerial:sqlite-jdbc` driver does not create the DB file's parent directory — a fresh checkout with no `data/` directory fails at startup with `path to '...': '...' does not exist`. Fixed in `Application.kt`'s `main()` by `File(dbPath).absoluteFile.parentFile?.mkdirs()` before `runApplication(...)`, since this must happen before Spring builds the DataSource bean (too early for an `ApplicationRunner`). Not an issue in the eventual Docker/compose deployment (the named volume's mount point always exists), but needed for local `bootRun`/bare-metal use.
