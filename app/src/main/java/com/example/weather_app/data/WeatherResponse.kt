package com.example.weather_app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    @SerialName("name") val name: String,
    @SerialName("main") val main: Main,
    @SerialName("weather") val weather: List<Weather>,
    @SerialName("wind") val wind: Wind
)

@Serializable
data class Main(
    @SerialName("temp") val temp: Double,
    @SerialName("humidity") val humidity: Int
)

@Serializable
data class Weather(
    @SerialName("main") val main: String,
    @SerialName("description") val description: String
)

@Serializable
data class Wind(
    @SerialName("speed") val speed: Double
)
