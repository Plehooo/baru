#!/usr/bin/env python3
"""Local-only YouTube HLS resolver for A01 YouTube M3U8 Player.

This service binds exclusively to 127.0.0.1. It resolves public YouTube URLs
with yt-dlp and returns only non-DRM HLS/M3U8 formats exposed by the extractor.
It does not use browser cookies, account sessions, PO-token workarounds, or DRM
circumvention.
"""

from __future__ import annotations

import json
import logging
import math
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, urlunsplit

HOST = "127.0.0.1"
PORT = 8765
MAX_REQUEST_BYTES = 16 * 1024
MAX_RESPONSE_BYTES = 8 * 1024 * 1024
RESOLVE_TIMEOUT_SECONDS = 95
ALLOWED_HOSTS = {
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "youtu.be",
    "www.youtu.be",
    "kids.youtube.com",
    "youtubekids.com",
    "www.youtubekids.com",
    "youtube-nocookie.com",
    "www.youtube-nocookie.com",
}
SAFE_HEADERS = {
    "user-agent",
    "referer",
    "origin",
    "accept",
    "accept-language",
}

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOG = logging.getLogger("a01-resolver")


def normalize_youtube_url(raw: str) -> str:
    raw = (raw or "").strip()
    if not raw or len(raw) > 4096:
        raise ValueError("URL kosong atau terlalu panjang.")

    parsed = urlsplit(raw)
    if parsed.scheme.lower() not in {"https", "http"}:
        raise ValueError("Gunakan URL YouTube yang diawali https://.")
    host = (parsed.hostname or "").lower().rstrip(".")
    if host not in ALLOWED_HOSTS:
        raise ValueError("Sumber dibatasi ke youtube.com, youtu.be, dan YouTube Kids.")

    # Do not carry user-info or an arbitrary port into the resolver.
    if parsed.username or parsed.password or parsed.port not in (None, 80, 443):
        raise ValueError("Format alamat YouTube tidak didukung.")

    scheme = "https"
    path = parsed.path or "/"
    query = parsed.query
    if host in {"youtubekids.com", "www.youtubekids.com", "kids.youtube.com"}:
        # YouTube Kids shares public video IDs with YouTube. This only normalizes
        # the public watch URL; it does not unlock age-restricted/private content.
        host = "www.youtube.com"
    if host in {"youtu.be", "www.youtu.be"}:
        host = "youtu.be"
    return urlunsplit((scheme, host, path, query, ""))


def safe_headers(info: dict, fmt: dict) -> dict[str, str]:
    merged: dict[str, str] = {}
    for source in (info.get("http_headers"), fmt.get("http_headers")):
        if not isinstance(source, dict):
            continue
        for key, value in source.items():
            if not isinstance(key, str) or not isinstance(value, str):
                continue
            if key.lower() not in SAFE_HEADERS:
                continue
            if len(value) > 2048 or "\r" in value or "\n" in value:
                continue
            merged[key] = value
    return merged


def finite_number(value: object) -> float:
    try:
        number = float(value or 0)
        return number if math.isfinite(number) else 0.0
    except (TypeError, ValueError):
        return 0.0


