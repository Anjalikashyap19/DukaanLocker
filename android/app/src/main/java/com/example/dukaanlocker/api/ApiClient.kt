package com.example.dukaanlocker.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://api.dukaanlocker.com/"
    private const val PREFS_NAME = "dukaan_api_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_MOBILE = "user_mobile"
    private const val KEY_ROLE = "user_role"

    @Volatile private var apiService: ApiService? = null
    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var documentStreamApi: DocumentStreamApi? = null

    // ── Token Management ──────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAuth(context: Context, response: AuthResponse) {
        prefs(context).edit().apply {
            putString(KEY_TOKEN, response.token)
            putLong(KEY_USER_ID, response.userId)
            putString(KEY_USER_NAME, response.userName)
            putString(KEY_EMAIL, response.emailId)
            putString(KEY_MOBILE, response.mobileNumber)
            putString(KEY_ROLE, response.role)
            apply()
        }
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun getUserId(context: Context): Long = prefs(context).getLong(KEY_USER_ID, -1)

    fun getUserName(context: Context): String = prefs(context).getString(KEY_USER_NAME, "") ?: ""

    fun getUserEmail(context: Context): String = prefs(context).getString(KEY_EMAIL, "") ?: ""

    fun getUserMobile(context: Context): String = prefs(context).getString(KEY_MOBILE, "") ?: ""

    fun getUserRole(context: Context): String = prefs(context).getString(KEY_ROLE, "") ?: ""

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun clearAuth(context: Context) {
        prefs(context).edit().clear().apply()
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
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
                val retrofitInstance = provideRetrofit(context)
                retrofitInstance.create(DocumentStreamApi::class.java).also { documentStreamApi = it }
            }
        }
    }
}
