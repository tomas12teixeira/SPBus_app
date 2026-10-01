package com.example.spbus.data

import com.example.spbus.BuildConfig
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SpTransService {
    private val baseUrl = "https://api.olhovivo.sptrans.com.br/v2.1/".toHttpUrl()
    private val token = BuildConfig.SPTRANS_TOKEN.trim()
    private val authLock = Any()
    private var authenticated = false
    private var authenticating = false
    private val authCallbacks = mutableListOf<(String?) -> Unit>()
    private val cookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this.cookies) {
                this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain } }
                this.cookies.addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
            cookies.filter { it.matches(url) }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = token.isNotBlank()

    fun searchLines(query: String, callback: (List<SpTransLine>?, String?) -> Unit) {
        getJsonArray("Linha/Buscar", mapOf("termosBusca" to query)) { array, error ->
            callback(array?.let(::parseLines), error)
        }
    }

    fun searchStops(query: String, callback: (List<SpTransStop>?, String?) -> Unit) {
        getJsonArray("Parada/Buscar", mapOf("termosBusca" to query)) { array, error ->
            callback(array?.let(::parseStops), error)
        }
    }

    fun stopsForLine(lineCode: Int, callback: (List<SpTransStop>?, String?) -> Unit) {
        getJsonArray("Parada/BuscarParadasPorLinha", mapOf("codigoLinha" to lineCode.toString())) { array, error ->
            callback(array?.let(::parseStops), error)
        }
    }

    fun vehiclesForLine(lineCode: Int, callback: (List<SpTransVehicle>?, String?) -> Unit) {
        getJsonObject("Posicao/Linha", mapOf("codigoLinha" to lineCode.toString())) { json, error ->
            val vehicles = json?.optJSONArray("vs")?.let(::parseVehicles).orEmpty()
            callback(vehicles, error)
        }
    }

    fun lineCodesAtStop(stopCode: Int, callback: (Set<Int>?, String?) -> Unit) {
        getJsonObject("Previsao/Parada", mapOf("codigoParada" to stopCode.toString())) { json, error ->
            val codes = json?.optJSONObject("p")?.optJSONArray("l")?.let { lines ->
                (0 until lines.length()).mapNotNull { lines.optJSONObject(it)?.optInt("cl")?.takeIf { code -> code > 0 } }.toSet()
            }
            callback(codes, error)
        }
    }

    fun linesAtStop(stopCode: Int, callback: (List<SpTransLine>?, String?) -> Unit) {
        getJsonObject("Previsao/Parada", mapOf("codigoParada" to stopCode.toString())) { json, error ->
            val lines = json?.optJSONObject("p")?.optJSONArray("l")?.let(::parsePredictionLines).orEmpty()
            callback(lines, error)
        }
    }

    fun arrivals(stopCode: Int, lineCode: Int, callback: (List<String>?, String?) -> Unit) {
        getJsonObject("Previsao", mapOf("codigoParada" to stopCode.toString(), "codigoLinha" to lineCode.toString())) { json, error ->
            val lineRows = json?.optJSONObject("p")?.optJSONArray("l")
            val line = (0 until (lineRows?.length() ?: 0)).mapNotNull { lineRows?.optJSONObject(it) }
                .firstOrNull { it.optInt("cl") == lineCode }
            val vehicles = line?.optJSONArray("vs")
            val times = (0 until (vehicles?.length() ?: 0)).mapNotNull { index ->
                vehicles?.optJSONObject(index)?.optString("t")?.takeIf { it.isNotBlank() }
            }
            callback(times, error)
        }
    }

    private fun getJsonArray(path: String, query: Map<String, String>, callback: (JSONArray?, String?) -> Unit) {
        request(path, query, post = false) { body, error ->
            if (body == null) callback(null, error)
            else try { callback(JSONArray(body), null) } catch (_: Exception) { callback(null, "A SPTrans retornou uma lista inválida.") }
        }
    }

    private fun getJsonObject(path: String, query: Map<String, String>, callback: (JSONObject?, String?) -> Unit) {
        request(path, query, post = false) { body, error ->
            if (body == null) callback(null, error)
            else try { callback(JSONObject(body), null) } catch (_: Exception) { callback(null, "A SPTrans retornou dados inválidos.") }
        }
    }

    private fun request(path: String, query: Map<String, String>, post: Boolean, callback: (String?, String?) -> Unit) {
        withAuthentication { authError ->
            if (authError != null) { callback(null, authError); return@withAuthentication }
            val builder = baseUrl.newBuilder().addPathSegments(path)
            query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            val requestBuilder = Request.Builder().url(builder.build()).header("Accept", "application/json")
            val request = if (post) requestBuilder.post(ByteArray(0).toRequestBody(null)).build() else requestBuilder.get().build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) = callback(null, e.localizedMessage ?: "Falha de conexão com a SPTrans.")
                override fun onResponse(call: okhttp3.Call, response: Response) {
                    response.use {
                        if (it.code == 401 || it.code == 403) {
                            synchronized(authLock) { authenticated = false }
                            callback(null, "A sessão Olho Vivo expirou. Confira o token SPTrans.")
                        } else if (!it.isSuccessful) callback(null, "SPTrans respondeu ${it.code}.")
                        else callback(it.body?.string(), null)
                    }
                }
            })
        }
    }

    private fun withAuthentication(callback: (String?) -> Unit) {
        if (token.isBlank()) { callback("Configure sptrans.token em local.properties do Android Studio."); return }
        synchronized(authLock) {
            if (authenticated) { callback(null); return }
            authCallbacks.add(callback)
            if (authenticating) return
            authenticating = true
        }

        val encodedToken = URLEncoder.encode(token, "UTF-8")
        val request = Request.Builder()
            .url("${baseUrl}Login/Autenticar?token=$encodedToken")
            .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = completeAuthentication(e.localizedMessage ?: "Falha ao autenticar na SPTrans.")
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    val success = it.isSuccessful && it.body?.string()?.trim()?.equals("true", ignoreCase = true) == true
                    completeAuthentication(if (success) null else "Token SPTrans inválido ou não autorizado.")
                }
            }
        })
    }

    private fun completeAuthentication(error: String?) {
        val callbacks: List<(String?) -> Unit>
        synchronized(authLock) {
            authenticated = error == null
            authenticating = false
            callbacks = authCallbacks.toList()
            authCallbacks.clear()
        }
        callbacks.forEach { it(error) }
    }

    private fun parseLines(array: JSONArray): List<SpTransLine> = (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val code = row.optInt("cl")
        if (code <= 0) return@mapNotNull null
        val prefix = row.optString("lt")
        val suffix = row.optInt("tl").toString().padStart(2, '0')
        SpTransLine(code, "$prefix-$suffix", row.optInt("sl"), row.optBoolean("lc"), row.optString("tp"), row.optString("ts"))
    }

    private fun parsePredictionLines(array: JSONArray): List<SpTransLine> = (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val code = row.optInt("cl")
        if (code <= 0) return@mapNotNull null
        val lineText = row.optString("c")
        val parts = lineText.split('-')
        SpTransLine(
            code,
            lineText.ifBlank { code.toString() },
            row.optInt("sl"),
            false,
            row.optString("lt1").ifBlank { parts.firstOrNull().orEmpty() },
            row.optString("lt0").ifBlank { parts.getOrNull(1).orEmpty() }
        )
    }

    private fun parseStops(array: JSONArray): List<SpTransStop> = (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val lat = row.optDouble("py", Double.NaN)
        val lng = row.optDouble("px", Double.NaN)
        val code = row.optInt("cp")
        if (code <= 0 || !lat.isFinite() || !lng.isFinite() || kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180) return@mapNotNull null
        SpTransStop(code, row.optString("np"), row.optString("ed"), lat, lng)
    }

    private fun parseVehicles(array: JSONArray): List<SpTransVehicle> = (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val lat = row.optDouble("py", Double.NaN)
        val lng = row.optDouble("px", Double.NaN)
        if (!lat.isFinite() || !lng.isFinite() || kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180) return@mapNotNull null
        SpTransVehicle(row.optString("p"), row.optBoolean("a"), row.optString("ta"), lat, lng)
    }

    companion object { private val JSON_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType() }
}