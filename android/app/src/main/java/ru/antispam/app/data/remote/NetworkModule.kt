package ru.antispam.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * Serverless Autonomous CDN Mode:
     * When true (default), the app operates fully autonomously without requiring a dedicated
     * self-hosted REST API backend. Numbering plan updates are fetched directly over HTTPS from
     * GitHub Releases Global CDN (https://github.com/Mishakorobov/Antispam/releases/download/latest-data/pools.aspb).
     */
    var isServerlessMode: Boolean = true

    // 10.0.2.2 is Android Emulator's default alias for host machine localhost
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun createApiService(baseUrl: String = DEFAULT_BASE_URL): AntiSpamApiService {
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        return retrofit.create(AntiSpamApiService::class.java)
    }

    val apiService: AntiSpamApiService by lazy {
        createApiService()
    }
}
