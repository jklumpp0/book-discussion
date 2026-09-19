# October Discussion

A small, self-hosted discussion site for a book club.

## Running it

```
docker compose up --build
```

The site is then available at `http://localhost:8080`.

## First-run admin access code

On the very first boot (when the database has no users yet), the app creates one
admin user and prints its one-time access code to the container logs. Find it with:

```
docker compose logs app | grep "First-run admin"
```

It looks like:

```
=== First-run admin access code: <code> — save this now, it will not be shown again ===
```

Save that code — it is not stored anywhere in plaintext and will not be shown again.
Use it to log in as the admin, then create accounts for the rest of the book club
from the in-app admin UI.

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
