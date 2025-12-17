package com.example.weather_app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeatherInfo(
    val location: String,
    val currentTemp: Temperature,
    val currentHumidity: String,
    val currentWindSpeed: String,
    val forecast: List<HourlyForecast>
)

data class HourlyForecast(
    val time: String,
    val temp: Temperature
)

data class Temperature(val celsius: Double, val fahrenheit: Double)

fun Double.toCelsius(): Double = this - 273.15

fun Double.toFahrenheit(): Double = this * 9 / 5 - 459.67

fun Long.toHourString(): String {
    val date = Date(this)
    val format = SimpleDateFormat("ha", Locale.getDefault())
    return format.format(date)
}
