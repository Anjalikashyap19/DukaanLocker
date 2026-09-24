package com.iadv.dukaanlocker.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

import android.util.Base64
import com.iadv.dukaanlocker.BuildConfig
import org.json.JSONObject


object ApiClient {

    private const val BASE_URL = BuildConfig.BASE_URL
    private const val LOCAL_BASE_URL = BuildConfig.LOCAL_BASE_URL
    private const val PROD_BASE_URL = BuildConfig.PROD_BASE_URL
    private const val LOCAL_FAILOVER_ENABLED = BuildConfig.LOCAL_FAILOVER_ENABLED
    /** How long local is marked down before we re-probe it. */
    private const val LOCAL_DOWN_BACKOFF_MS = 60_000L
    /** Faster connect timeout when failover is on so a dead local host fails fast. */
    private const val FAILOVER_CONNECT_TIMEOUT_SEC = 5L

    private const val PREFS_NAME = "dukaan_api_prefs"
    private const val SECURE_PREFS_NAME = "dukaan_secure_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_MOBILE = "user_mobile"
    private const val KEY_ROLE = "user_role"
    private const val KEY_MANAGER_CODE = "manager_code"

    @Volatile private var apiService: ApiService? = null
    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var documentStreamApi: DocumentStreamApi? = null
    @Volatile private var documentStreamRetrofit: Retrofit? = null
    @Volatile private var cachedSecurePrefs: SharedPreferences? = null

    // Shared sticky failover state for main + document-stream clients.
    @Volatile private var localDownUntilMs: Long = 0L

