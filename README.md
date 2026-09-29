# October Discussion

A small, self-hosted discussion site for a book club.

## Deploying (released image)

Pushing a version tag (e.g. `git tag v0.2.0 && git push origin v0.2.0`) runs
`.github/workflows/release.yml`, which:

1. runs `./gradlew check` and builds the jar,
2. builds an **arm64** image containing only that jar on a Java 21 JRE and pushes it to
   `ghcr.io/jklumpp0/book-discussion` as `:<version>` and `:latest`,
3. creates a GitHub Release with `october-discussion-deploy-<version>.tgz` attached.

That tarball is the whole deliverable: a `docker-compose.yml` pinned to the released
image, an empty `data/` directory, and a README (source: `deploy/`). The server never
needs this repository — see `deploy/README.md` for the install/upgrade/backup steps.

After the first release, make the GHCR package public once (GitHub → Packages →
book-discussion → Package settings → Change visibility) so servers can pull without
logging in.

Pull requests and pushes to `main` only run `./gradlew check` (`.github/workflows/ci.yml`).

## Running it locally with Docker

The Dockerfile only packages a prebuilt jar, so build that first:

```
./gradlew bootJar
docker compose up --build
```

The site is then available at `http://localhost:8080`.

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

The SQLite database lives in `./data/` next to the compose file, bind-mounted at
`/app/data` inside the container. It survives container restarts, `docker compose
down`, and image upgrades; only deleting the directory loses it. Local
`./gradlew bootRun` uses the same `./data/` path, so it shares the database with
local `docker compose`.

## Stopping and restarting

```
docker compose down    # stops the container; ./data is untouched
docker compose up -d   # starts it again, same data, no new admin user is created
```
