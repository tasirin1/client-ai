# AI Client

Android client AI dengan antarmuka seperti ChatGPT. Mendukung **14 provider AI** dengan auto-fallback jika token habis.

## Fitur

### 💬 Chat seperti ChatGPT
- **Sidebar sesi** — grup tanggal (Hari Ini / Kemarin / 7 Hari / Bulan Ini), preview pesan terakhir
- **Cari sesi** berdasarkan judul
- **Edit & Regenerate** — tap ikon edit pada pesanmu, AI generate ulang respons
- **Copy respons AI** — tap ikon copy, animasi check
- **Auto-scroll** ke pesan terbaru
- **Keyboard otomatis turun** saat AI merespon
- **Animasi typing** ala ChatGPT (3 titik + "AI mengetik...")
- **Date separator** antar hari
- **Image recognition** — kirim gambar, AI otomatis pakai model vision

### 🔧 14 Provider AI
Setiap provider punya konfigurasi sendiri (API key, model, base URL, temperature, max tokens):

| Provider | Default Model | Model Lain |
|----------|--------------|------------|
| **OpenAI** | gpt-4o | gpt-4o-mini, gpt-4-turbo, gpt-4, gpt-3.5-turbo, o1, o1-mini, o3-mini |
| **Anthropic** | claude-3-5-sonnet | claude-3-opus, claude-3-sonnet, claude-3-haiku, claude-3-5-haiku |
| **Google Gemini** | gemini-2.5-flash | gemini-2.5-flash, gemini-2.5-pro, gemini-2.0-flash, gemini-2.0-flash-lite, gemini-1.5-pro, gemini-1.0-pro |
| **Deepseek** | deepseek-chat | deepseek-reasoner, deepseek-coder |
| **Groq** | llama-3.3-70b | llama-3.1-8b, llama-guard-3, llama3-70b, llama3-8b, mixtral-8x7b, gemma2-9b, deepseek-r1-distill |
| **OpenRouter** | openai/gpt-4o | 18+ model dari berbagai provider |
| **Mistral** | mistral-large | mistral-medium, mistral-small, open-mistral-nemo, codestral |
| **xAI (Grok)** | grok-2 | grok-beta |
| **Cohere** | command-r-plus | command-r, command |
| **Perplexity** | sonar-pro | sonar, sonar-reasoning |
| **Together AI** | Mixtral-8x7B | Llama-3.3, Llama-3.1, Deepseek-coder |
| **Fireworks AI** | llama-v3p3 | llama-v3p1, mixtral-8x7b |
| **GitHub Models** | gpt-4o-mini | gpt-4o, gpt-4-turbo, gpt-3.5-turbo |
| **AI21** | jamba-1.5 | jamba-1.5-mini |
| **Custom** | — | Bebas isi manual |

### 🔄 Auto-Fallback
Gagal karena token habis / rate limit / error? AI otomatis pindah:
1. Model berikutnya di provider yang sama
2. Provider berikutnya di daftar
3. **Semua provider lain yang API key-nya terisi**
4. Fallback berjalan di latar belakang, transparan

### 🧠 Memory & Konteks
- **System prompt** kustom — atur kepribadian AI
- **AI tau waktu** — inject info waktu nyata ke system prompt
- **Backup & Restore** — simpan/muat data percakapan ke file JSON

### 📡 Test Koneksi
Tombol **Uji Koneksi** di pengaturan:
- ✅ Terhubung — status 2xx
- ❌ Gagal — tampilkan HTTP code + pesan error

### 🎨 Tampilan
- Dark mode netral (#121212 + aksen hijau #10A37F)
- Chat bubble (Kamu / Asisten)
- Timestamp setiap pesan
- Loading & typing indicator
- Animasi fade-in

## Persyaratan

- Android 8.0+ (API 26)
- Koneksi internet
- API Key dari provider AI

## Cara Pakai

1. Download APK dari **Actions** → artifact `ai-client-debug-apk`
2. Buka **Pengaturan** (ikon ⚙️ di sidebar)
3. Pilih **Provider**, isi **API Key**, pilih **Model**
4. Klik **Test** untuk verifikasi koneksi
5. Tutup pengaturan, ketik pesan, kirim

## Build

```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions otomatis build tiap push ke `main`. Download dari artifact.

## Struktur

```
app/src/main/java/com/example/aiclient/
├── MainActivity.kt          # UI (Jetpack Compose)
├── AppViewModel.kt          # State management + API logic + fallback
├── AppContainer.kt          # Dependency injection
├── AiClientApplication.kt   # Application class
├── data/
│   ├── AppDatabase.kt       # Room database
│   ├── ChatDao.kt           # DAO queries
│   ├── ChatRepository.kt    # Repository
│   ├── Models.kt            # Entity + AppPrefs + all provider configs
│   ├── SettingsStore.kt     # DataStore preferences
│   └── BackupManager.kt     # Backup/restore JSON
├── network/
│   ├── GenericApiClient.kt  # OkHttp client + template engine
└── ui/
    └── Theme.kt             # Dark theme color scheme
```

## Lisensi
[GNU General Public License v3.0](LICENSE)
