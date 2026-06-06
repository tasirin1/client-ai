# AI Client

Android client generik untuk memanggil API apa saja, dengan:

- endpoint URL fleksibel
- method `GET` / `POST` / `PUT` / `PATCH` / `DELETE`
- headers bebas
- template body dengan placeholder raw `{{input}}`, `{{memory}}`, `{{history}}`
- template body aman-JSON dengan placeholder `{{input_json}}`, `{{memory_json}}`, `{{history_json}}`
- sesi tersimpan di Room
- memory global tersimpan di DataStore agar sinkron antar sesi dan saat aplikasi dibuka ulang

## Cara pakai

1. Buka proyek ini di Android Studio.
2. Sinkronkan Gradle.
3. Jalankan ke emulator atau device Android.
4. Isi endpoint dan body template sesuai API yang mau dipakai.

## Catatan

- Implementasi ini menyimpan memory secara lokal di device. Kalau yang dimaksud sinkron ke cloud lintas device, perlu backend tambahan.
- File ini adalah scaffold awal yang sudah siap dikembangkan jadi client AI / REST yang lebih lengkap.
