package com.example.spbus.data

import com.example.spbus.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ThingSpeakService {
    private val channelId = BuildConfig.THINGSPEAK_CHANNEL_ID.trim()
    private val readKey = BuildConfig.THINGSPEAK_READ_API_KEY.trim()
    private val writeKey = BuildConfig.THINGSPEAK_WRITE_API_KEY.trim()
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()

    val isConfigured: Boolean get() = channelId.isNotBlank()

    fun updateWalkingDistance(distanceMeters: Int, callback: (String?) -> Unit) {
        if (channelId.isBlank() || writeKey.isBlank()) {
            callback("Configure thingspeak.channel_id e thingspeak.write_api_key em local.properties.")
            return
        }
        val url = "https://api.thingspeak.com/update".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", writeKey)
            .addQueryParameter("field1", distanceMeters.coerceAtLeast(0).toString())
            .build()
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = callback(e.localizedMessage ?: "Falha ao enviar distância ao ThingSpeak.")
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    val entryId = it.body?.string()?.trim()?.toLongOrNull()
                    callback(if (it.isSuccessful && entryId != null && entryId > 0) null else "ThingSpeak não aceitou a leitura. Aguarde o intervalo mínimo de gravação e tente novamente.")
                }
            }
        })
    }

    fun loadDistanceHistory(callback: (ThingSpeakDistanceFeed?, String?) -> Unit) {
        if (channelId.isBlank()) { callback(null, "Configure thingspeak.channel_id em local.properties."); return }
        val urlBuilder = "https://api.thingspeak.com/channels/$channelId/feeds.json".toHttpUrl().newBuilder()
            .addQueryParameter("results", "48")
        if (readKey.isNotBlank()) urlBuilder.addQueryParameter("api_key", readKey)
        client.newCall(Request.Builder().url(urlBuilder.build()).get().build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = callback(null, e.localizedMessage ?: "Falha ao ler o ThingSpeak.")
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { callback(null, "ThingSpeak respondeu ${it.code}."); return }
                    try {
                        val json = JSONObject(it.body?.string().orEmpty())
                        val feeds = json.optJSONArray("feeds")
                        val readings = (0 until (feeds?.length() ?: 0)).mapNotNull { index ->
                            val feed = feeds?.optJSONObject(index) ?: return@mapNotNull null
                            val meters = feed.optString("field1").toDoubleOrNull()?.takeIf(Double::isFinite)?.toInt() ?: return@mapNotNull null
                            ThingSpeakDistanceReading(feed.optString("created_at"), meters.coerceAtLeast(0))
                        }
                        callback(ThingSpeakDistanceFeed(readings, channelId), null)
                    } catch (_: Exception) { callback(null, "Resposta ThingSpeak inválida.") }
                }
            }
        })
    }

}