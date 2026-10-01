package com.example.spbus.data

import com.example.spbus.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ThingSpeakService {
    private val channelId = BuildConfig.THINGSPEAK_CHANNEL_ID.trim()
    private val readKey = BuildConfig.THINGSPEAK_READ_API_KEY.trim()
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()

    val isConfigured: Boolean get() = channelId.isNotBlank() && readKey.isNotBlank()

    fun loadChannelData(callback: (ThingSpeakFeed?, String?) -> Unit) {
        if (!isConfigured) {
            callback(null, "Configure o Channel ID e a chave de leitura do ThingSpeak em local.properties.")
            return
        }
        val url = "https://api.thingspeak.com/channels/$channelId/feeds.json".toHttpUrl().newBuilder()
            .addQueryParameter("results", "48")
            .addQueryParameter("api_key", readKey)
            .build()
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(null, "Falha de conexão com o ThingSpeak.")
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(null, "ThingSpeak respondeu ${it.code}.")
                        return
                    }
                    try {
                        val json = JSONObject(it.body?.string().orEmpty())
                        val channel = json.optJSONObject("channel") ?: JSONObject()
                        val feeds = json.optJSONArray("feeds") ?: JSONArray()
                        val fields = (1..8).mapNotNull { number ->
                            channel.optString("field$number")
                                .takeIf(String::isNotBlank)
                                ?.let { label -> ThingSpeakField(number, label) }
                        }
                        val readings = (0 until feeds.length()).mapNotNull { index ->
                            val feed = feeds.optJSONObject(index) ?: return@mapNotNull null
                            val values = fields.mapNotNull { field ->
                                feed.optString("field${field.number}").toDoubleOrNull()
                                    ?.takeIf(Double::isFinite)
                                    ?.let { field.number to it }
                            }.toMap()
                            ThingSpeakReading(feed.optString("created_at"), values)
                        }
                        callback(
                            ThingSpeakFeed(
                                readings = readings,
                                channelId = channel.optString("id", channelId),
                                channelName = channel.optString("name").ifBlank { "ThingSpeak" },
                                fields = fields
                            ),
                            null
                        )
                    } catch (_: Exception) {
                        callback(null, "Resposta ThingSpeak inválida.")
                    }
                }
            }
        })
    }
}