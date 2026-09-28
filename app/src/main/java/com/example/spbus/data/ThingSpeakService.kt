package com.example.spbus.data

import com.example.spbus.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ThingSpeakService {
    private val channelId = BuildConfig.THINGSPEAK_CHANNEL_ID.trim()
    private val readKey = BuildConfig.THINGSPEAK_READ_API_KEY.trim()
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()

    fun loadTelemetry(callback: (FleetTelemetry?, String?) -> Unit) {
        if (channelId.isBlank()) { callback(null, "Configure o canal ThingSpeak em secrets.properties."); return }
        val keyQuery = if (readKey.isNotBlank()) "&api_key=$readKey" else ""
        val request = Request.Builder()
            .url("https://api.thingspeak.com/channels/$channelId/feeds.json?results=48$keyQuery")
            .get().build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = callback(null, e.localizedMessage ?: "Falha de conexão com ThingSpeak.")
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { callback(null, "ThingSpeak respondeu ${it.code}."); return }
                    try {
                        val json = JSONObject(it.body?.string().orEmpty())
                        val feeds = json.optJSONArray("feeds")
                        val readings = (0 until (feeds?.length() ?: 0)).mapNotNull { index ->
                            val item = feeds?.optJSONObject(index) ?: return@mapNotNull null
                            ThingSpeakReading(
                                createdAt = item.optString("created_at"),
                                occupancy = item.optString("field1").toDoubleOrNull(),
                                speedKmh = item.optString("field2").toDoubleOrNull(),
                                temperatureCelsius = item.optString("field3").toDoubleOrNull()
                            )
                        }
                        callback(FleetTelemetry(readings, channelId), null)
                    } catch (error: Exception) { callback(null, "Resposta ThingSpeak inválida.") }
                }
            }
        })
    }
}