#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

SCRIPT="$HOME/A01_YTM3U8/ytm3u8_resolver.py"
PIDFILE="$HOME/.a01_ytm3u8_resolver.pid"
LOGFILE="$HOME/.a01_ytm3u8_resolver.log"

if ! command -v python >/dev/null 2>&1; then
    echo "Python belum terpasang. Jalankan pkg install python."
    exit 1
fi
if [ ! -f "$SCRIPT" ]; then
    echo "Resolver tidak ditemukan: $SCRIPT"
    echo "Jalankan termux/install-termux.sh dari folder proyek."
    exit 1
fi

if curl -fsS --max-time 2 http://127.0.0.1:8765/health >/dev/null 2>&1; then
    echo "[OK] Resolver sudah aktif di 127.0.0.1:8765"
    exit 0
fi

nohup python "$SCRIPT" >> "$LOGFILE" 2>&1 < /dev/null &
echo "$!" > "$PIDFILE"

for _ in $(seq 1 12); do
    if curl -fsS --max-time 2 http://127.0.0.1:8765/health >/dev/null 2>&1; then
        echo "[OK] Resolver aktif di 127.0.0.1:8765"
        echo "[LOG] $LOGFILE"
        exit 0
    fi
    sleep 1
done

echo "[ERROR] Resolver gagal aktif. Periksa: $LOGFILE"
exit 1
