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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MapActivity : AppCompatActivity() {

    private var routePoints: List<LatLng> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // a) Preberemo tri glavne točke iz Intenta
        routePoints = intent.getParcelableArrayListExtra("ROUTE_POINTS") ?: emptyList()

        // b) Znotraj coroutine, da ne blokiramo glavne niti
        lifecycleScope.launch {
            // Če ni dovolj točk, le prikažemo sporočilo
            if (routePoints.size < 2) {
                setContent {
                    MyApplicationTheme {
                        Text("Not enough points to draw routes.")
                    }
                }
                return@launch
            }

            // c) Sestavimo seznam polilinij (vsaka polilinija = mini pot med dvema točkama)
            val allSegmentPolylines = mutableListOf<List<LatLng>>()

            withContext(Dispatchers.IO) {
                // Primer: (Ljubljana->Maribor), (Maribor->Zagreb)
                // Če imate več točk, se ustrezno ustvari več segmentov
                for (i in 0 until routePoints.size - 1) {
                    val segmentStart = routePoints[i]
                    val segmentEnd = routePoints[i + 1]

                    // Pokličemo Routes API za to dvojico
                    val polylinedSegment = getRoutesApiRoute(
                        listOf(segmentStart, segmentEnd),
                        apiKey = ""
                    )
                    allSegmentPolylines.add(polylinedSegment)
                }
            }

            // d) Ko imamo vse polilinije, jih narišemo v MapScreen
            setContent {
                MyApplicationTheme {
                    // V MapScreen pošljemo:
                    // - routePoints: 3 glavne lokacije za markerje
                    // - allSegmentPolylines: seznam “mini” polilinij
                    MapScreen(
                        mainPoints = routePoints,
                        polylinesList = allSegmentPolylines
                    )
                }
            }
        }
    }
}

@Composable
fun MapScreen(
    mainPoints: List<LatLng>,
    polylinesList: List<List<LatLng>>
) {
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

                    // (A) Narišemo vsako polilinijo
                    polylinesList.forEach { segmentPoints ->
                        if (segmentPoints.isNotEmpty()) {
                            val polylineOptions = PolylineOptions()
                                .addAll(segmentPoints)
                                .color(android.graphics.Color.BLUE)
                                .width(8f)
                            googleMap.addPolyline(polylineOptions)
                        }
                    }

                    // (B) Dodamo markerje za glavne točke
                    mainPoints.forEach { point ->
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(point)
                                .icon(
                                    BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_RED
                                    )
                                )
                        )
                    }

                    // (C) Premaknemo kamero recimo na prvo točko
                    if (mainPoints.isNotEmpty()) {
                        googleMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(mainPoints.first(), 7f)
                        )
                    }
                }
            }
        }
    }
}

fun getRoutesApiRoute(
    allPoints: List<LatLng>,
    apiKey: String
): List<LatLng> {
    if (allPoints.size < 2) return emptyList()
    val origin = allPoints.first()
    val destination = allPoints.last()
    val intermediatePoints = allPoints.drop(1).dropLast(1)

    // Sestavimo JSON
    val intermediatesJson = intermediatePoints.joinToString(separator = ",") { point ->
        """
        {
          "location": {
            "latLng": {
              "latitude": ${point.latitude},
              "longitude": ${point.longitude}
            }
          }
        }
        """.trimIndent()
    }

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
      ${if (intermediatesJson.isNotEmpty()) """"intermediates": [ $intermediatesJson ],""" else ""}
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

    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = requestBodyJson.toRequestBody(mediaType)

    val client = OkHttpClient()
    // Dodamo key v URL namesto v header
    val request = Request.Builder()
        .url("https://routes.googleapis.com/directions/v2:computeRoutes?key=$apiKey")
        .post(body)
        // .addHeader("X-Goog-Api-Key", apiKey) // to po potrebi zakomentiramo
        .addHeader("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline")
        .addHeader("Content-Type", "application/json")
        .build()

    val response = client.newCall(request).execute()
    val responseBody = response.body?.string() ?: return emptyList()

    Log.d(TAG, "HTTP status: ${response.code}")
    Log.d(TAG, "Response body: $responseBody")

    if (!response.isSuccessful) {
        Log.e(TAG, "Request not successful!")
        return emptyList()
    }

    val jsonObj = com.google.gson.JsonParser.parseString(responseBody).asJsonObject
    val routesArray = jsonObj.getAsJsonArray("routes") ?: return emptyList()
    if (routesArray.size() == 0) return emptyList()

    val firstRoute = routesArray[0].asJsonObject
    val encodedPolyline = firstRoute
        .getAsJsonObject("polyline")
        .get("encodedPolyline")
        .asString

    // Če želite, lahko namesto .toInt() uporabite .code
    return decodePoly(encodedPolyline)
}

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
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
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



