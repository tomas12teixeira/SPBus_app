package com.example.spbus

import com.example.spbus.assistant.AssistantEngine
import com.example.spbus.assistant.AssistantIntent
import com.example.spbus.assistant.IntentDetector
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ExampleUnitTest {
    @Test
    fun detectsPortugueseRouteRequest() {
        assertEquals(AssistantIntent.ROUTE, IntentDetector().detect("Como chego à Avenida Paulista?"))
    }

    @Test
    fun asksForMissingRouteContext() {
        val response = AssistantEngine().respond("Como chego ao destino?")
        assertEquals(AssistantIntent.ROUTE, response.intent)
        assertTrue(response.needsClarification)
    }

    @Test
    fun doesNotInventArrivalPrediction() {
        val response = AssistantEngine().respond("Quanto falta para chegar?")
        assertEquals(AssistantIntent.ARRIVAL, response.intent)
        assertTrue(response.message.contains("Não tenho uma previsão confiável"))
    }

    @Test
    fun knownRouteContextNeedsNoClarification() {
        val response = AssistantEngine().respond(
            "Como chego?",
            com.example.spbus.assistant.AssistantContext(origin = "Sé", destination = "Paulista")
        )
        assertFalse(response.needsClarification)
    }
}