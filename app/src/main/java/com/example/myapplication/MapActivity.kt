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
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MapActivity : AppCompatActivity() {

    // Your locations read from Intent
    private var routePoints: List<LatLng> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Read your locations from Intent
        routePoints = intent.getParcelableArrayListExtra("ROUTE_POINTS") ?: emptyList()
        Log.d("MapActivity", "Vsebina routePoints: $routePoints")

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

            val apiKey = "AIzaSyA5aFPU0b4GbgwwnfmQOtG0eZkmYJsG_XM"

            try {
                val routePointsRoad = withContext(Dispatchers.IO) {
                    getRoutesApiRoute(origin, destination, apiKey)
                }

                if (routePointsRoad.isEmpty()) {
                    Log.e(TAG, "No route points returned from Routes API.")
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

// Function to get directions route from Routes API
fun getRoutesApiRoute(
    origin: LatLng,
    destination: LatLng,
    apiKey: String
): List<LatLng> {
    val requestBodyJson = """
    {
      "origin": {
        "location": {
          "latLng": {
            "latitude": ${origin.latitude},
            "longitude": ${origin.longitude}
          }
        }
      },
      "destination": {
        "location": {
          "latLng": {
            "latitude": ${destination.latitude},
            "longitude": ${destination.longitude}
          }
        }
      },
      "travelMode": "DRIVE"
    }
    """.trimIndent()

    // -- 2) Sestavimo POST zahtevo
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = requestBodyJson.toRequestBody(mediaType)

    val client = OkHttpClient()

    val request = Request.Builder()
        .url("https://routes.googleapis.com/directions/v2:computeRoutes")
        .post(body)
        // Dodamo API KEY v header (X-Goog-Api-Key) in poljubni field mask
        .addHeader("X-Goog-Api-Key", apiKey)
        .addHeader("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline")
        .addHeader("Content-Type", "application/json")
        .build()

    // -- 3) Izvedemo sinhroni klic (pozor, v praksi raje v Dispatchers.IO/coroutines)
    val response = client.newCall(request).execute()
    val responseBody = response.body?.string() ?: return emptyList()
    Log.d(TAG, "getRoutesApiRoute: ")

    val jsonObj = JsonParser.parseString(responseBody).asJsonObject
    val routesArray = jsonObj.getAsJsonArray("routes") ?: return emptyList()
    if (routesArray.size() == 0) return emptyList()

    val firstRoute = routesArray[0].asJsonObject

    // Preberemo polje "polyline" -> "encodedPolyline"
    val encodedPolyline = firstRoute
        .getAsJsonObject("polyline")
        .get("encodedPolyline")
        .asString

    // -- 5) Dekodiramo polilinijo (encoded string -> List<LatLng>)
    return decodePoly(encodedPolyline)
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


