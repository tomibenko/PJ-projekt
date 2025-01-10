package com.example.myapplication.tsp

import android.util.Log
import java.io.File

class TSP(path: String, var maxFe: Int, selectedCityIndices: List<Int>? = null) {

    enum class DistanceType { EUCLIDEAN, WEIGHTED }

    class City {
        var index = 0
        var x = 0.0
        var y = 0.0

        constructor(index: Int, x: Double, y: Double) {
            this.index = index
            this.x = x
            this.y = y
        }

        fun copy(): City {
            return City(index, x, y)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is City) return false
            return index == other.index
        }

        override fun hashCode(): Int {
            return index
        }
    }

    lateinit var name: String
    lateinit var start: City
    var cities: MutableList<City> = mutableListOf()
    var number = 0
    lateinit var weights: MutableList<DoubleArray>
    var distanceType = DistanceType.EUCLIDEAN
    var currentEval = 0

    init {
        loadData(path, selectedCityIndices)
        currentEval = 0
    }

    fun evaluate(tour: Tour) {
        var distance = 0.0
        distance += calculateDistance(start, tour.path[0])
        for(index in 0 until number) {
            distance +=
                if (index + 1 < number) calculateDistance(tour.path[index], tour.path[index + 1])
                else calculateDistance(tour.path[index], start)
        }
        tour.distance = distance
        currentEval++
    }

    private fun calculateDistance(originCity: City, destinationCity: City): Double {
        return when (distanceType) {
            DistanceType.EUCLIDEAN -> calculateEuclideanDistance(originCity, destinationCity)

            DistanceType.WEIGHTED -> weights[originCity.index - 1][destinationCity.index - 1]
        }
    }

    private fun calculateEuclideanDistance(originCity: City, destinationCity: City): Double {
        return Math.sqrt(
            Math.pow(
                (originCity.x - destinationCity.x),
                2.0
            ) + Math.pow((originCity.y - destinationCity.y), 2.0)
        )
    }

    fun generateTour(): Tour {
        val tour = Tour(number)
        for(i in 0 until number) {
            tour.setCity(i, cities[i])
        }
        tour.path.shuffle()
        return tour
    }

    private fun loadData (path: String, selectedCityIndices: List<Int>?) {
        try {
            val file = File(path)
            val lines = file.readLines()
            var values = lines[0].replace(" ", "").split(":")
            name = values[1]
            Log.d("neke", "TSP Name: $name")

            var index = lines.indexOfFirst { it.contains("DIMENSION") }
            values = lines[index].replace(" ", "").split(":")
            number = values[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid DIMENSION value.")
            Log.d("neke", "Number of cities: $number")

            index = lines.indexOfFirst { it.contains("EDGE_WEIGHT_TYPE") }
            values = lines[index].replace(" ", "").split(":")
            Log.d("neke", "EDGE_WEIGHT_TYPE: ${values[1]}")

            if(values[1] == "EXPLICIT") {
                index = lines.indexOfFirst { it.contains("EDGE_WEIGHT_SECTION") }
                weights = readExplicit(lines, index + 1, selectedCityIndices)
            }
            else if (values[1] == "EUC_2D") {
                index = lines.indexOfFirst { it.contains("NODE_COORD_SECTION") }
                weights = readEuc2D(lines, index + 1, selectedCityIndices)
            }

            if (cities.isNotEmpty()) {
                start = cities[0].copy()
                Log.d("neke", "Start city: ${start.index} (${start.x}, ${start.y})")
            } else {
                Log.e("neke", "No cities loaded. Ensure that the data file contains city information.")
                throw IllegalStateException("No cities loaded. Ensure that the data file contains city information.")
            }
        }
        catch (e: Exception) {
            Log.e("neke", "Exception during TSP loadData: ${e.message}", e)
            throw e
        }
    }

    private fun readExplicit(lines: List<String>, index: Int, selectedCityIndices: List<Int>?): MutableList<DoubleArray> {
        try {
            val activeIndices = selectedCityIndices?.sorted() ?: (1..number).toList()
            val filteredNumber = activeIndices.size
            val connections = MutableList(filteredNumber) { DoubleArray(filteredNumber) }

            for(i in 0 until filteredNumber) {
                val lineIndex = index + activeIndices[i] - 1
                if (lineIndex >= lines.size) {
                    Log.e("neke", "Line index out of bounds: $lineIndex")
                    throw IndexOutOfBoundsException("Line index out of bounds: $lineIndex")
                }
                val line = lines[lineIndex]
                val values = line.split(" ").filterNot { it.isEmpty() }

                if (values.size < filteredNumber) {
                    Log.e("neke", "Insufficient weights in line: '$line'")
                    throw NumberFormatException("Insufficient weights in line: '$line'")
                }

                for (j in 0 until filteredNumber) {
                    val weightStr = values[activeIndices[j] - 1]
                    val weight = weightStr.toDoubleOrNull()
                    if(weight == null) {
                        Log.e("neke", "Invalid weight value '$weightStr' in line: '$line'")
                        throw NumberFormatException("Invalid weight value '$weightStr'")
                    }
                    connections[i][j] = weight
                }
            }

            val displaySectionIndex = lines.indexOfFirst { it.contains("DISPLAY_DATA_SECTION") }
            if (displaySectionIndex == -1) {
                Log.e("neke", "DISPLAY_DATA_SECTION not found in TSP file.")
                throw IllegalArgumentException("DISPLAY_DATA_SECTION not found in the .tsp file.")
            }

            for (i in 0 until filteredNumber) {
                val cityLineIndex = displaySectionIndex + 1 + activeIndices[i] - 1
                if (cityLineIndex >= lines.size) {
                    Log.e("neke", "City line index out of bounds: $cityLineIndex")
                    throw IndexOutOfBoundsException("City line index out of bounds: $cityLineIndex")
                }
                val line = lines[cityLineIndex].trim()
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val cityIndex = parts[0].toIntOrNull()
                    val lat = parts[1].toDoubleOrNull()
                    val lng = parts[2].toDoubleOrNull()

                    if(cityIndex == null || lat == null || lng == null) {
                        Log.e("neke", "Invalid city data in line: '$line'")
                        throw NumberFormatException("Invalid city data in line: '$line'")
                    }

                    cities.add(City(cityIndex, lat, lng))
                    Log.d("neke", "Added city: $cityIndex ($lat, $lng)")
                } else {
                    Log.e("neke", "Invalid city line format: '$line'")
                    throw NumberFormatException("Invalid city line format: '$line'")
                }
            }

            distanceType = DistanceType.WEIGHTED
            Log.d("neke", "Distance type set to WEIGHTED.")
            return connections
        }
        catch (e: Exception) {
            Log.e("neke", "Exception in readExplicit: ${e.message}", e)
            throw e
        }
    }
    private fun readEuc2D(lines: List<String>, index: Int, selectedCityIndices: List<Int>?): MutableList<DoubleArray> {
        val activeIndices = selectedCityIndices?.sorted() ?: (1..number).toList()
        val filteredNumber = activeIndices.size
        for(i in 0 until filteredNumber) {
            val line = lines[index + activeIndices[i] - 1]
            val parts = line.split(" ")
            if (parts.size >= 3) {
                val city = City(parts[0].toInt(), parts[1].toDouble(), parts[2].toDouble())
                cities.add(city)
            }
        }

        val connections = MutableList(filteredNumber) { DoubleArray(filteredNumber) }
        for(i in 0 until filteredNumber) {
            for(j in 0 until filteredNumber) {
                connections[i][j] = calculateEuclideanDistance(cities[i], cities[j])
            }
        }
        distanceType = DistanceType.WEIGHTED
        return connections
    }
}
