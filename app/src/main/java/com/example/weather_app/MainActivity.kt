package com.example.weather_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.weather_app.data.HourlyForecast
import com.example.weather_app.ui.theme.Weather_AppTheme
import com.example.weather_app.viewmodel.TempUnit
import com.example.weather_app.viewmodel.WeatherUiState
import com.example.weather_app.viewmodel.WeatherViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : ComponentActivity() {

    private val weatherViewModel: WeatherViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fetchLocationAndWeather()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            Weather_AppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "weather") {
                    composable("weather") {
                        WeatherScreen(
                            weatherViewModel = weatherViewModel,
                            onFetchWeatherClick = { this@MainActivity.requestLocationAndWeather() },
                            onGoToMapClick = { navController.navigate("map") }
                        )
                    }
                    composable("map") {
                        MapScreen(navController = navController)
                    }
                }
            }
        }
    }

    private fun requestLocationAndWeather() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndWeather()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndWeather() {
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            location?.let {
                weatherViewModel.fetchWeather(
                    "ZbrU8HlwEk98pfIg4szMl3FQsfkwCkVs",
                    it.latitude,
                    it.longitude
                )
            }
        }
    }
}

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
    weatherViewModel: WeatherViewModel,
    onFetchWeatherClick: () -> Unit,
    onGoToMapClick: () -> Unit
) {
    val weatherState by weatherViewModel.weatherState.collectAsState()

    Column(modifier = modifier.padding(16.dp)) {
        Row {
            Button(onClick = onFetchWeatherClick) {
                Text("Fetch Weather")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onGoToMapClick) {
                Text("Map")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        when (val state = weatherState) {
            is WeatherUiState.Empty -> {
                Text("Press the button to fetch the weather for your current location.")
            }
            is WeatherUiState.Success -> {
                val weatherInfo = state.weatherInfo
                val unit = state.tempUnit
                Column {
                    Text("Location: ${weatherInfo.location}")
                    Text(
                        "Temperature: ${if (unit == TempUnit.C) weatherInfo.currentTemp.celsius.toInt() else weatherInfo.currentTemp.fahrenheit.toInt()}°${unit.name}"
                    )
                    Text("Humidity: ${weatherInfo.currentHumidity}")
                    Text("Wind Speed: ${weatherInfo.currentWindSpeed}")
                    Button(onClick = { weatherViewModel.toggleTempUnit() }) {
                        Text("Switch to °${if (unit == TempUnit.C) "F" else "C"}")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Hourly Forecast:")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(weatherInfo.forecast) { forecast ->
                            ForecastItem(forecast, unit)
                        }
                    }
                }
            }
            is WeatherUiState.Error -> {
                Text(state.message)
            }
            is WeatherUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun ForecastItem(forecast: HourlyForecast, unit: TempUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(forecast.time)
        Text("${if (unit == TempUnit.C) forecast.temp.celsius.toInt() else forecast.temp.fahrenheit.toInt()}°${unit.name}")
    }
}

@Composable
fun MapScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { navController.popBackStack() }) {
            Text("Back to Weather")
        }
        AndroidView(factory = {
            WebView(it).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                loadUrl("file:///android_asset/windy_map.html")
            }
        })
    }
}
