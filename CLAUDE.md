# CLAUDE.md

Guidance for Claude Code when working in this Kotlin site/service.

## Stack defaults

- **Build**: Gradle with the Kotlin DSL (`build.gradle.kts`), version catalog in `gradle/libs.versions.toml`.
- **Web framework**: Ktor (lightweight, coroutine-native). If the project already uses Spring Boot, follow that instead — don't introduce a second framework.
- **Serialization**: `kotlinx.serialization`. Avoid Gson/Jackson unless already present.
- **Async**: Kotlin coroutines (`kotlinx.coroutines`) — no callback-style or blocking I/O in request handlers.
- **DB access**: Exposed or plain JDBC for simple needs. Avoid heavyweight ORMs unless the project already has one.
- **HTTP client**: Ktor client, not OkHttp/Retrofit, to stay consistent with the server stack.
- **Testing**: `kotlin.test` + JUnit 5 runner, or Kotest if already in use. `MockK` for mocking, not Mockito.
- **Logging**: `kotlin-logging` (SLF4J wrapper) over raw SLF4J or println.

Prefer the standard library over a dependency when the stdlib already covers it (e.g. `Result`, `sequence {}`, `buildList`, `Duration`, `Instant` via `kotlinx-datetime` only if you need cross-platform).

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

- Package structure: `com.<org>.<project>.<feature>`, feature-first rather than layer-first (`user/`, not `controllers/` + `services/` + `models/` split across the whole app).
- Run `./gradlew ktlintFormat` (or the project's configured formatter) before considering a change done, if configured.
- Run `./gradlew test` before reporting a task complete.

## UI conventions

- Buttons in the same row/group (e.g. header actions) must share a consistent, equal height — never let one grow taller than its neighbors because of icon/emoji content inside it. Set an explicit `height` (or `min-height`) plus `display: inline-flex; align-items: center;` on the shared button style so larger inline content (icons, emoji) is vertically centered instead of stretching the box.
- Prefer shorter, wider buttons over taller ones — accommodate extra content (icons, longer labels) with horizontal padding, not additional height.
