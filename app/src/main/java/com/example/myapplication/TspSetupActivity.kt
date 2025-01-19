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

class TspSetupActivity : AppCompatActivity() {

    private lateinit var allCities: List<CitySelectionItem>
    private lateinit var outFile: File

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
                            try {
                                if(selectedCities.size < 2) {
                                    Toast.makeText(this, "Please select at least two cities.", Toast.LENGTH_SHORT).show()
                                    return@TspSetupScreen
                                }


                                if(optimizeBy == "distance") {
                                    val tspInputStream = assets.open("direct4me_locations_distance.tsp")
                                    val outFile = File(filesDir, "direct4me_locations_distance.tsp")
                                    tspInputStream.use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
                                }

                                else if(optimizeBy == "time") {
                                    val tspInputStream = assets.open("direct4me_locations_time.tsp")
                                    val outFile = File(filesDir, "direct4me_locations_time.tsp")
                                    tspInputStream.use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
                                }
                                val tsp = TSP(outFile.absolutePath, maxFe = 200000)


                                val selectedIndices = selectedCities.map { it.index }
                                filterTspBySelectedCities(tsp, selectedIndices)

                                val ga = GA(
                                    populationSize = gaParams.populationSize,
                                    crossoverChance = gaParams.crossoverChance,
                                    mutationChance = gaParams.mutationChance
                                )

                                val bestTour: Tour = ga.run(tsp)
                                Log.d("neke", "Best tour distance: ${bestTour.distance}")

                                val totalDistance = bestTour.distance
                                val coordinateList: List<LatLng> = createLatLngList(bestTour.path)
                                val latLngArrayList: ArrayList<LatLng> = ArrayList(coordinateList)

                                val intent = Intent(this, MapActivity::class.java).apply {
                                    putExtra("latLngList", latLngArrayList)
                                }
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

    private fun filterTspBySelectedCities(tsp: TSP, selectedIndices: List<Int>) {
        val filteredCities = tsp.cities.filter { city ->
            selectedIndices.contains(city.index)
        }

        val oldIndexes = filteredCities.map { it.index }

        filteredCities.forEachIndexed { newIdx, city ->
            city.index = newIdx + 1
        }

        val newSize = filteredCities.size
        val newWeights = MutableList(newSize) { DoubleArray(newSize) }


        for (i in 0 until newSize) {
            val oldIndexI = oldIndexes[i]
            for (j in 0 until newSize) {
                val oldIndexJ = oldIndexes[j]
                newWeights[i][j] = tsp.weights[oldIndexI - 1][oldIndexJ - 1]
            }
        }

        tsp.cities = filteredCities.toMutableList()
        tsp.weights = newWeights
        tsp.number = newSize

        if (filteredCities.isNotEmpty()) {
            tsp.start = filteredCities[0].copy()
        }
    }


    private fun estimateTime(distance: Double): Double {
        return distance * 1.2
    }
}

fun createLatLngList(routePath: List<TSP.City>): List<LatLng> {
    return routePath.map { city ->
        LatLng(city.y, city.x)
    }
}

data class CitySelectionItem(
    val index: Int,
    val lat: Double,
    val lng: Double,
    var isSelected: Boolean
)


data class GaParams(
    val populationSize: Int,
    val crossoverChance: Double,
    val mutationChance: Double
)

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

    var cities by remember { mutableStateOf(initialCities) }

    var populationSize by remember { mutableStateOf("100") }
    var crossoverChance by remember { mutableStateOf("0.8") }
    var mutationChance by remember { mutableStateOf("0.1") }

    var optimizeBy by remember { mutableStateOf("distance") }

    Column(modifier = Modifier.padding(16.dp)) {

        Text(text = "Izberite mesta:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
