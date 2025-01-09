package com.example.myapplication

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request

class MapActivity : AppCompatActivity() {

    // Your locations read from Intent
    private var routePoints: List<LatLng> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Read your locations from Intent
        routePoints = intent.getParcelableArrayListExtra("ROUTE_POINTS") ?: emptyList()

        // 2) Use coroutine to avoid blocking UI with synchronous HTTP calls
        lifecycleScope.launch {
            if (routePoints.size < 2) {
                // Not enough points
                setContent {
                    MyApplicationTheme {
                        Text("Not enough points for Directions API.")
                    }
                }
                Log.e(TAG, "Not enough points provided to fetch directions.")
                return@launch
            }

            val origin = routePoints.first()
            val destination = routePoints.last()

            val apiKey = "AIzaSyBGqGnv3SNV4B5fhttwvbI2-vI4spDv_6c"

            try {
                val routePointsRoad = withContext(Dispatchers.IO) {
                    getDirectionsRoute(origin, destination, apiKey)
                }

                if (routePointsRoad.isEmpty()) {
                    Log.e(TAG, "No route points returned from Directions API.")
                } else {
                    Log.d(TAG, "Route points received: ${routePointsRoad.size} points.")
                }

                setContent {
                    MyApplicationTheme {
                        MapScreen(routePointsRoad)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching directions: ${e.message}")
                setContent {
                    MyApplicationTheme {
                        Text("Error fetching directions: ${e.localizedMessage}")
                    }
                }
            }
        }

    }
}

@Composable
fun MapScreen(routePoints: List<LatLng>) {
    val context = LocalContext.current
    val fragmentContainerId = remember { View.generateViewId() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply { id = fragmentContainerId }
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(Unit) {
            val fragmentManager = (context as AppCompatActivity).supportFragmentManager
            if (fragmentManager.findFragmentByTag("mapFragment") == null) {
                val mapFragment = SupportMapFragment.newInstance()
                fragmentManager.beginTransaction()
                    .replace(fragmentContainerId, mapFragment, "mapFragment")
                    .commitNow()

                mapFragment.getMapAsync { googleMap ->
                    googleMap.uiSettings.isZoomControlsEnabled = true

                    if (routePoints.isNotEmpty()) {
                        // Draw Polyline with decoded road route
                        val polylineOptions = PolylineOptions()
                            .addAll(routePoints)
                            .color(android.graphics.Color.BLUE) // or any color
                            .width(8f)

                        googleMap.addPolyline(polylineOptions)

                        // Start/End markers
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(routePoints.first())
                                .title("Start")
                        )
                        if (routePoints.size > 1) {
                            googleMap.addMarker(
                                MarkerOptions()
                                    .position(routePoints.last())
                                    .title("End")
                            )
                        }

                        // Move camera to the first point
                        googleMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(routePoints.first(), 12f)
                        )
                    }
                }
            }
        }
    }
}

// Function to get directions route from Directions API
fun getDirectionsRoute(
    origin: LatLng,
    destination: LatLng,
    apiKey: String
): List<LatLng> {
    val client = OkHttpClient()

    val urlBuilder = StringBuilder("https://maps.googleapis.com/maps/api/directions/json?")
    urlBuilder.append("origin=${origin.latitude},${origin.longitude}")
    urlBuilder.append("&destination=${destination.latitude},${destination.longitude}")
    urlBuilder.append("&key=$apiKey")

    val request = Request.Builder()
        .url(urlBuilder.toString())
        .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
        Log.e(TAG, "Directions API call failed with response code: ${response.code}")
        return emptyList()
    }

    val body = response.body?.string() ?: run {
        Log.e(TAG, "Directions API response body is null.")
        return emptyList()
    }

    val jsonObj = JsonParser.parseString(body).asJsonObject
    val status = jsonObj.get("status")?.asString
    if (status != "OK") {
        Log.e(TAG, "Directions API returned status: $status")
        return emptyList()
    }

    val routesArray = jsonObj.getAsJsonArray("routes")
    if (routesArray.size() == 0) {
        Log.e(TAG, "No routes found in Directions API response.")
        return emptyList()
    }

    val routeObj = routesArray[0].asJsonObject
    val overviewPolyline = routeObj
        .getAsJsonObject("overview_polyline")
        .get("points")
        .asString

    Log.d(TAG, "Encoded polyline: $overviewPolyline")

    return decodePoly(overviewPolyline)
}

// Helper function to decode polyline
fun decodePoly(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    try {
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val finalLat = lat / 1E5
            val finalLng = lng / 1E5
            poly.add(LatLng(finalLat, finalLng))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error decoding polyline: ${e.message}")
    }

    Log.d(TAG, "Decoded polyline has ${poly.size} points.")
    return poly
}


