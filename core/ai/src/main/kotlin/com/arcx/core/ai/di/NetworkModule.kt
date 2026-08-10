package com.arcx.core.ai.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** One client and one [Json] for every provider, so connection pools and threads are shared. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Providers add fields constantly (safetyRatings, modelVersion, ...); unknown keys must
        // never fail a running stream.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Responses stream: the gap between two SSE frames is what has to fit in the read
            // timeout, and the call as a whole must not be capped at all.
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .apply {
                if (context.isDebuggable()) addInterceptor(loggingInterceptor())
            }
            .build()

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        // BYOK keys travel in headers; logcat is world-readable on a rooted device.
        redactHeader("x-goog-api-key")
        redactHeader("Authorization")
    }

    // The library has no BuildConfig of its own worth trusting, and the host app's debuggable
    // flag is the thing that actually decides whether logs are safe.
    private fun Context.isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 120L
}
