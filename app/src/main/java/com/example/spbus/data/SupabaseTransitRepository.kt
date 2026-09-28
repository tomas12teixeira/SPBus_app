package com.example.spbus.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.spbus.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SupabaseTransitRepository {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    fun loadBuses(callback: (List<BusPosition>, String?) -> Unit) {
        getRows("buses", "id,line_code,lat,lng,speed,updated_at", "order=updated_at.desc") { rows, error ->
            callback(rows?.mapNotNull(::parseBus).orEmpty(), error)
        }
    }

    fun loadBusStops(callback: (List<BusStopRecord>, String?) -> Unit) {
        getRows("bus_stops", "id,name,lat,lng,lines", "limit=500") { rows, error ->
            val stops = rows?.mapNotNull { row ->
                val lat = row.optDouble("lat", Double.NaN)
                val lng = row.optDouble("lng", Double.NaN)
                if (!lat.isFinite() || !lng.isFinite() || kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180) null
                else BusStopRecord(row.optString("id"), row.optString("name"), lat, lng, row.optJSONArray("lines").toStringList())
            }.orEmpty()
            callback(stops, error)
        }
    }

    fun loadFeedback(callback: (List<PassengerFeedback>, String?) -> Unit) {
        getRows("feedbacks", "id,line_code,status,comment,created_at", "order=created_at.desc&limit=20") { rows, error ->
            callback(rows?.mapNotNull(::parseFeedback).orEmpty(), error)
        }
    }

    fun submitFeedback(lineCode: String, status: String, comment: String, callback: (String?) -> Unit) {
        if (!isConfigured) { callback("Configure o Supabase no arquivo secrets.properties para enviar relatos."); return }
        val body = JSONObject().put("line_code", lineCode).put("status", status).put("comment", comment)
        val request = Request.Builder().url("$baseUrl/rest/v1/feedbacks")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Prefer", "return=minimal")
            .post(okhttp3.RequestBody.create(JSON, body.toString()))
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = callback(e.localizedMessage ?: "Falha de rede.")
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use { callback(if (it.isSuccessful) null else "Supabase recusou o relato (${it.code}).") }
            }
        })
    }

    fun connectRealtime(listener: SupabaseRealtimeListener): SupabaseRealtimeClient? {
        if (!isConfigured) return null
        return SupabaseRealtimeClient(baseUrl, anonKey, listener).also { it.connect() }
    }

    private fun getRows(table: String, select: String, query: String, callback: (List<JSONObject>?, String?) -> Unit) {
        if (!isConfigured) { callback(null, null); return }
        val url = "$baseUrl/rest/v1/$table?select=$select&$query"
        val request = Request.Builder().url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .get().build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = callback(null, e.localizedMessage ?: "Falha de rede.")
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { callback(null, "Supabase respondeu ${it.code}."); return }
                    try {
                        val json = JSONArray(it.body?.string().orEmpty())
                        callback((0 until json.length()).map { index -> json.getJSONObject(index) }, null)
                    } catch (error: Exception) { callback(null, "Resposta Supabase inválida.") }
                }
            }
        })
    }

    private fun parseBus(row: JSONObject): BusPosition? {
        val lat = row.optDouble("lat", Double.NaN)
        val lng = row.optDouble("lng", Double.NaN)
        val speed = row.optDouble("speed", 0.0)
        if (!lat.isFinite() || !lng.isFinite() || kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180 || speed < 0) return null
        return BusPosition(row.optString("id"), row.optString("line_code"), lat, lng, speed, row.optString("updated_at"))
    }

    private fun parseFeedback(row: JSONObject): PassengerFeedback? {
        val id = row.optString("id")
        if (id.isBlank()) return null
        return PassengerFeedback(id, row.optString("line_code"), row.optString("status"), row.optString("comment"), row.optString("created_at"))
    }

    private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.orEmpty()

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}

interface SupabaseRealtimeListener {
    fun onBusChanged(bus: BusPosition?, deletedId: String?)
    fun onFeedbackChanged(feedback: PassengerFeedback?, deletedId: String?)
    fun onConnectionChanged(connected: Boolean)
}

