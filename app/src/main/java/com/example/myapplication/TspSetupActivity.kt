package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.tsp.GA
import com.example.myapplication.tsp.TSP
import com.example.myapplication.tsp.Tour
import com.google.android.gms.maps.model.LatLng
import com.example.myapplication.utils.TSPUtils
import java.io.File

/**
 * Prikaz seznama mest, izbiranje parametrov GA, zagon TSP in prehod na MapActivity.
 */
class TspSetupActivity : AppCompatActivity() {

    private lateinit var allCities: List<CitySelectionItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val tspInputStream = assets.open("direct4me_locations_distance.tsp")
            allCities = TSPUtils.parseCitiesFromTspFile(tspInputStream)
            Log.d("neke", "Successfully loaded ${allCities.size} cities.")
        } catch (e: Exception) {
            Log.e("neke", "Error loading cities: ${e.message}")
            Toast.makeText(this, "Failed to load cities.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                Surface {
                    TspSetupScreen(
                        initialCities = allCities,
                        onRunTsp = { selectedCities, gaParams, optimizeBy ->
                            try{
                                // Ko uporabnik klikne "Zaženi TSP"

                                // 1) Zgradimo TSP primer (če imate branje iz datoteke, storite tam).
                                //    Tu za primer kar ročno kreiramo TSP z bazo "bays29".
                                //    Uporabite svojo pot ali branje distance matrike.
                                if(selectedCities.size < 2) {
                                    Toast.makeText(this, "Please select at least two cities.", Toast.LENGTH_SHORT).show()
                                    return@TspSetupScreen
                                }

                                val selectedIndices = selectedCities.map { it.index }

                                val tspInputStream = assets.open("direct4me_locations_distance.tsp")
                                val outFile = File(filesDir, "direct4me_locations_distance.tsp")

                                tspInputStream.use { input ->
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                val tsp = TSP(outFile.absolutePath, maxFe = 200000, selectedIndices) // poljubno

                                // 2) Prilagodimo TSP, da upošteva le izbrane cities (če želite).
                                //    Morda pa imate v TSP logiki že vgrajeno "vse mest" in
                                //    ročno izločite nepotrebne. To je povsem odvisno od vaše implementacije.
                                //    Za demonstracijo se pretvarjamo, da so "selectedCities" vsi, ki jih obdržimo.

                                // 3) Ustvarimo GA in zaženemo.
                                val ga = GA(
                                    populationSize = gaParams.populationSize,
                                    crossoverChance = gaParams.crossoverChance,
                                    mutationChance = gaParams.mutationChance
                                )

                                val bestTour: Tour = ga.run(tsp)
                                Log.d("neke", "Best tour distance: ${bestTour.distance}")

                                // 4) Iz bestTour dobimo zaporedje indeksov.
                                val routeIndexes = bestTour.path.map { it.index }

                                // 5) Skupna "razdalja" je bestTour.distance,
                                //    za "čas" bi morali imeti ustrezno matriko ali preračun v TSP.

                                val totalDistance = bestTour.distance

                                //val totalTime = estimateTime(bestTour.distance)
                                // Ta metoda je izmišljena. Če imate matrične podatke,
                                // lahko shranite tako "distance" kot "čas" v TSP evaluate().

                                // 6) Prehod na MapActivity:
                                //    -> mu pošljemo routeIndexes (zaporedje)
                                //    -> in informacijo, ali naj prikaže razdaljo ali čas
                                val coordinateList: List<LatLng> = createLatLngList(bestTour.path)
                                Log.d("neke", "Coordinate list size: ${coordinateList.size}")

                                val latLngArrayList: ArrayList<LatLng> = ArrayList(coordinateList)

                                val intent = Intent(this, MapActivity::class.java).apply {
                                    putExtra("latLngList", latLngArrayList)
                                    //putExtra("TOTAL_TIME", totalTime)
                                }
                                Log.d("neke", "Starting MapActivity with coordinates: $coordinateList")
                                Log.d("neke", "onCreate: ${coordinateList}")

                                startActivity(intent)
                            }
                            catch (e: Exception) {
                                Log.e("neke", "Error running TSP: ${e.message}", e)
                                Toast.makeText(this, "An error occurred while running TSP.", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Primer navidezne metode za preračun časa na podlagi razdalje.
     * V praksi bi bil to lahko kak "speed factor".
     */
    private fun estimateTime(distance: Double): Double {
        // Denimo: 1 enota distance ~ 1.2 minute
        return distance * 1.2
    }
}

/*fun createLatLngList(routeIndexes: List<Int>, tsp: TSP): List<LatLng> {
    val latLngList = mutableListOf<LatLng>()
    for (cityIndex in routeIndexes) { // Adjust based on indexing
        if (cityIndex - 1 in tsp.cities.indices) {
            val city = tsp.cities[cityIndex - 1]
            latLngList.add(LatLng(city.y, city.x))
        } else {
            Log.e("createLatLngList", "Invalid city index: $cityIndex")
        }
    }
    return latLngList
}*/

fun createLatLngList(routePath: List<TSP.City>): List<LatLng> {
    return routePath.map { city ->
        LatLng(city.y, city.x)
    }
}


/**
 * Podatkovni razred za checkbox v LazyColumn.
 */
data class CitySelectionItem(
    val index: Int,
    val lat: Double,
    val lng: Double,
    var isSelected: Boolean
)

/**
 * Parametri GA, ki jih nastavimo z vnosnimi polji.
 */
data class GaParams(
    val populationSize: Int,
    val crossoverChance: Double,
    val mutationChance: Double
)

/**
 * UI, kjer prikažemo:
 *  - Seznam mest z checkboxi,
 *  - polja za GA parametre,
 *  - radio button za optimizacijo (čas / razdalja),
 *  - gumb za "Zaženi TSP".
 */
@Composable
fun TspSetupScreen(
    initialCities: List<CitySelectionItem>,
    onRunTsp: (
        selectedCities: List<CitySelectionItem>,
        gaParams: GaParams,
        optimizeBy: String
    ) -> Unit
) {
    val context = LocalContext.current

    // Kopija seznama mest v Compose stanju
    var cities by remember { mutableStateOf(initialCities) }

    // GA parametri v stanju
    var populationSize by remember { mutableStateOf("100") }
    var crossoverChance by remember { mutableStateOf("0.8") }
    var mutationChance by remember { mutableStateOf("0.1") }

    // Radio button za optimizacijo
    var optimizeBy by remember { mutableStateOf("distance") } // ali "time"

    Column(modifier = Modifier.padding(16.dp)) {

        Text(text = "Izberite mesta:", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // da zapolni prostor
        ) {
            itemsIndexed(cities) { index, city ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = city.isSelected,
                        onCheckedChange = { isChecked ->
                            cities = cities.mapIndexed { idx, oldCity ->
                                if (idx == index) oldCity.copy(isSelected = isChecked)
                                else oldCity
                            }
                        }
                    )
                    Text(text = "Mesto #${city.index} (lat=${city.lat}, lng=${city.lng})")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(text = "Nastavitve GA:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = populationSize,
            onValueChange = { populationSize = it },
            label = { Text("Velikost populacije") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = crossoverChance,
            onValueChange = { crossoverChance = it },
            label = { Text("Verjetnost križanja") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = mutationChance,
            onValueChange = { mutationChance = it },
            label = { Text("Verjetnost mutacije") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Text(text = "Optimiziraj:", style = MaterialTheme.typography.titleMedium)
        Row {
            RadioButton(
                selected = optimizeBy == "distance",
                onClick = { optimizeBy = "distance" }
            )
            Text("Razdalja")

            Spacer(modifier = Modifier.width(16.dp))

            RadioButton(
                selected = optimizeBy == "time",
                onClick = { optimizeBy = "time" }
            )
            Text("Čas")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                // Ob kliku gumb "Zaženi TSP"
                val selectedCities = cities.filter { it.isSelected }
                val gaParams = GaParams(
                    populationSize = populationSize.toIntOrNull() ?: 100,
                    crossoverChance = crossoverChance.toDoubleOrNull() ?: 0.8,
                    mutationChance = mutationChance.toDoubleOrNull() ?: 0.1
                )
                onRunTsp(selectedCities, gaParams, optimizeBy)
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Zaženi TSP")
        }
    }
}


