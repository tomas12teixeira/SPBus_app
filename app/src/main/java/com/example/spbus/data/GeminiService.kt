package com.example.spbus.data

import com.example.spbus.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeminiService {
    private val apiKey = BuildConfig.GEMINI_API_KEY.trim()
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    fun ask(question: String, recentFeedback: List<PassengerFeedback>, callback: (String?, String?) -> Unit) {
        if (apiKey.isBlank()) { callback(null, "Configure GEMINI_API_KEY em secrets.properties."); return }
        if (question.isBlank()) { callback(null, "Digite uma pergunta para o assistente."); return }

        val feedbackContext = if (recentFeedback.isEmpty()) "Sem relatos recentes disponíveis." else recentFeedback.take(10)
            .joinToString("\n") { "${it.lineCode}: ${it.status} — ${it.comment}" }
        val instruction = "Você é o assistente de mobilidade do SPBus para São Paulo. Responda em português brasileiro, com clareza e concisão. Ajude a interpretar linhas, integrações e trajetos, mas não invente horários, itinerários, posições de veículos ou previsões oficiais. Os relatos abaixo são comunitários e podem estar desatualizados; apresente-os como indícios. Se faltarem origem, destino ou linha, pergunte. Recomende confirmar horários oficiais com a operadora.\nRelatos recentes:\n$feedbackContext"
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question.take(1500))))))
            .put("generationConfig", JSONObject().put("temperature", 0.35).put("maxOutputTokens", 500))
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$encodedKey")
            .post(okhttp3.RequestBody.create(JSON, body.toString()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = callback(null, e.localizedMessage ?: "Falha de conexão com Gemini.")
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { callback(null, "Gemini respondeu ${it.code}. Confira a chave e o modelo configurado."); return }
                    try {
                        val json = JSONObject(it.body?.string().orEmpty())
                        val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        val answer = (0 until (parts?.length() ?: 0)).mapNotNull { index -> parts?.optJSONObject(index)?.optString("text") }
                            .joinToString("\n").trim()
                        callback(answer.takeIf(String::isNotBlank), if (answer.isBlank()) "O assistente não retornou uma resposta." else null)
                    } catch (error: Exception) { callback(null, "Resposta do Gemini inválida.") }
                }
            }
        })
    }

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}