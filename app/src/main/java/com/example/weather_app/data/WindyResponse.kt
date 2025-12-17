package com.example.weather_app.data

import kotlinx.serialization.Serializable

@Serializable
data class WindyResponse(
    val lat: Double,
    val lon: Double,
    val model: String,
    val parameters: List<Parameter>,
    val data: Map<String, List<Double?>>
)

@Serializable
data class Parameter(
    val name: String,
    val level: String,
    val units: String
)
