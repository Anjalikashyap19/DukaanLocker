# ── Jetpack Compose ─────────────────────────────────────────────────────────
# Keep @Composable methods (required by Compose compiler plugin)
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── App Data Classes ────────────────────────────────────────────────────────
# Keep class names only (not all members) — JSONObject uses string keys, not reflection
-keep,allowshrinking class com.example.dukaanlocker.** {
    <init>(...);
}

# ── General ─────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
