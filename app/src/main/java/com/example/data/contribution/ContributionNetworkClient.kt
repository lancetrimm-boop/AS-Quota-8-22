package com.example.data.contribution

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Client-side configuration boundary for the Aura Contribution Network Layer (Phase 3B.3).
 * Provides secure OkHttp and Retrofit initialization without hardcoding active live endpoints.
 */
object ContributionNetworkClient {

    private const val DEFAULT_BASE_URL = "https://api.aura.intelligence/"

    private val moshi = createMoshi()
    private val okHttpClient = createOkHttpClient()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(DEFAULT_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(ContributionApiService::class.java)

    /**
     * Returns the singleton production API service.
     */
    fun getApiService(): ContributionApiService = apiService

    fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun createMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    fun createApiService(
        baseUrl: String = DEFAULT_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient(),
        moshi: Moshi = createMoshi()
    ): ContributionApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(ContributionApiService::class.java)
    }
}
