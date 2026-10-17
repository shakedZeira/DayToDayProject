#!/data/data/com.termux/files/usr/bin/bash
# Stop the DayToDay server (phone-local). Safe to run when nothing is running.
pkill -f "server/dist/phone.js" 2>/dev/null || true
pkill -f "proot-distro login ubuntu" 2>/dev/null || true
pkill -f "phone/start.sh" 2>/dev/null || true
termux-wake-unlock 2>/dev/null || true
echo "DayToDay stopped"
exit 0
