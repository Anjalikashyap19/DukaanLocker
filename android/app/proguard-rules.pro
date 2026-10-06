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

# Keep error-body DTOs intact — Gson parses them reflectively, so R8 must not
# strip/rename their fields (bug: empty error toasts in release when it did).
-keep class com.iadv.dukaanlocker.api.ErrorResponse { *; }

# Keep ALL Retrofit/Gson DTOs. These classes are only ever created reflectively
# (no `new-instance` in bytecode), so R8 treats them as uninstantiable: it
# rewrites every `check-cast DocumentResponse` into an unconditional
# ClassCastException, deletes the field reads, then removes the class itself.
# That hollowed out toDocumentItems(), so no DocumentItem was ever built and the
# whole document pipeline (fetch / certificate viewer / upload) vanished from
# release builds while debug kept working.
-keep class com.iadv.dukaanlocker.api.** { *; }
-keep class com.iadv.dukaanlocker.DocumentItem { *; }

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

# ── Release log stripping ───────────────────────────────────────────────────
# Remove verbose/debug/info logging from RELEASE builds (R8 optimization).
# Log.e/Log.w are kept for production diagnostics, but sensitive payloads
# (GST/FSSAI bodies, tokens) must be DEBUG-gated at the call site.
-assumenosideeffects class android.util.Log {
    public static int v(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int d(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int i(java.lang.String, java.lang.String);
    public static int i(java.lang.String, java.lang.String, java.lang.Throwable);
}

# TEMP DIAG EXPERIMENT: keep Fetch/Certificate dialog feature — removed, root
# cause was the API-DTO keep rule above (R8 was hollowing out toDocumentItems).


