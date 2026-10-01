# CLAUDE.md

Guidance for Claude Code when working in this Kotlin site/service — "October Discussion," a small self-hosted book-club discussion site. See `plans/ARCHITECTURE.md` for full context and the complete decision log, and `docs/API.md` for the current API contract.

## Stack (decided and in use — not a default, this is what's actually here)

- **Build**: Gradle with the Kotlin DSL (`build.gradle.kts`), version catalog in `gradle/libs.versions.toml`. Wrapper pinned to Gradle 8.11.1, JDK 21 toolchain (`kotlin { jvmToolchain(21) }`). Run everything with `JAVA_HOME` pointed at a JDK 21 install — the machine's default JDK may be newer than what the Kotlin/Spring Boot toolchain reliably supports.
- **Web framework**: **Spring Boot with Spring WebFlux + Kotlin coroutines** (`org.springframework.boot`, `io.spring.dependency-management` plugins; `spring-boot-starter-webflux`). This is a deliberate project-level choice overriding a more generic Ktor default — do not introduce Ktor alongside it. Handlers are `suspend fun` methods on `@RestController` classes (WebFlux natively supports coroutine handlers with `kotlinx-coroutines-reactor` on the classpath); prefer this annotated-controller style over the functional `coRouter` DSL, matching the existing codebase.
- **Serialization**: **Jackson + `jackson-module-kotlin`** (Spring's own auto-configured default), not kotlinx.serialization — fighting Spring's default converter wiring buys nothing at this project's scale.
- **Async**: Kotlin coroutines throughout — no callback-style or blocking I/O directly in a request handler. This matters even more here than in a typical coroutine server: blocking a WebFlux/Reactor/Netty event-loop thread stalls *other* in-flight requests too, not just the one that blocked.
- **DB access**: **Plain JDBC via `JdbcTemplate`** against a hand-written `src/main/resources/schema.sql` (SQLite) — no Spring Data JPA/Hibernate, no Exposed. `schema.sql` is the single source of truth for the data model; Spring Boot auto-executes it against the datasource on startup, so no custom bootstrap code is needed to create tables. One concrete `@Component` class per feature handles its own DB access directly — no repository-interface abstraction for a single implementation.
- **HTTP client**: not applicable yet (no outbound HTTP calls in this project).
- **Testing**: `kotlin.test` + JUnit 5, via `spring-boot-starter-test` with its default Mockito **excluded** (`exclude(group = "org.mockito")` in `build.gradle.kts`) and `io.mockk:mockk` added instead. Prefer `@SpringBootTest(webEnvironment = RANDOM_PORT)` driven with `WebTestClient` over `@WebFluxTest` — see the WebFlux gotcha below for why.
- **Logging**: `kotlin-logging` (`io.github.oshai:kotlin-logging-jvm`), a thin wrapper over SLF4J, compatible with Spring Boot's default Logback.
- **Frontend**: **plain JS + ES modules + CSS**, served as static files directly by Spring Boot from `src/main/resources/static/` — deliberately no Node/npm, no bundler, no framework, no build step. Keep it that way; a "basic React" frontend was considered and rejected specifically because it would add a Node build stage to the Docker image for no real benefit at this scale.

Prefer the standard library over a dependency when the stdlib already covers it (e.g. `Result`, `sequence {}`, `buildList`, `Duration`).

## Idiomatic Kotlin

- Prefer `val` over `var`; treat mutability as something you have to justify.
- Use data classes for plain data holders; avoid Java-style POJOs with manual getters/equals/hashCode.
- Use sealed classes/interfaces for closed sets of states or results instead of enums-with-payload or exceptions-as-control-flow.
- Use expression bodies (`fun x() = ...`) for one-line functions.
- Prefer `?.`, `?:`, and smart casts over explicit null checks and `!!`. Never use `!!` outside tests.
- Use extension functions to add behavior to a type rather than utility/helper classes with static methods.
- Use named arguments for calls with more than 2-3 params, especially booleans (`send(retry = true)`, not `send(true)`).
- Use `require`/`check`/`error` for precondition failures instead of manually throwing generic exceptions.
- Prefer collection operations (`map`, `filter`, `associateBy`, `groupBy`, `fold`) over manual loops, unless a loop is clearer for the specific case.
- Use trailing lambdas and scope functions (`let`, `apply`, `also`, `run`, `with`) only where they remove real noise — don't chain them for style points.
- Structure coroutines with structured concurrency (`coroutineScope`, `supervisorScope`); avoid `GlobalScope`.

## Comments

- Default to no comments — clear naming and small functions should carry the intent.
- Add a comment only where the *why* isn't obvious from the code: a non-obvious algorithm, a workaround for a specific library quirk, a concurrency invariant, or a business rule that isn't self-evident.
- Keep comments to one line where possible. No comment banners, no restating what the code does.

## Simplicity

- Don't add abstraction layers (repositories, interfaces, DI modules) for a single implementation "in case it changes later."
- Don't build generic/configurable solutions for a one-off need — solve the actual case in front of you.
- Keep functions small and focused; split when a function does two distinct things, not preemptively.

## Project conventions

- Package structure: `com.octoberdiscussion.<feature>`, feature-first rather than layer-first (`auth/`, `book/`, `message/`, `bootstrap/` — each owns its own DTOs, repository, and controller(s), not split across the whole app).
- Run `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH"` first if the shell's default `java` isn't already JDK 21.
- Run `./gradlew ktlintFormat` before considering a change done.
- Run `./gradlew test` before reporting a task complete.
- The frozen contracts other code depends on are `docs/API.md` (endpoint shapes/status codes), `src/main/resources/schema.sql` (data model), and `src/main/kotlin/com/octoberdiscussion/auth/CurrentUser.kt` (the `requireCurrentUser()`/`requireAdmin()` extension functions every controller uses for auth). Treat changes to these as cross-cutting — check what else depends on the exact shape before editing.
- The preset avatar key list (`pumpkin, ghost, bat, black-cat, spider, skull, witch, vampire, zombie`) must stay identical in `auth/Avatars.kt` (backend, source of truth for validation) and `static/js/avatars.js` (frontend). See `docs/AVATARS.md`.

## Gotchas learned in this codebase (avoid re-discovering these)

- **`org.jlleitschuh.gradle.ktlint` must be 14.2.0+.** Version 12.1.2 fails against Kotlin 2.1.20 with a `KtTokens ... HEADER_KEYWORD` error (a bundled-ktlint/Kotlin-PSI mismatch) regardless of whether an explicit `ktlint { version.set(...) }` is set. Don't pin an explicit ktlint engine version — let the plugin use its own bundled-compatible one.
- **Jackson silently mangles Kotlin `isXxx: Boolean` properties.** A data class field like `val isClosed: Boolean` serializes as `"closed"` (Jackson's JavaBean introspection strips the `is` prefix from the Kotlin-generated `isXxx()` getter). Fix with `@get:JsonProperty("isClosed")` on the property. Verify any new boolean field with a `JsonCompareMode.STRICT` full-body assertion in a test, not just a spot `jsonPath` check — a wrong field name still "passes" a check for the right *value* at the wrong key.
- **`@WebFluxTest` component-scans every `@Component WebFilter` in the app**, not just the controller named in the slice annotation. Adding `SessionAuthWebFilter` broke the pre-existing `HealthControllerTest` this way (`NoSuchBeanDefinitionException` for the filter's own dependencies). Fix: a nested `@TestConfiguration` supplying a `mockk(relaxed = true)` bean for whatever the filter needs, imported via `@Import`. `@MockBean`/`springmockk` aren't available here (Mockito is excluded from `spring-boot-starter-test` and `springmockk` isn't in the version catalog). Simplest overall fix for a controller test: use full `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` instead of `@WebFluxTest` — it builds the real `JdbcTemplate`/filters and sidesteps this entirely, which is why it's the default pattern used across this project's test suite.
- **Multi-statement atomicity with plain JDBC**: there's no `@Transactional` composing cleanly with suspend functions here. Pattern used: constructor-inject `PlatformTransactionManager`, wrap it in a `TransactionTemplate`, and call `.execute { ... }` *inside* a `withContext(Dispatchers.IO) { }` block (see `BookRepository.activateBook`). This runs both statements as one transaction with no callback-adaptor complexity.
- **SQLite specifics**: the `org.xerial:sqlite-jdbc` driver does **not** create the DB file's parent directory — a fresh checkout with no `data/` dir fails at startup. `Application.kt`'s `main()` creates it (`File(dbPath).absoluteFile.parentFile?.mkdirs()`) *before* `runApplication(...)`, since it must happen before Spring builds the `DataSource` bean (too late for an `ApplicationRunner`). Also, SQLite allows only one writer at a time — `application.yml` sets `spring.datasource.hikari.maximum-pool-size: 1` to avoid "database is locked" errors under concurrent load.
- **Native `confirm()`/`alert()` dialogs freeze browser automation** (Claude-in-Chrome / CDP-based testing) — clicking a button that triggers one blocks all further input/eval on that tab until a human dismisses it. Not an app bug (real users are unaffected), but when verifying delete/destructive flows via browser automation, either avoid clicking those buttons and hit the endpoint directly with `curl` instead, or expect to need manual dialog dismissal.
- **Static JS/CSS must be served with `Cache-Control: no-cache`** (`spring.web.resources.cache.cachecontrol.no-cache` in `application.yml`). Without it Spring sends only `Last-Modified`, and browsers heuristically reuse cached ES modules without revalidating — after an upgrade a page can mix old and new modules (seen with the v0.4.0 avatar picker). When verifying a frontend change in an already-open browser tab, confirm the new CSS/JS actually loaded (e.g. `performance.getEntriesByType('resource')` `transferSize === 0` means served from cache).
- **`avatar_key` has a non-preset `'default'` value** in `schema.sql` for rows created before a real avatar is chosen (including the bootstrap-seeded first admin). The frontend's `avatarGlyph()` falls back to a generic glyph for any unrecognized key rather than crashing — keep that fallback if you touch avatar rendering.

## UI conventions

- Buttons in the same row/group (e.g. header actions) must share a consistent, equal height — never let one grow taller than its neighbors because of icon/emoji content inside it. Set an explicit `height` (or `min-height`) plus `display: inline-flex; align-items: center;` on the shared button style so larger inline content (icons, emoji) is vertically centered instead of stretching the box.
- Prefer shorter, wider buttons over taller ones — accommodate extra content (icons, longer labels) with horizontal padding, not additional height.
- Avoid native `confirm()`/`alert()`/`prompt()` for anything that will be exercised by browser automation in this project's own testing (see the gotcha above) — if a confirmation step is genuinely needed, a real user clicking it is fine, but be aware future automated verification of that flow will need to go around it via the API directly.
