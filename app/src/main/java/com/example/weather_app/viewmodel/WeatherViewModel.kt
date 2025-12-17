package com.example.weather_app.viewmodel

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weather_app.data.DailyForecast
import com.example.weather_app.data.HourlyForecast
import com.example.weather_app.data.Temperature
import com.example.weather_app.data.WeatherInfo
import com.example.weather_app.data.toCelsius
import com.example.weather_app.data.toDayString
import com.example.weather_app.data.toFahrenheit
import com.example.weather_app.data.toHourString
import com.example.weather_app.network.WeatherApiService
import com.example.weather_app.network.WindyRequest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.util.Calendar
import java.util.Locale

enum class TempUnit { C, F }

sealed interface WeatherUiState {
    object Empty : WeatherUiState
    object Loading : WeatherUiState
    data class Success(val weatherInfo: WeatherInfo, val tempUnit: TempUnit) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Empty)
    val weatherState = _weatherState.asStateFlow()

    private val apiService: WeatherApiService by lazy {
        val contentType = "application/json".toMediaType()
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        Retrofit.Builder()
            .baseUrl("https://api.windy.com/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(WeatherApiService::class.java)
    }

    fun fetchWeather(apiKey: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = WeatherUiState.Loading
            try {
                val request = WindyRequest(lat = lat, lon = lon, key = apiKey)
                val response = apiService.getWeather(request)
                val weatherInfo = parseWeatherResponse(response, lat, lon)
                _weatherState.value = WeatherUiState.Success(weatherInfo, TempUnit.C)
            } catch (e: Exception) {
                val errorMessage = if (e is HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    "HTTP ${e.code()}: ${e.message()} - $errorBody"
                } else {
                    e.message ?: "An unknown error occurred"
                }
                _weatherState.value = WeatherUiState.Error(errorMessage)
            }
        }
    }

    fun toggleTempUnit() {
        _weatherState.update {
            if (it is WeatherUiState.Success) {
                it.copy(tempUnit = if (it.tempUnit == TempUnit.C) TempUnit.F else TempUnit.C)
            } else {
                it
            }
        }
    }

    private fun parseWeatherResponse(response: JsonObject, lat: Double, lon: Double): WeatherInfo {
        val geocoder = Geocoder(getApplication(), Locale.getDefault())
        val locationName = try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.locality ?: "Lat: $lat, Lon: $lon"
        } catch (e: IOException) {
            "Lat: $lat, Lon: $lon"
        }

        val timestamps = (response["ts"] as JsonArray).map { it.jsonPrimitive.content.toLong() }
        val tempsK = (response["temp-surface"] as JsonArray).map { it.jsonPrimitive.double }
        val humidity = (response["rh-surface"] as JsonArray).map { it.jsonPrimitive.double }
        val windU = (response["wind_u-surface"] as JsonArray).map { it.jsonPrimitive.double }
        val windV = (response["wind_v-surface"] as JsonArray).map { it.jsonPrimitive.double }
        val ptype = (response["ptype-surface"] as JsonArray).map { it.jsonPrimitive.double.toInt() }
        val lclouds = (response["lclouds-surface"] as JsonArray).map { it.jsonPrimitive.double }
        val mclouds = (response["mclouds-surface"] as JsonArray).map { it.jsonPrimitive.double }
        val hclouds = (response["hclouds-surface"] as JsonArray).map { it.jsonPrimitive.double }

        val currentWindSpeed = kotlin.math.sqrt(windU.first() * windU.first() + windV.first() * windV.first())

        val hourlyForecasts = timestamps.zip(tempsK).zip(humidity).zip(ptype).zip(lclouds).zip(mclouds).zip(hclouds).map { (all, h) ->
            val (all2, m) = all
            val (all3, l) = all2
            val (all4, p) = all3
            val (tsAndTemp, hum) = all4
            val (ts, tempK) = tsAndTemp
            HourlyForecast(
                time = ts.toHourString(),
                temp = Temperature(tempK.toCelsius(), tempK.toFahrenheit()),
                humidity = "${hum.toInt()}%",
                condition = getWeatherCondition(p, l, m, h)
            )
        }

        val dailyForecasts = groupHourlyForecastsByDay(hourlyForecasts, timestamps).take(6)

        val currentTempK = tempsK.first()
        return WeatherInfo(
            location = locationName,
            currentTemp = Temperature(currentTempK.toCelsius(), currentTempK.toFahrenheit()),
            currentHumidity = "${humidity.first().toInt()}%",
            currentWindSpeed = "${currentWindSpeed.toInt()} m/s",
            hourlyForecast = hourlyForecasts.take(9), // First 8 hours plus current
            dailyForecast = dailyForecasts
        )
    }

    private fun groupHourlyForecastsByDay(
        hourly: List<HourlyForecast>,
        timestamps: List<Long>
    ): List<DailyForecast> {
        val cal = Calendar.getInstance()
        return timestamps.zip(hourly)
            .groupBy { (ts, _) ->
                cal.timeInMillis = ts
                cal.get(Calendar.DAY_OF_YEAR)
            }
            .map { (_, entries) ->
                val day = entries.first().first.toDayString()
                val highs = entries.map { it.second.temp.celsius }
                val lows = entries.map { it.second.temp.celsius }
                val conditions = entries.map { it.second.condition }
                DailyForecast(
                    day = day,
                    highTemp = Temperature(highs.maxOrNull() ?: 0.0, (highs.maxOrNull() ?: 0.0).toFahrenheit()),
                    lowTemp = Temperature(lows.minOrNull() ?: 0.0, (lows.minOrNull() ?: 0.0).toFahrenheit()),
                    condition = conditions.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
                )
            }
    }

    private fun getWeatherCondition(ptype: Int, lclouds: Double, mclouds: Double, hclouds: Double): String {
        return when {
            ptype > 0 -> "🌧️"
            lclouds > 50 || mclouds > 50 || hclouds > 50 -> "☁️"
            else -> "☀️"
        }
    }
}