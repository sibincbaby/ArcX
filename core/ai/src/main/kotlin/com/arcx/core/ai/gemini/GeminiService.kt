package com.arcx.core.ai.gemini

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * No converter factory is installed anywhere in the app, so every call hands back the raw
 * [ResponseBody] and decoding happens against the shared Json instance.
 */
internal interface GeminiService {

    /**
     * `alt=sse` is not optional: without it the endpoint answers with a chunked JSON array
     * instead of server-sent events and the line parser silently produces nothing.
     */
    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent?alt=sse")
    fun streamGenerateContent(
        @Path("model") model: String,
        // Header rather than ?key=: query strings end up in logs, proxies and crash reports.
        @Header("x-goog-api-key") apiKey: String,
        @Body body: RequestBody,
    ): Call<ResponseBody>

    @GET("v1beta/models")
    fun listModels(
        @Header("x-goog-api-key") apiKey: String,
    ): Call<ResponseBody>
}
