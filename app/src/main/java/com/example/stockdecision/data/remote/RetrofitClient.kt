package com.example.stockdecision.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client singleton for API calls
 */
object RetrofitClient {
    
    private const val TIMEOUT_SECONDS = 30L
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    // Alpha Vantage API (for historical data)
    private val alphaVantageRetrofit = Retrofit.Builder()
        .baseUrl(StockApiService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    // Cloud API (for stock monitoring)
    private val cloudRetrofit = Retrofit.Builder()
        .baseUrl(CloudApiService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val stockApiService: StockApiService = alphaVantageRetrofit.create(StockApiService::class.java)
    
    val cloudApiService: CloudApiService = cloudRetrofit.create(CloudApiService::class.java)
}
