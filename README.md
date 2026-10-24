# TiruPrompt v1.0.0 — by DAFTECH

Kirim link video → APK membedah videonya → keluar **prompt final per scene 10 detik**, siap ditempel ke Veo / Kling / Wan / Muse Video. Plus **Trend Scrape Engine**: tarik video viral/trending YouTube & TikTok, dikelompokkan jadi tema + pola hook, lalu satu tombol mengirim video trending ke tab Analisa.

Package: `com.daftech.tiruprompt` • Kotlin native + Jetpack Compose (Material3) • minSdk 24, targetSdk 34.

## Tab

| Tab | Fungsi |
|---|---|
| **Analisa** | Link video + mode tiru + mesin tujuan → prompt per scene 10 detik |
| **Trend** | Scrape trending YouTube/TikTok → kartu tema (hook, format, status) + daftar video |
| **Riwayat** | Semua hasil analisa tersimpan lokal, bisa dibuka lagi / dihapus |
| **Setting** | Gemini API key, YouTube Data API key, nama model, region |

## Dua jalur analisa (jujur, selalu dilabeli)

1. **Gemini Video (ASLI)** — link YouTube + Gemini API key terisi: video dikirim ke Gemini 2.5 Flash sebagai `fileData`, Gemini menonton videonya dan mengembalikan JSON per potongan 10 detik (scene 1 = hook), lengkap dengan *Character/Product Lock* untuk kontinuitas antar-scene. Cap waktu scene selalu dinormalisasi ulang di kode (`start = index*10`), tidak dipercaya mentah dari model.
2. **Template (BUKAN analisa video asli)** — link non-YouTube atau Gemini key kosong: prompt disusun dari durasi (detik) + deskripsi yang diisi pengguna, dengan rotasi gerakan kamera & lighting. Hasilnya **selalu** dilabeli "Template (bukan analisa video asli …)". Tidak pernah ditampilkan sebagai analisa sungguhan.

Mode tiru: **Clone Struktur** (tiru persis), **Ganti Subjek** (struktur sama, subjek/produk diganti — field pengganti wajib), **Ambil Style** (hanya sinematografi/lighting/pacing).

## Trend Scrape Engine

- **YouTube + API key:** YouTube Data API v3 — `chart=mostPopular` per region + `search.list` per niche (urut viewCount) + statistik lengkap.
- **YouTube tanpa key:** scrape halaman hasil pencarian (`ytInitialData`, parser defensif — gagal = pesan jelas, tidak crash).
- **TikTok:** coba halaman pencarian publik (`__UNIVERSAL_DATA__`) + Creative Center (hashtag trending). TikTok sering memblokir scrape otomatis; kalau gagal, APK menampilkan pesan jujur dan **tidak pernah mengarang hasil**.
- Metrik per video: *velocity* (views/jam), *engagement* ((likes+komen)/views), *viral score* (log views + velocity + engagement).
- **Analisa tema lokal & deterministik** (tanpa AI): clustering kata signifikan judul + hashtag (min. muncul 2×), deteksi pola hook (pertanyaan / listicle angka / POV / before-after / kata kejut / reaction), tebak format, status tema: `LAGI NAIK` (velocity rata-rata ≥ 5.000/jam & video < 5), `RAMAI/JENUH` (video ≥ 6), `STABIL`. Tema yang juga muncul di cache platform lain berlabel **LINTAS PLATFORM**.
- Tombol **BEDAH JADI PROMPT** di kartu video: pindah ke tab Analisa dengan link terisi (lo yang menekan ANALISA).

## API key

Gratis, disimpan hanya di perangkat (SharedPreferences, tidak ikut backup, tidak pernah ditulis ke log):

- **Gemini:** Google AI Studio → Get API key.
- **YouTube Data API v3:** Google Cloud Console → aktifkan YouTube Data API v3 → Credentials → API key.

## Build

Build resmi lewat GitHub Actions (lihat `PANDUAN_UPLOAD.md` di folder paket): workflow memasang keystore tetap dari secret `KEYSTORE_B64`, ada **gerbang keras** sidik jari keystore + sertifikat APK — build gagal total bila kunci tidak cocok, supaya signature tidak pernah acak antar-rilis. APK hasil: release rolling `latest` → `TiruPrompt-v1.0.0.apk`.

Stack: AGP 8.5.2, Kotlin 1.9.25, Compose BOM 2024.06.00, Gradle 8.7, Java 17. Dependensi hanya Compose/Material3 + OkHttp (JSON pakai `org.json` bawaan).

## Batas jujur v1

- Analisa visual asli hanya untuk **YouTube** (Gemini fileUri). TikTok/Reels masuk jalur Template sampai ada jalur unduh+unggah video yang sah.
- Scrape TikTok bersifat best-effort (anti-bot TikTok bisa berubah sewaktu-waktu).
- Video panjang menghasilkan banyak scene; Gemini `maxOutputTokens` 8192 — video sangat panjang (> ~3–4 menit) bisa terpotong jumlah scene-nya.
