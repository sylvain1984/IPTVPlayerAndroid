package com.iptv.player.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.iptv.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class RTCTokenCredentials(
    val appId: String,
    val token: String
)

object RTCTokenService {
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private data class TokenRequest(
        val roomId: String,
        val userId: String,
        val role: String
    )

    private data class TokenResponse(
        val token: String,
        @SerializedName("appId") val appId: String?
    )

    suspend fun fetch(
        client: OkHttpClient,
        roomId: String,
        userId: String,
        role: String
    ): RTCTokenCredentials = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.RTC_TOKEN_URL
        require(endpoint.isNotBlank()) { "RTC_TOKEN_URL is missing" }

        val body = gson.toJson(TokenRequest(roomId, userId, role)).toRequestBody(jsonType)
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Token request failed: ${response.code}")
            response.body?.string() ?: error("Token response body is empty")
        }
        val response = gson.fromJson(responseBody, TokenResponse::class.java)
        val appId = response.appId?.takeIf { it.isNotBlank() } ?: BuildConfig.RTC_APP_ID
        require(appId.isNotBlank() && response.token.isNotBlank()) { "Token response is invalid" }
        RTCTokenCredentials(appId, response.token)
    }
}
