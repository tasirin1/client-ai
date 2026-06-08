# AI Client

Android client AI generik dengan antarmuka seperti ChatGPT. Mendukung berbagai provider AI: OpenAI, Anthropic, Google Gemini, Deepseek, Groq, OpenRouter.

## Fitur

### 💬 Chat seperti ChatGPT
- **Sidebar sesi** dengan grup tanggal (Hari Ini / Kemarin / 7 Hari / Bulan Ini)
- Preview pesan terakhir di setiap sesi
- Cari sesi berdasarkan judul
- Buat, pilih, rename, dan hapus sesi
- **Edit pesan** — tap ikon edit pada pesan kamu untuk mengubah dan AI generate ulang respons
- **Copy respons AI** — tap ikon copy, animasi check hijau
- Auto-scroll ke pesan terbaru
- Keyboard otomatis turun saat AI merespon

### 🔧 Multi-Provider AI
Setiap provider punya konfigurasi sendiri (API key, model, base URL, temperature, max tokens):

| Provider | Model Default |
|----------|--------------|
| **OpenAI** | gpt-4o, gpt-4o-mini, gpt-4-turbo, o1, o3-mini |
| **Anthropic** | claude-3-5-sonnet, claude-3-opus, claude-3-haiku |
| **Google Gemini** | gemini-1.5-pro, gemini-1.5-flash, gemini-2.0-flash |
| **Deepseek** | deepseek-chat, deepseek-reasoner |
| **Groq** | llama-3.3-70b, mixtral-8x7b, gemma2-9b |
| **OpenRouter** | openai/gpt-4o, anthropic/claude-3.5, google/gemini-2.0 |
| **Custom** | Bebas (isi manual) |

### 🧠 Memory & Konteks
- **System prompt** kustom — atur kepribadian AI
- **AI tau waktu** — otomatis inject info waktu ke system prompt
- **Auto-greeting** — AI sapa duluan di sesi baru (juga saat buat sesi baru)
- **AI Chat Duluan** — AI bisa memulai obrolan tanpa perlu kamu chat duluan
- **Jadwal Chat Natural** — Bilang "chat jam 8 malam", AI jawab natural dan otomatis chat kamu nanti (tanpa kode aneh)
- **Backup & Restore** — simpan/muat memory AI ke file JSON

### 📡 Test Koneksi
Tombol **Uji Koneksi** di pengaturan untuk verifikasi API:
- ✅ **Terhubung** — response sukses (2xx)
- ❌ **Gagal** — tampilkan HTTP code + pesan error

### 🎨 Tampilan
- Dark mode netral (#121212 + aksen hijau #10A37F)
- Chat bubble dengan label peran (Kamu / Asisten)
- Timestamp di setiap pesan
- Loading indicator saat mengirim
- Animasi fade-in pesan baru

### 📦 Pengaturan
- **Provider** — pilih provider, otomatisi isi Base URL
- **API Key** — masked, bisa ditampilkan
- **Model** — pilih dari daftar atau input model kustom
- **Temperature** slider (0.0 – 2.0)
- **Max Tokens**
- **System Prompt**
- **Backup / Restore Data**

## Persyaratan

- Android 8.0+ (API 26)
- Koneksi internet
- API Key dari provider AI

## Cara Pakai

1. Install APK dari [Releases](https://github.com/tasirin1/client-ai/releases)
2. Buka **Pengaturan** (ikon ⚙️ di sidebar)
3. Pilih **Provider**, isi **API Key**, pilih **Model**
4. Klik **Test** untuk verifikasi koneksi
5. Tutup pengaturan, ketik pesan, kirim

## Build & CI

### Build Lokal
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions
Push ke branch `main` otomatis build dan upload APK ke artifact.

## Struktur Proyek

```
app/src/main/java/com/example/aiclient/
├── MainActivity.kt          # UI (Jetpack Compose)
├── AppViewModel.kt          # State management + API logic
├── AppContainer.kt          # Dependency injection
├── AiClientApplication.kt   # Application class
├── data/
│   ├── AppDatabase.kt       # Room database
│   ├── ChatDao.kt           # DAO queries
│   ├── ChatRepository.kt    # Repository
│   ├── Models.kt            # Entity + AppPrefs + ProviderConfig
│   ├── SettingsStore.kt     # DataStore preferences
│   └── BackupManager.kt     # Backup/restore JSON
├── network/
│   ├── GenericApiClient.kt  # OkHttp client + template engine
└── ui/
    └── Theme.kt             # Dark theme color scheme
```

## Lisensi
Open source. Kembangkan sendiri sesuai kebutuhan.