class SupabaseRealtimeClient(
    private val baseUrl: String,
    private val anonKey: String,
    private val listener: SupabaseRealtimeListener
) {
    private val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var stopped = false
    private var reference = 1
    private var heartbeat: Runnable? = null
    private var reconnect: Runnable? = null

    fun connect() {
        stopped = false
        val host = baseUrl.replaceFirst(Regex("^https?://"), "wss://").trimEnd('/')
        val url = "$host/realtime/v1/websocket?apikey=${URLEncoder.encode(anonKey, "UTF-8")}&vsn=1.0.0"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                reference = 1
                listener.onConnectionChanged(true)
                val changes = JSONArray()
                    .put(JSONObject().put("event", "*").put("schema", "public").put("table", "buses"))
                    .put(JSONObject().put("event", "*").put("schema", "public").put("table", "feedbacks"))
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", changes)
                val join = JSONObject().put("topic", "realtime:spbus-android")
                    .put("event", "phx_join")
                    .put("payload", JSONObject().put("config", config).put("access_token", anonKey))
                    .put("ref", reference.toString()).put("join_ref", reference.toString())
                webSocket.send(join.toString())
                scheduleHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) { parseRealtimeMessage(text) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { connectionLost() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("SPBusRealtime", "Realtime connection failed", t)
                connectionLost()
            }
        })
    }

    fun close() {
        stopped = true
        heartbeat?.let(mainHandler::removeCallbacks)
        reconnect?.let(mainHandler::removeCallbacks)
        socket?.close(1000, "Activity paused")
        socket = null
        listener.onConnectionChanged(false)
    }

    private fun scheduleHeartbeat() {
        heartbeat?.let(mainHandler::removeCallbacks)
        heartbeat = Runnable {
            val message = JSONObject().put("topic", "phoenix").put("event", "heartbeat")
                .put("payload", JSONObject()).put("ref", (++reference).toString())
            socket?.send(message.toString())
            if (!stopped) scheduleHeartbeat()
        }
        mainHandler.postDelayed(heartbeat!!, 25000)
    }

    private fun connectionLost() {
        listener.onConnectionChanged(false)
        heartbeat?.let(mainHandler::removeCallbacks)
        if (stopped) return
        reconnect?.let(mainHandler::removeCallbacks)
        reconnect = Runnable { if (!stopped) connect() }
        mainHandler.postDelayed(reconnect!!, 5000)
    }

    private fun parseRealtimeMessage(raw: String) {
        try {
            val envelope = JSONObject(raw)
            if (envelope.optString("event") != "postgres_changes") return
            val data = envelope.optJSONObject("payload")?.optJSONObject("data") ?: return
            val table = data.optString("table")
            val event = data.optString("type").uppercase()
            val row = if (event == "DELETE") data.optJSONObject("old_record") else data.optJSONObject("record")
            if (row == null) return
            if (table == "buses") {
                val lat = row.optDouble("lat", Double.NaN)
                val lng = row.optDouble("lng", Double.NaN)
                val speed = row.optDouble("speed", 0.0)
                val bus = if (event != "DELETE" && lat.isFinite() && lng.isFinite() && kotlin.math.abs(lat) <= 90 && kotlin.math.abs(lng) <= 180 && speed >= 0) {
                    BusPosition(row.optString("id"), row.optString("line_code"), lat, lng, speed, row.optString("updated_at"))
                } else null
                mainHandler.post { listener.onBusChanged(bus, if (event == "DELETE") row.optString("id") else null) }
            } else if (table == "feedbacks") {
                val feedback = if (event == "DELETE") null else PassengerFeedback(row.optString("id"), row.optString("line_code"), row.optString("status"), row.optString("comment"), row.optString("created_at"))
                mainHandler.post { listener.onFeedbackChanged(feedback, if (event == "DELETE") row.optString("id") else null) }
            }
        } catch (error: Exception) { Log.w("SPBusRealtime", "Could not parse realtime event", error) }
    }
}