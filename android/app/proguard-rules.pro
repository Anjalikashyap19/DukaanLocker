# ── Jetpack Compose ─────────────────────────────────────────────────────────
# Keep @Composable methods (required by Compose compiler plugin)
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── App Data Classes ────────────────────────────────────────────────────────
# Keep class names only (not all members) — JSONObject uses string keys, not reflection
-keep,allowshrinking class com.iadv.dukaanlocker.** {
    <init>(...);
}

# ── Retrofit / Gson ─────────────────────────────────────────────────────────
# Keep Gson serialized data classes (Retrofit + Gson uses reflection for deserialization)
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep Retrofit API models with @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Prevent R8 from stripping interface information needed by Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── General ─────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
