# October Discussion

A small, self-hosted discussion site for a book club.

## Running it (Docker)

```
docker compose up --build
```

The site is then available at `http://localhost:8080`.

> **Note:** this Docker path has not been verified against a real Docker daemon as of this writing (none was available in the environments used during development) — the Dockerfile/compose file were reviewed carefully and the equivalent native build+run flow below was verified instead. If `docker compose up --build` doesn't work cleanly, that's the first thing to debug.

## Running it (local development, no Docker)

Requires JDK 21. If your shell's default `java` is a different version:

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home   # adjust for your machine
export PATH="$JAVA_HOME/bin:$PATH"
```

Then:

```
./gradlew bootRun
```

The site is available at `http://localhost:8080`, and the database lands in `./data/october-discussion.db` (relative to the project root) instead of a Docker volume. Everything else below (admin code, persistence behavior) works the same way.

## First-run admin access code

On the very first boot (when the database has no users yet), the app creates one
admin user and prints its one-time access code to the logs. Find it with:

```
docker compose logs app | grep "First-run admin"     # Docker
```

or, for `./gradlew bootRun`, just look at the console output — it's the same log line:

```
=== First-run admin access code: <code> — save this now, it will not be shown again ===
```

Save that code — it is not stored anywhere in plaintext and will not be shown again.
Use it to log in as the admin, then create accounts for the rest of the book club
from the in-app **admin panel** (the "Admin panel" button in the header, visible only
to admin accounts).

## Data persistence

The SQLite database lives on the named Docker volume `october-discussion-data`,
mounted at `/app/data` inside the container. It survives container restarts and
rebuilds (`docker compose up --build`); it's only lost if you explicitly remove the
volume with `docker compose down -v`.

## Stopping and restarting

```
docker compose down    # stops the container, keeps the data volume
docker compose up -d   # starts it again, same data, no new admin user is created
```
