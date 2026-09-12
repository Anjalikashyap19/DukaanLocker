package com.iadv.dukaanlocker.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

import android.util.Base64
import com.iadv.dukaanlocker.BuildConfig
import org.json.JSONObject


object ApiClient {

    private const val BASE_URL = BuildConfig.BASE_URL
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
            }
        }
    }

    fun saveAuth(context: Context, response: AuthResponse) {
        securePrefs(context).edit().apply {
            putString(KEY_TOKEN, response.token)
            putString(KEY_MANAGER_CODE, response.managerCode ?: "")
            apply()
        }
        prefs(context).edit().apply {
            putLong(KEY_USER_ID, response.userId)
            putString(KEY_USER_NAME, response.userName)
            putString(KEY_EMAIL, response.emailId)
            putString(KEY_MOBILE, response.mobileNumber)
            putString(KEY_ROLE, response.role)
            apply()
        }
    }

    fun getToken(context: Context): String? = securePrefs(context).getString(KEY_TOKEN, null)

    fun getUserId(context: Context): Long = prefs(context).getLong(KEY_USER_ID, -1)

    fun getUserName(context: Context): String = prefs(context).getString(KEY_USER_NAME, "") ?: ""

    fun getUserEmail(context: Context): String = prefs(context).getString(KEY_EMAIL, "") ?: ""

    fun getUserMobile(context: Context): String = prefs(context).getString(KEY_MOBILE, "") ?: ""

    fun getUserRole(context: Context): String = prefs(context).getString(KEY_ROLE, "") ?: ""

    fun getManagerCode(context: Context): String = securePrefs(context).getString(KEY_MANAGER_CODE, "") ?: ""

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

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
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
                    val isRetryable = e is java.io.EOFException ||
                        e is java.io.InterruptedIOException ||
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

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // Longer read timeout for large files
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)  // Enable automatic retry on connection failure
            .build()
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
