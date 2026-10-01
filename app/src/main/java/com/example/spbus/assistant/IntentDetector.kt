package com.example.spbus.assistant

import java.text.Normalizer
import java.util.Locale

class IntentDetector {
    fun detect(message: String): AssistantIntent {
        val text = normalize(message)
        return when {
            hasAny(text, "ajuda", "como usar", "como funciona") -> AssistantIntent.HELP
            hasAny(text, "quanto falta", "que horas chega", "previsao", "demora para chegar", "chegada") -> AssistantIntent.ARRIVAL
            hasAny(text, "lotado", "lotacao", "vazio", "cheio", "lotacao") -> AssistantIntent.CROWDING
            hasAny(text, "alternativa", "outra opcao", "outras opcoes") -> AssistantIntent.ALTERNATIVES
            hasAny(text, "perto", "proximo", "aqui", "passa aqui") -> AssistantIntent.NEARBY_BUSES
            hasAny(text, "ponto", "parada", "paradas") -> AssistantIntent.STOP_SEARCH
            hasAny(text, "linha", "onibus") && !hasAny(text, "rota", "chego", "destino") -> AssistantIntent.LINE_SEARCH
            hasAny(text, "rota", "chego", "destino", "como vou") -> AssistantIntent.ROUTE
            else -> AssistantIntent.UNKNOWN
        }
    }

    private fun hasAny(text: String, vararg phrases: String): Boolean = phrases.any(text::contains)

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
