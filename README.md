# AI Client

Android client AI generik dengan antarmuka seperti ChatGPT. Mendukung OpenAI, Anthropic, Google Gemini, Deepseek, dan API kustom lainnya.

## Fitur

### 💬 Chat seperti ChatGPT
- Sidebar sesi dengan grup tanggal (Hari Ini / Kemarin / 7 Hari / Bulan Ini)
- Preview pesan terakhir di setiap sesi
- Cari sesi berdasarkan judul
- Buat, pilih, dan hapus sesi

### 🔧 Pengaturan API lengkap
- **Provider:** OpenAI / Anthropic / Google Gemini / Deepseek / Custom
- **API Key** (masked, bisa ditampilkan)
- **Model** — daftar model per provider, plus input model kustom
- **Base URL** auto-terisi sesuai provider
- **Temperature** slider (0.0 – 2.0)
- **Max Tokens**
- **System Prompt**

### 🔗 Multi-provider
Setiap provider punya format request yang berbeda secara otomatis:

| Provider | Auth | Endpoint |
|----------|------|----------|
| **OpenAI** | Bearer token | `…/v1/chat/completions` |
| **Anthropic** | x-api-key + version header | `…/v1/messages` |
| **Google Gemini** | API key via query param | `…/v1beta/models/{model}:generateContent` |
| **Deepseek** | Bearer token (OpenAI-compatible) | `…/v1/chat/completions` |
| **Custom** | Bebas (via template engine) | Sesuai konfigurasi |

### 💬 Response Cerdas
- Response JSON API otomatis di-parse ke teks murni (tidak menampilkan JSON mentah)
- Mendukung format OpenAI, Anthropic, Google Gemini, Deepseek
- Riwayat chat tersimpan rapi tanpa nested JSON error

### 📡 Test Koneksi
Tombol "Test" di pengaturan untuk verifikasi API bisa dijangkau:
- ✅ Terhubung — response sukses (2xx)
- ❌ Gagal — tampilkan HTTP code + response body

### 🎨 Tampilan
- Dark mode netral (abu-abu `#121212` + aksen hijau `#10A37F`)
- Chat bubble dengan animasi fade-in
- Timestamp di setiap pesan
- Loading indicator saat mengirim
- Input stabil tanpa loncat kursor saat mengetik
- State input lokal, tidak terpengaruh perubahan state lain

## Cara Pakai

1. Buka proyek ini di **Android Studio**.
2. Tunggu sync Gradle selesai.
3. Jalankan ke emulator atau device Android.
4. Buka **Pengaturan API** (ikon ⚙️ di kanan atas).
5. Pilih provider, isi **API Key**, pilih **model**.
6. Klik **Test** untuk verifikasi koneksi.
7. Tutup pengaturan, ketik pesan, kirim.

## Build & CI

### Build Lokal

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/`

### GitHub Actions

Push ke branch `main` otomatis build dan upload APK ke artifact.

## Arsitektur

```
app/src/main/java/com/example/aiclient/
├── MainActivity.kt          # UI (Jetpack Compose)
├── AppViewModel.kt          # State management + API logic
├── AppContainer.kt          # Dependency injection manual
├── AiClientApplication.kt   # Application class
├── data/
│   ├── AppDatabase.kt       # Room database
│   ├── ChatDao.kt           # DAO queries
│   ├── ChatRepository.kt    # Repository
│   ├── Models.kt            # Entity + AppPrefs
│   └── SettingsStore.kt     # DataStore preferences
└── network/
    └── GenericApiClient.kt  # OkHttp HTTP client
```

## Catatan

- Sesi & memory tersimpan lokal di device (Room + DataStore).
- Untuk sinkronisasi lintas device, perlu backend cloud tambahan.
- Proyek ini fondasi untuk client AI yang bisa dikembangkan lebih lanjut.

