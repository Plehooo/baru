# A01 YouTube M3U8 Player

Android app + local Termux resolver for public YouTube and YouTube Kids URLs.

## Fitur

- Input URL YouTube, YouTube Live, atau YouTube Kids yang publik.
- Ekstrak daftar format video HLS/M3U8 yang benar-benar disediakan oleh yt-dlp.
- Urutkan pilihan berdasarkan resolusi, frame rate, dan bitrate.
- Putar format terpilih dengan AndroidX Media3 / ExoPlayer.
- Salin URL M3U8 hasil ekstraksi.
- Resolver hanya listen di `127.0.0.1:8765`; tidak membuka server ke jaringan LAN.
- Build APK debug otomatis melalui GitHub Actions setelah push ke branch `main`.

## Batasan yang perlu diketahui

YouTube tidak menyediakan M3U8 untuk semua video. Beberapa video biasa hanya memiliki format DASH atau MP4. Aplikasi ini hanya menampilkan format HLS/M3U8 yang memang diekspos oleh extractor; aplikasi tidak mengubah DASH/MP4 menjadi M3U8. URL media YouTube biasanya bertanda tangan dan dapat kedaluwarsa, jadi ekstrak ulang ketika playback gagal.

Resolver membutuhkan Node.js (versi 22 atau lebih baru) dan komponen `yt-dlp-ejs`. Resolver tidak memakai cookie akun, tidak membuka video privat/terbatas, tidak melewati pemeriksaan usia/login, dan tidak membypass DRM. Gunakan hanya konten yang memang boleh kamu akses dan putar.

## 1. Instal resolver di Termux

Dari folder proyek yang sudah diekstrak, resolver memasang Python, Node.js, dan paket yt-dlp beserta EJS yang diperlukan untuk tantangan JavaScript YouTube:

```bash
bash termux/install-termux.sh
bash ~/A01_YTM3U8/start-resolver.sh
```

Cek status:

```bash
curl -s http://127.0.0.1:8765/health
```

Hentikan resolver:

```bash
bash ~/A01_YTM3U8/stop-resolver.sh
```

Biarkan Termux berjalan saat aplikasi dipakai. APK berkomunikasi dengan resolver melalui loopback ponsel, bukan lewat IP publik.

## 2. Push ke GitHub

Untuk repo `Plehooo/baru`, jalankan dari folder proyek:

```bash
bash push-to-github.sh
```

Script akan clone repo ke `~/a01_ytm3u8_repo`, menaruh source Android di subfolder `A01-YouTube-M3U8-Player`, lalu memasang workflow unik di `.github/workflows/a01-youtube-m3u8.yml`. File repo lain tidak dihapus. Perubahan lokal yang belum di-commit di folder clone akan membuat script berhenti agar tidak tertimpa.

## 3. Ambil APK

Setelah push sukses:

1. Buka `https://github.com/Plehooo/baru/actions`.
2. Pilih workflow **A01 YouTube M3U8 APK**.
3. Buka run paling baru yang statusnya hijau.
4. Unduh artifact **A01-YouTube-M3U8-Player-debug-APK**.
5. Ekstrak ZIP artifact lalu instal `app-debug.apk` di Android.

APK debug ditandatangani otomatis untuk instalasi dan pengujian. Itu bukan APK release Play Store.

## Versi build

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- JDK: 17
- Compile SDK: 36
- AndroidX Media3: 1.11.1
