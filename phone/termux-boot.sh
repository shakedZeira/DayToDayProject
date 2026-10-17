#!/data/data/com.termux/files/usr/bin/bash
# Outer-Termux bootstrap: installs proot-distro + Ubuntu, extracts the pushed
# source tarball, and runs phone/setup.sh inside Ubuntu. Run from Termux.
set -euo pipefail

export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH="$PREFIX/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib:${LD_LIBRARY_PATH:-}"
cd "$HOME"

# Keep Android from freezing/killing the long build.
if command -v termux-wake-lock >/dev/null 2>&1; then termux-wake-lock || true; fi

echo "[boot] $(date) rerun: pinning Termux mirror + installing proot-distro"
export DEBIAN_FRONTEND=noninteractive
echo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main" > "$PREFIX/etc/apt/sources.list"
apt update
apt install -y proot-distro

UBUNTU_ROOT="$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu"
if [ ! -d "$UBUNTU_ROOT" ]; then
  echo "[boot] installing Ubuntu rootfs"
  proot-distro install ubuntu
fi

echo "[boot] entering Ubuntu: extract source + run setup"
proot-distro login ubuntu --bind "$HOME:/root" -- bash -lc '
  set -e
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y git tar curl ca-certificates
  mkdir -p /root/dtd
  tar -xzf /root/dtd-src.tar.gz -C /root/dtd
  cd /root/dtd
  bash phone/setup.sh
'
echo "[boot] DONE"
