package xyz.lisfox.big.notenoughfakeqqbot.data.api

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private var _api: FakeQqBotApi? = null
    private var _baseUrl: String = ""
    private var _token: String = ""

    val api: FakeQqBotApi
        get() = _api ?: throw IllegalStateException("API not initialized. Call configure() first.")

    val isConfigured: Boolean
        get() = _api != null

    val baseUrl: String get() = _baseUrl
    val token: String get() = _token

    fun configure(serverUrl: String, token: String) {
        _baseUrl = serverUrl.trimEnd('/')
        _token = token

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .apply {
                    if (token.isNotBlank()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val baseApiUrl = "$_baseUrl/"

        _api = Retrofit.Builder()
            .baseUrl(baseApiUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FakeQqBotApi::class.java)
    }

    fun reset() {
        _api = null
        _baseUrl = ""
        _token = ""
    }
}
