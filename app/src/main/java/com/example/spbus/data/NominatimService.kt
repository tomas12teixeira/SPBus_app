package com.example.spbus.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AddressSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

class NominatimService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    fun searchAddress(query: String, callback: (List<AddressSuggestion>, String?) -> Unit) {
        val trimmed = query.trim()
        if (trimmed.length < 3) {
            callback(emptyList(), null)
            return
        }
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmed)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", "5")
            .addQueryParameter("countrycodes", "br")
            .addQueryParameter("viewbox", "-47.0,-23.0,-46.0,-24.0")
            .addQueryParameter("bounded", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SPBusAndroid/1.0")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(emptyList(), "Falha ao pesquisar endereços. Tente novamente.")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(emptyList(), "Pesquisa de endereços respondeu ${it.code}.")
                        return
                    }
                    try {
                        val rows = JSONArray(it.body?.string().orEmpty())
                        val suggestions = (0 until rows.length()).mapNotNull { index ->
                            val row = rows.optJSONObject(index) ?: return@mapNotNull null
                            val latitude = row.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                            val longitude = row.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                            val name = row.optString("display_name").takeIf(String::isNotBlank) ?: return@mapNotNull null
                            if (!latitude.isFinite() || !longitude.isFinite()) return@mapNotNull null
                            AddressSuggestion(name, latitude, longitude)
                        }
                        callback(suggestions, null)
                    } catch (_: Exception) {
                        callback(emptyList(), "A pesquisa de endereços retornou dados inválidos.")
                    }
                }
            }
        })
    }
}
