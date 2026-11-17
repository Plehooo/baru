#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET="$HOME/A01_YTM3U8"

pkg update -y
pkg install -y python curl nodejs
python -m pip install --upgrade "yt-dlp[default]"
mkdir -p "$TARGET"
cp "$SCRIPT_DIR/ytm3u8_resolver.py" "$TARGET/ytm3u8_resolver.py"
cp "$SCRIPT_DIR/start-resolver.sh" "$TARGET/start-resolver.sh"
cp "$SCRIPT_DIR/stop-resolver.sh" "$TARGET/stop-resolver.sh"
chmod 700 "$TARGET"/*.sh
chmod 600 "$TARGET/ytm3u8_resolver.py"

printf '\n[OK] Resolver terpasang di %s\n' "$TARGET"
printf '[NEXT] Jalankan: bash ~/A01_YTM3U8/start-resolver.sh\n'
printf '[INFO] Server hanya listen di 127.0.0.1:8765.\n'