def extract_hls(url: str) -> dict:
    command = [
        sys.executable,
        "-m",
        "yt_dlp",
        "--dump-single-json",
        "--no-warnings",
        "--no-playlist",
        "--skip-download",
        "--no-progress",
        "--js-runtimes",
        "node",
        url,
    ]
    try:
        completed = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=RESOLVE_TIMEOUT_SECONDS,
            check=False,
            shell=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError("Ekstraksi melewati batas waktu. Coba lagi.") from exc
    except OSError as exc:
        raise RuntimeError("yt-dlp belum terpasang. Jalankan install-termux.sh.") from exc

    if completed.returncode != 0:
        stderr = (completed.stderr or "").lower()
        if "sign in" in stderr or "age-restricted" in stderr or "age restricted" in stderr:
            raise RuntimeError("YouTube membatasi video ini. Resolver tidak melewati batas usia atau login.")
        if "private video" in stderr or "video unavailable" in stderr:
            raise RuntimeError("Video tidak tersedia secara publik untuk resolver ini.")
        raise RuntimeError("yt-dlp gagal membaca URL. Perbarui yt-dlp dan periksa URL/internet.")

    if len(completed.stdout) > MAX_RESPONSE_BYTES:
        raise RuntimeError("Metadata video terlalu besar untuk diproses.")
    try:
        info = json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError("Respons metadata YouTube tidak valid.") from exc

    raw_formats = info.get("formats") or []
    candidates: list[dict] = []
    seen_urls: set[str] = set()
    for fmt in raw_formats:
        if not isinstance(fmt, dict):
            continue
        protocol = str(fmt.get("protocol") or "").lower()
        stream_url = fmt.get("url")
        if "m3u8" not in protocol or not isinstance(stream_url, str):
            continue
        if not stream_url.startswith("https://") or stream_url in seen_urls:
            continue
        if fmt.get("has_drm") is True:
            continue
        if str(fmt.get("vcodec") or "none").lower() == "none":
            continue
        seen_urls.add(stream_url)
        height = fmt.get("height")
        try:
            height_num = int(height or 0)
        except (TypeError, ValueError):
            height_num = 0
        candidates.append({
            "url": stream_url,
            "format_id": str(fmt.get("format_id") or "hls"),
            "protocol": protocol,
            "resolution": str(fmt.get("resolution") or (f"{height_num}p" if height_num else "auto")),
            "height": height_num,
            "fps": finite_number(fmt.get("fps")),
            "tbr": finite_number(fmt.get("tbr")),
            "headers": safe_headers(info, fmt),
        })

    candidates.sort(
        key=lambda item: (item["height"], item["fps"], item["tbr"]),
        reverse=True,
    )
    if not candidates:
        raise RuntimeError(
            "YouTube tidak menyediakan format video HLS/M3U8 yang terdeteksi untuk URL ini. "
            "Sebagian video biasa hanya menyediakan DASH/MP4; resolver tidak mengubahnya menjadi M3U8."
        )

    live_status = str(info.get("live_status") or "").lower()
    is_live = bool(info.get("is_live")) or live_status == "is_live"
    return {
        "ok": True,
        "title": str(info.get("title") or "YouTube video")[:300],
        "extractor": str(info.get("extractor_key") or info.get("extractor") or "YouTube"),
        "is_live": is_live,
        "live_status": live_status,
        "formats": candidates,
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "A01Resolver/1.0"

    def log_message(self, fmt: str, *args) -> None:
        # Do not log submitted URLs, signed media URLs, or headers.
        LOG.info("HTTP request handled")

    def send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/health":
            self.send_json(404, {"ok": False, "error": "Not found"})
            return
        try:
            import yt_dlp
            version = getattr(yt_dlp.version, "__version__", "installed")
        except Exception:
            version = "missing"
        self.send_json(200, {"ok": True, "service": "A01 YouTube M3U8 Resolver", "yt_dlp": version})

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/resolve":
            self.send_json(404, {"ok": False, "error": "Not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_REQUEST_BYTES:
                self.send_json(413, {"ok": False, "error": "Ukuran permintaan tidak valid."})
                return
            raw_body = self.rfile.read(length)
            request = json.loads(raw_body.decode("utf-8"))
            source_url = normalize_youtube_url(str(request.get("url") or ""))
            result = extract_hls(source_url)
            self.send_json(200, result)
        except (ValueError, json.JSONDecodeError) as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except RuntimeError as exc:
            self.send_json(422, {"ok": False, "error": str(exc)})
        except BrokenPipeError:
            return
        except Exception:
            LOG.exception("Unexpected resolver error")
            self.send_json(500, {"ok": False, "error": "Kesalahan internal resolver."})


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.daemon_threads = True
    LOG.info("A01 resolver aktif di http://%s:%d (localhost only)", HOST, PORT)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        LOG.info("Resolver dihentikan")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
