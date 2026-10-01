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

data class ThingSpeakDistanceReading(
    val createdAt: String,
    val distanceMeters: Int
)

data class ThingSpeakDistanceFeed(
    val readings: List<ThingSpeakDistanceReading>,
    val channelId: String
)