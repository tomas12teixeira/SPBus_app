package com.example.spbus.data

data class BusPosition(
    val id: String,
    val lineCode: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val updatedAt: String
)

data class BusStopRecord(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lines: List<String>
)

data class PassengerFeedback(
    val id: String,
    val lineCode: String,
    val status: String,
    val comment: String,
    val createdAt: String
)

data class ThingSpeakReading(
    val createdAt: String,
    val occupancy: Double?,
    val speedKmh: Double?,
    val temperatureCelsius: Double?
)

data class FleetTelemetry(
    val readings: List<ThingSpeakReading>,
    val channelId: String
)