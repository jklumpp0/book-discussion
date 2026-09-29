# October Discussion — deployment bundle

This bundle is everything needed to run October Discussion. The server only needs
Docker (with the Compose plugin) on an **arm64** host — no source checkout, no JDK.

## Install

```
tar xzf october-discussion-deploy-<version>.tgz
cd october-discussion
docker compose up -d
```

The site is then available at `http://<host>:8080`. To use a different host port,
change the left side of `"8080:8080"` in `docker-compose.yml`.

## First-run admin access code

On the very first start (empty database) the app creates an admin user and prints
its one-time access code to the logs:

```
docker compose logs app | grep "First-run admin"
```

Save it — it is not stored in plaintext and will not be shown again. Log in with it
and create accounts for everyone else from the in-app admin panel.

## Data

The SQLite database lives in `./data/` next to `docker-compose.yml` (a bind mount).
It survives container restarts, `docker compose down`, and image upgrades. Only
deleting that directory loses it.

To back it up:

```
docker compose stop
cp data/october-discussion.db ~/october-discussion-backup-$(date +%F).db
docker compose start
```

## Upgrading

Download the new release's bundle and copy its `docker-compose.yml` over the old
one (or just edit the image tag in place), keeping `./data/`. Then:

```
docker compose pull
docker compose up -d
```
