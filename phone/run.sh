#!/data/data/com.termux/files/usr/bin/bash
# Launch the DayToDay server (inside proot Ubuntu). Idempotent: no-op if already up.
set -euo pipefail

export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH="$PREFIX/bin:$PATH"
cd "$HOME"

# Already listening on the app port? Nothing to do.
if (exec 3<>/dev/tcp/127.0.0.1/4000) 2>/dev/null; then
  echo "DayToDay already running on 127.0.0.1:4000"
  exit 0
fi

exec proot-distro login ubuntu --bind "$HOME:/root" -- \
  bash -lc 'cd /root/dtd && exec bash phone/start.sh'
