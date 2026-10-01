package com.example.spbus.assistant

enum class AssistantIntent {
    HELP,
    NEARBY_BUSES,
    ROUTE,
    ARRIVAL,
    CROWDING,
    LINE_SEARCH,
    STOP_SEARCH,
    ALTERNATIVES,
    UNKNOWN
}

data class AssistantContext(
    val origin: String? = null,
    val destination: String? = null,
    val selectedLine: String? = null,
    val arrivalMinutes: Int? = null,
    val recentFeedbackSummary: String? = null,
    val nearbyLines: List<String> = emptyList()
)

data class AssistantResponse(
    val intent: AssistantIntent,
    val message: String,
    val needsClarification: Boolean = false
)