    // ── Token Management ──────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Encrypted storage for secrets (JWT, manager code). Values are AES-GCM
     * encrypted with a key in the Android Keystore, so they are never readable
     * from plaintext prefs files or device backups.
     */
    private fun securePrefs(context: Context): SharedPreferences {
        return cachedSecurePrefs ?: synchronized(this) {
            cachedSecurePrefs ?: run {
                try {
                    val masterKey = MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context.applicationContext,
                        SECURE_PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ).also { cachedSecurePrefs = it }
                } catch (e: Exception) {
                    android.util.Log.e("ApiClient", "EncryptedSharedPreferences failed, deleting corrupted file and retrying", e)
                    try { context.applicationContext.deleteFile(SECURE_PREFS_NAME) } catch (_: Exception) {}
                    try {
                        val masterKey = MasterKey.Builder(context.applicationContext)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build()
                        EncryptedSharedPreferences.create(
                            context.applicationContext,
                            SECURE_PREFS_NAME,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        ).also { cachedSecurePrefs = it }
                    } catch (e2: Exception) {
                        android.util.Log.e("ApiClient", "EncryptedSharedPreferences retry also failed, falling back to plain prefs", e2)
                        prefs(context.applicationContext).also { cachedSecurePrefs = it }
                    }
                }
            }
        }
    }

    fun saveAuth(context: Context, response: AuthResponse) {
        securePrefs(context).edit().apply {
            putString(KEY_TOKEN, response.token)
            putString(KEY_MANAGER_CODE, response.managerCode ?: "")
            apply()
        }
        // Never write the JWT to plain prefs — profile fields only (no secret).
        prefs(context).edit().apply {
            putLong(KEY_USER_ID, response.userId)
            putString(KEY_USER_NAME, response.userName)
            putString(KEY_EMAIL, response.emailId)
            putString(KEY_MOBILE, response.mobileNumber)
            putString(KEY_ROLE, response.role)
            apply()
        }
        // Migration: clear any legacy plaintext token left by older builds.
        if (prefs(context).contains(KEY_TOKEN)) {
            prefs(context).edit().remove(KEY_TOKEN).apply()
        }
    }

    fun getToken(context: Context): String? {
        // Encrypted prefs only — plaintext token storage was removed for security.
        return try {
            securePrefs(context).getString(KEY_TOKEN, null)
        } catch (_: Exception) { null }
    }

    fun getUserId(context: Context): Long = prefs(context).getLong(KEY_USER_ID, -1)

    fun getUserName(context: Context): String = prefs(context).getString(KEY_USER_NAME, "") ?: ""

    fun getUserEmail(context: Context): String = prefs(context).getString(KEY_EMAIL, "") ?: ""

    fun getUserMobile(context: Context): String = prefs(context).getString(KEY_MOBILE, "") ?: ""

    fun getUserRole(context: Context): String = prefs(context).getString(KEY_ROLE, "") ?: ""

    fun getManagerCode(context: Context): String {
        val secureCode = try {
            securePrefs(context).getString(KEY_MANAGER_CODE, null)
        } catch (_: Exception) { null }
        return secureCode ?: ""
    }

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun isTokenExpired(context: Context): Boolean {
        val token = getToken(context) ?: return true
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val exp = JSONObject(payload).optLong("exp", 0)
            val now = System.currentTimeMillis() / 1000
            now >= exp
        } catch (_: Exception) {
            true
        }
    }

    fun clearAuth(context: Context) {
        prefs(context).edit().clear().apply()
        securePrefs(context).edit().clear().apply()
    }

    suspend fun registerDeviceToken(
        context: Context,
        token: String,
        sendWelcomePush: Boolean = false
    ): Boolean {
        if (!isLoggedIn(context)) {
            android.util.Log.w("FCM", "Skipping device-token registration because the user is not authenticated")
            return false
        }

        return try {
            val body = mutableMapOf(
                "token" to token,
                "platform" to "ANDROID"
            )
            if (sendWelcomePush) body["sendWelcomePush"] = "true"

            val response = getApiService(context).registerDeviceToken(body)
            if (response.isSuccessful) {
                android.util.Log.d("FCM", "Device token registered successfully (HTTP ${response.code()})")
                true
            } else {
                android.util.Log.e("FCM", "Device-token registration failed (HTTP ${response.code()})")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Device-token registration request failed: ${e.message}", e)
            false
        }
    }

    /**
     * Removes the FCM device token from the backend on logout. Best-effort:
     * failures are logged only. [jwt] must be captured by the caller BEFORE
     * [clearAuth], because this runs asynchronously after logout state is wiped.
     */
    suspend fun unregisterDeviceToken(context: Context, jwt: String?): Boolean {
        if (jwt.isNullOrBlank()) return false
        val token = try {
            com.google.android.gms.tasks.Tasks.await(
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            )
        } catch (e: Exception) {
            android.util.Log.w("FCM", "Unable to obtain FCM token for unregister", e)
            return false
        }
        if (token.isNullOrBlank()) return false

        return try {
            val response = getApiService(context).removeDeviceToken(
                authorization = "Bearer $jwt",
                body = mapOf("token" to token, "platform" to "ANDROID")
            )
            if (!response.isSuccessful) {
                android.util.Log.w("FCM", "Device-token removal HTTP ${response.code()}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Device-token removal failed: ${e.message}", e)
            false
        }
    }

    // ── Local → Production failover ──────────────────────────────────────────

    /**
     * Application interceptor that fails requests over to production when the
     * local backend is unreachable.
     *
     * - Only active when [BuildConfig.LOCAL_FAILOVER_ENABLED] (LOCAL_BACKEND=true).
     * - Only retries on connection-level failures (ConnectException, timeouts,
     *   UnknownHost, …) — HTTP 4xx/5xx from a live server are NOT failed over.
     * - Sticky: once local fails, subsequent requests go straight to production
     *   for [LOCAL_DOWN_BACKOFF_MS], then local is re-probed automatically.
     * - Shared by the main and document-stream OkHttp clients so both stay in sync.
     */
    private fun failoverInterceptor(): Interceptor? {
        if (!LOCAL_FAILOVER_ENABLED) return null
        val primary = LOCAL_BASE_URL.toHttpUrl()
        val fallback = PROD_BASE_URL.toHttpUrl()

        return Interceptor { chain ->
            var request = chain.request()
            // Ensure the attempt targets the primary (local) base if somehow rewritten.
            if (!isSameOrigin(request.url, primary)) {
                request = rewriteUrl(request, primary)
            }

            val localMarkedDown = System.currentTimeMillis() < localDownUntilMs
            if (localMarkedDown) {
                // Sticky: skip local, go straight to production.
                chain.proceed(rewriteUrl(request, fallback))
            } else {
                try {
                    chain.proceed(request)
                } catch (e: IOException) {
                    if (!isConnectionFailure(e)) throw e
                    localDownUntilMs = System.currentTimeMillis() + LOCAL_DOWN_BACKOFF_MS
                    android.util.Log.w(
                        "ApiClient",
                        "Local backend unreachable (${e.javaClass.simpleName}: ${e.message}) " +
                            "→ failing over to production for ${LOCAL_DOWN_BACKOFF_MS / 1000}s",
                        e
                    )
                    chain.proceed(rewriteUrl(request, fallback))
                }
            }
        }
    }

    private fun isSameOrigin(url: okhttp3.HttpUrl, base: okhttp3.HttpUrl): Boolean =
        url.scheme == base.scheme && url.host == base.host && url.port == base.port

    private fun rewriteUrl(request: Request, base: okhttp3.HttpUrl): Request {
        val rewritten = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return request.newBuilder().url(rewritten).build()
    }

    /** True for failures that mean "could not reach the server at all". */
    private fun isConnectionFailure(e: IOException): Boolean = when (e) {
        is ConnectException,
        is UnknownHostException,
        is NoRouteToHostException,
        is SocketTimeoutException -> true
        else -> {
            val msg = e.message?.lowercase() ?: ""
            msg.contains("failed to connect") ||
                msg.contains("connection refused") ||
                msg.contains("connection reset") ||
                msg.contains("network is unreachable") ||
                msg.contains("software caused connection abort")
        }
        // Mid-stream failures (EOFException, ChunkedSource) are handled by the
        // document-stream retry interceptor, not by failover.
    }

    // ── Retrofit Setup ────────────────────────────────────────────────────────

    private fun provideOkHttpClient(context: Context): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = getToken(context)
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .build()
            } else {
                chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
            }
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Body logging leaks credentials/JWTs to logcat — debug builds only.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // Order: auth → failover (rewrite URL) → logging (sees final URL) → network.
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        failoverInterceptor()?.let { builder.addInterceptor(it) }
        builder.addInterceptor(loggingInterceptor)

        // Shorter connect timeout when failover is enabled so a dead local host
        // fails fast and we can retry against production within a few seconds.
        if (LOCAL_FAILOVER_ENABLED) {
            builder.connectTimeout(FAILOVER_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        }

        return builder.build()
    }

    /**
     * Separate OkHttpClient for document streaming that does NOT log the response body.
     * Using Level.BODY on binary PDF responses can consume the stream or cause OOM.
     * Includes retry logic for chunked encoding errors (EOFException).
     */
    private fun provideDocumentStreamOkHttpClient(context: Context): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = getToken(context)
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .build()
            } else {
                chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
            }
            chain.proceed(request)
        }

        // BASIC in debug (method/URL/status only), NONE in release builds.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        // Retry interceptor for transient network errors (EOFException, etc.)
        val retryInterceptor = Interceptor { chain ->
            var lastException: Exception? = null
            val maxRetries = 2
            
            for (attempt in 1..maxRetries) {
                try {
                    return@Interceptor chain.proceed(chain.request())
                } catch (e: Exception) {
                    lastException = e
                    // Retry mid-stream glitches only. Do NOT retry SocketTimeoutException —
                    // that would multiply the short connect timeout before failover runs.
                    val isRetryable = e is java.io.EOFException ||
                        (e is java.io.InterruptedIOException && e !is SocketTimeoutException) ||
                        (e.message?.contains("ChunkedSource") == true)

                    if (isRetryable && attempt < maxRetries) {
                        try { Thread.sleep(500L * attempt) } catch (_: InterruptedException) { return@Interceptor chain.proceed(chain.request()) }
                        continue
                    }
                    throw e
                }
            }
            throw lastException ?: Exception("Unknown error")
        }

        // Order: auth → failover (URL) → retry (mid-stream EOF) → logging → network.
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // Longer read timeout for large files
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)  // Enable automatic retry on connection failure

        failoverInterceptor()?.let { builder.addInterceptor(it) }
        builder
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)

        if (LOCAL_FAILOVER_ENABLED) {
            builder.connectTimeout(FAILOVER_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        }

        return builder.build()
    }

    private fun provideRetrofit(context: Context): Retrofit {
        return synchronized(this) {
            retrofit ?: run {
                val okHttpClient = provideOkHttpClient(context.applicationContext)
                val instance = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                retrofit = instance
                instance
            }
        }
    }

    /**
     * Separate Retrofit instance for document streaming.
     * Uses Level.BASIC logging (not BODY) to avoid consuming binary streams.
     * Still includes GsonConverterFactory for JSON responses like requestViewToken().
     */
    private fun provideDocumentStreamRetrofit(context: Context): Retrofit {
        return synchronized(this) {
            documentStreamRetrofit ?: run {
                val okHttpClient = provideDocumentStreamOkHttpClient(context.applicationContext)
                val instance = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                documentStreamRetrofit = instance
                instance
            }
        }
    }

    fun getApiService(context: Context): ApiService {
        return synchronized(this) {
            apiService ?: run {
                val retrofitInstance = provideRetrofit(context)
                retrofitInstance.create(ApiService::class.java).also { apiService = it }
            }
        }
    }

    fun getDocumentStreamApi(context: Context): DocumentStreamApi {
        return synchronized(this) {
            documentStreamApi ?: run {
                val retrofitInstance = provideDocumentStreamRetrofit(context)
                retrofitInstance.create(DocumentStreamApi::class.java).also { documentStreamApi = it }
            }
        }
    }
}
