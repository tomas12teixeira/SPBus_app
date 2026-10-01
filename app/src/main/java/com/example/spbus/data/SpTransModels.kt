package com.example.spbus.data

data class SpTransLine(
    val code: Int,
    val number: String,
    val sense: Int,
    val circular: Boolean,
    val origin: String,
    val destination: String
)

data class SpTransStop(
    val code: Int,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class SpTransVehicle(
    val prefix: String,
    val accessible: Boolean,
    val updatedAt: String,
    val latitude: Double,
    val longitude: Double
)

data class SpTransArrival(
    val line: SpTransLine,
    val stop: SpTransStop,
    val vehicleCount: Int,
    val nextArrivals: List<String>
)