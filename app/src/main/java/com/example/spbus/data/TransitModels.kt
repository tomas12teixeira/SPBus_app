package com.example.spbus.data

data class FleetTelemetry(
    val routeCode: Int,
    val routeNumber: String,
    val vehiclePrefix: String,
    val speedKmh: Int,
    val occupancy: String,
    val status: String,
    val timestamp: String
)

data class ThingSpeakReading(
    val createdAt: String,
    val fields: Map<Int, Double>
)

data class ThingSpeakField(
    val number: Int,
    val label: String
)

data class ThingSpeakFeed(
    val readings: List<ThingSpeakReading>,
    val channelId: String,
    val channelName: String,
    val fields: List<ThingSpeakField>
)