# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Gson / JSON
-keep class org.json.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep model classes
-keep class com.example.aiclient.data.** { *; }
