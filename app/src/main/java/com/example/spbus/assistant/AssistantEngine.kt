package com.example.spbus.assistant

class AssistantEngine(private val intentDetector: IntentDetector = IntentDetector()) {
    fun respond(message: String, context: AssistantContext = AssistantContext()): AssistantResponse {
        val intent = intentDetector.detect(message)
        val response = when (intent) {
            AssistantIntent.HELP -> "Informe origem e destino na tela Mapa para buscar linhas diretas. Selecione uma opção para consultar a previsão disponível na SPTrans."
            AssistantIntent.NEARBY_BUSES -> context.nearbyLines.takeIf(List<String>::isNotEmpty)
                ?.joinToString(prefix = "Linhas disponíveis nos dados consultados: ")
                ?: "Não tenho linhas próximas disponíveis. Permita a localização e tente atualizar os dados da SPTrans."
            AssistantIntent.ROUTE -> when {
                context.origin.isNullOrBlank() -> "Qual é o ponto de partida?"
                context.destination.isNullOrBlank() -> "Qual é o destino?"
                context.selectedLine.isNullOrBlank() -> "Pesquise a rota na tela Mapa; ainda não tenho uma linha selecionada para este trajeto."
                else -> "A linha ${context.selectedLine} está selecionada. Consulte os pontos e a previsão disponível no mapa."
            }
            AssistantIntent.ARRIVAL -> context.arrivalMinutes?.let {
                "A previsão consultada na SPTrans é de aproximadamente $it min."
            } ?: "Não tenho uma previsão confiável da SPTrans para este ponto agora."
            AssistantIntent.CROWDING -> context.recentFeedbackSummary
                ?: "Não há relatos recentes disponíveis para confirmar a lotação deste ônibus."
            AssistantIntent.LINE_SEARCH -> context.selectedLine?.let { "Linha selecionada: $it. Consulte os pontos e veículos disponíveis no mapa." }
                ?: "Informe o número da linha na busca do mapa para consultar os dados disponíveis na SPTrans."
            AssistantIntent.STOP_SEARCH -> "Digite o nome do ponto ou endereço na busca. As sugestões de endereço e paradas aparecem conforme os serviços respondem."
            AssistantIntent.ALTERNATIVES -> "Informe origem e destino para consultar as alternativas diretas encontradas nas paradas da SPTrans."
            AssistantIntent.UNKNOWN -> "Não entendi a solicitação. Posso ajudar com linhas, pontos, rotas e previsões disponíveis."
        }
        val clarification = intent == AssistantIntent.ROUTE &&
            (context.origin.isNullOrBlank() || context.destination.isNullOrBlank())
        return AssistantResponse(intent, response, clarification)
    }
}
