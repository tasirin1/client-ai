# AI Client

Android client generik untuk memanggil API apa saja, menyimpan sesi, dan mempertahankan memory antar sesi di device yang sama.

## Fitur

- Endpoint URL bebas
- Method `GET`, `POST`, `PUT`, `PATCH`, `DELETE`
- Header custom
- Template body dengan placeholder raw `{{input}}`, `{{memory}}`, `{{history}}`
- Template body aman JSON dengan placeholder `{{input_json}}`, `{{memory_json}}`, `{{history_json}}`
- Sesi tersimpan di Room
- Global memory tersimpan di DataStore, jadi tetap ada setelah app ditutup lalu dibuka lagi
- Response panel dan riwayat pesan per sesi

## Cara Pakai

1. Buka proyek ini di Android Studio.
2. Tunggu sync Gradle selesai.
3. Jalankan ke emulator atau device Android.
4. Isi endpoint API yang mau dipanggil.
5. Sesuaikan headers dan body template sesuai format API target.

## Contoh Template

```json
{
  "input": {{input_json}},
  "memory": {{memory_json}},
  "history": {{history_json}}
}
```

Kalau API kamu butuh body mentah, gunakan placeholder raw:

```text
User: {{input}}
Memory: {{memory}}
History: {{history}}
```

## Catatan

- Memory saat ini tersimpan lokal di device.
- Kalau yang kamu butuhkan sinkron lintas device atau antar akun, perlu backend cloud tambahan.
- Proyek ini sudah disiapkan sebagai fondasi untuk client REST atau client AI yang lebih besar.

## Build Lokal

Kalau environment sudah siap:

```bash
./gradlew assembleDebug
```

APK debug akan ada di:

`app/build/outputs/apk/debug/`

