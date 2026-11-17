#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PIDFILE="$HOME/.a01_ytm3u8_resolver.pid"

if [ ! -f "$PIDFILE" ]; then
    echo "[INFO] PID resolver tidak ditemukan."
    exit 0
fi
PID="$(cat "$PIDFILE" 2>/dev/null || true)"
if [[ "$PID" =~ ^[0-9]+$ ]] && kill -0 "$PID" 2>/dev/null; then
    CMD="$(ps -p "$PID" -o args= 2>/dev/null || true)"
    case "$CMD" in
        *"$HOME/A01_YTM3U8/ytm3u8_resolver.py"*)
            kill "$PID" 2>/dev/null || true
            echo "[OK] Sinyal berhenti dikirim ke resolver (PID $PID)."
            ;;
        *)
            echo "[INFO] PID tersimpan bukan proses resolver; tidak dihentikan."
            ;;
    esac
else
    echo "[INFO] Proses resolver sudah tidak berjalan."
fi
rm -f "$PIDFILE"
