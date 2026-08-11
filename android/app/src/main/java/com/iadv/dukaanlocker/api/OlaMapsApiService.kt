package com.iadv.dukaanlocker.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import java.util.concurrent.TimeUnit

interface OlaMapsApiService {

    @GET("places/v1/autocomplete")
    suspend fun autocomplete(
        @Query("input") input: String,
        @Query("api_key") apiKey: String
    ): Response<OlaAutocompleteResponse>
}

object OlaMapsClient {
    private const val BASE_URL = "https://api.olamaps.io/"

    val apiService: OlaMapsApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OlaMapsApiService::class.java)
    }
}
