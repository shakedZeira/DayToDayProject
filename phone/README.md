# Running DayToDay on an Android phone

Runs the app (API + built PWA) on one local port inside a proot Ubuntu environment, so
the relative `/api/...` client calls hit the same origin. No root required.

## 1. Termux -> proot-distro -> Ubuntu

In the outer **Termux** app:

```bash
pkg update -y && pkg install -y proot-distro
proot-distro install ubuntu
# log in, binding Termux home to /root so the project persists in shared storage:
proot-distro login ubuntu --bind /data/data/com.termux/files/home:/root
```

Inside the Ubuntu shell:

```bash
apt-get update && apt-get install -y git
git clone https://github.com/shakedZeira/DayToDayProject.git /root/dtd
bash /root/dtd/phone/setup.sh
```

The setup script is idempotent: re-run it any time to pull the latest code and rebuild.

## 2. Open it on the phone

In Chrome go to `http://127.0.0.1:4000`, then **menu -> Add to Home screen**.
`localhost` is a secure context, so the PWA installs and its service worker runs even
without HTTPS.

## 3. Keep it running

- `phone/run.sh` calls `termux-wake-lock` automatically so Android does not freeze the
  process.
- Termux is whitelisted from battery optimization (`adb shell cmd deviceidle whitelist
  +com.termux`), or do it in Settings -> Apps -> Termux -> Battery -> Unrestricted.
- Auto-start: `~/.bashrc` (installed from `phone/termux-autostart.bashrc`) starts the
  server the moment Termux is opened, unless it is already listening on
  `127.0.0.1:4000`. So after a reboot just open the Termux app once.
- **Termux:Boot is not usable here**: it declares the `com.termux` shared user id, so it
  only installs alongside a Termux signed with the same key. The GitHub build we use is
  debug-signed, while the F-Droid Termux:Boot is F-Droid-signed, so Android rejects it
  (`INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`). Getting real boot autostart would require
  re-installing Termux from F-Droid and redoing setup.

## 4. Switch from SQLite to hosted Postgres

The phone build uses SQLite by default so no external database or card is needed. To use
a free hosted Postgres (e.g. Neon):

1. In the **repo-root** `.env`, set `DATABASE_URL` to your `postgresql://...` connection
   string.
2. In `server/prisma/schema.prisma`, change `provider = "sqlite"` back to
   `provider = "postgresql"`.
3. Re-run `bash phone/setup.sh`.

## 5. LLM / vision providers

Setup writes `LLM_PROVIDER="mock"` and `VISION_PROVIDER="stub"`, so no API key is
required. To use a real model, set `LLM_PROVIDER` to your HTTP provider and fill in
`LLM_HTTP_KEY` (and `LLM_HTTP_URL` / `LLM_MODEL`) in the repo-root `.env`.

## 6. A note on patching

`setup.sh` patches the phone clone at setup time (schema provider + removal of
Postgres-only `mode: "insensitive"` query args). Because of this, always re-run
`bash phone/setup.sh` after a `git pull` so the SQLite patches are re-applied.
