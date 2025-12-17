package com.example.weather_app.viewmodel

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weather_app.data.HourlyForecast
import com.example.weather_app.data.Temperature
import com.example.weather_app.data.WeatherInfo
import com.example.weather_app.data.toCelsius
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

        val currentWindSpeed = kotlin.math.sqrt(windU.first() * windU.first() + windV.first() * windV.first())

        val forecast = timestamps.zip(tempsK).mapIndexed { index, (ts, tempK) ->
            if (index > 0) { // Skip the current hour
                HourlyForecast(
                    time = ts.toHourString(),
                    temp = Temperature(tempK.toCelsius(), tempK.toFahrenheit())
                )
            } else {
                null
            }
        }.filterNotNull().take(8) // Take the next 8 hours

        val currentTempK = tempsK.first()
        return WeatherInfo(
            location = locationName,
            currentTemp = Temperature(currentTempK.toCelsius(), currentTempK.toFahrenheit()),
            currentHumidity = "${humidity.first().toInt()}%",
            currentWindSpeed = "${currentWindSpeed.toInt()} m/s",
            forecast = forecast
        )
    }
}