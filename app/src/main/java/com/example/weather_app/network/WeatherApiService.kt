package com.example.weather_app.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

interface WeatherApiService {
    @POST("api/point-forecast/v2")
    suspend fun getWeather(@Body body: WindyRequest): JsonObject
}

@Serializable
data class WindyRequest(
    val lat: Double,
    val lon: Double,
    val model: String = "gfs",
    val parameters: List<String> = listOf("temp", "rh", "wind", "ptype", "lclouds", "mclouds", "hclouds"),
    val levels: List<String> = listOf("surface"),
    val key: String
)
