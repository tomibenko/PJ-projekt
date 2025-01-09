package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : AppCompatActivity() {

    // Tu so vaše lokacije, prebrane iz Intenta
    private var routePoints: List<LatLng> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Preberemo vaše lokacije iz Intenta
        routePoints = intent.getParcelableArrayListExtra("ROUTE_POINTS") ?: emptyList()

        // 2) Uporabimo coroutine, da ne blokiramo UI niti s sinhronim HTTP klicem
        lifecycleScope.launch {
            // Preverimo, da imamo vsaj dve točki za origin in destination
            if (routePoints.size < 2) {
                // Ni dovolj točk - lahko prikažete opozorilo ali fallback
                setContent {
                    MyApplicationTheme {
                        Text("Manjka dovolj točk za Directions API.")
                    }
                }
                return@launch
            }

            // Vzamemo prvo in zadnjo točko kot origin in destination
            val origin = routePoints.first()
            val destination = routePoints.last()

            // Vaš API ključ (naj bo omejen in shranjen varno)
            val apiKey = "VAŠ_DIRECTIONS_API_KEY"

            // 3) Klic getDirectionsRoute(...) v ozadju
            val routePointsCesta = withContext(Dispatchers.IO) {
                getDirectionsRoute(origin, destination, apiKey)
            }

            // 4) Ko dobimo točke, prikažemo MapScreen z “cestno” polilinijo
            setContent {
                MyApplicationTheme {
                    MapScreen(routePointsCesta)
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
                        // Narišemo Polyline z dekodirano potjo po cestah
                        val polylineOptions = PolylineOptions()
                            .addAll(routePoints)
                            .color(android.graphics.Color.BLUE) // ali poljubna barva
                            .width(8f)

                        googleMap.addPolyline(polylineOptions)

                        // Start/End markerji
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

                        // Premik kamere na prvo točko
                        googleMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(routePoints.first(), 7f)
                        )
                    }
                }
            }
        }
    }
}

// -- 1) Napišemo funkcijo za sestavo URL-ja in klic Directions API
fun getDirectionsRoute(
    origin: LatLng,
    destination: LatLng,
    apiKey: String
): List<LatLng> {
    val client = okhttp3.OkHttpClient()

    // Zgradimo URL s parametri
    // Primer: https://maps.googleapis.com/maps/api/directions/json?origin=46.0569,14.5058&destination=45.8150,15.9819&key=VAŠ_API_KEY
    val urlBuilder = StringBuilder("https://maps.googleapis.com/maps/api/directions/json?")
    urlBuilder.append("origin=${origin.latitude},${origin.longitude}")
    urlBuilder.append("&destination=${destination.latitude},${destination.longitude}")
    urlBuilder.append("&key=$apiKey")

    val request = okhttp3.Request.Builder()
        .url(urlBuilder.toString())
        .build()

    // Izvedemo sinhron klic (v praksi raje uporabite coroutine/async)
    val response = client.newCall(request).execute()
    val body = response.body?.string() ?: return emptyList()

    // -- 2) Razčlenimo JSON odgovor (Gson, manualno, ali kakšna druga knjižnica)
    //     Poiščemo polje "overview_polyline" -> "points"
    val jsonObj = com.google.gson.JsonParser.parseString(body).asJsonObject
    val routesArray = jsonObj.getAsJsonArray("routes")
    if (routesArray.size() == 0) return emptyList()

    // Vzamemo prvo ruto
    val routeObj = routesArray[0].asJsonObject
    val overviewPolyline = routeObj
        .getAsJsonObject("overview_polyline")
        .get("points")
        .asString

    // -- 3) Dekodiramo encoded polyline v seznam LatLng
    return decodePoly(overviewPolyline)
}

// -- Pomozna funkcija za dekodiranje polilinije (encoded string -> list LatLng)
fun decodePoly(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].toInt() - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].toInt() - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lng += dlng

        val finalLat = lat / 1E5
        val finalLng = lng / 1E5
        poly.add(LatLng(finalLat, finalLng))
    }
    return poly
}



