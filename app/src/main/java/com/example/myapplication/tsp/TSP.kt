package com.example.myapplication.tsp

import java.io.File

class TSP(path: String, var maxFe: Int) {

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

    }

    lateinit var name: String
    lateinit var start: City
    var cities: MutableList<City> = mutableListOf()
    var number = 0
    lateinit var weights: MutableList<DoubleArray>
    var distanceType = DistanceType.EUCLIDEAN
    var currentEval = 0

    init {
        loadData(path)
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

    private fun loadData (path: String) {
        val file = File(path)
        val lines = file.readLines()
        var values = lines[0].replace(" ", "").split(":")
        name = values[1]
        var index = lines.indexOfFirst { it.contains("DIMENSION") }
        values = lines[index].replace(" ", "").split(":")
        number = values[1].toInt()
        index = lines.indexOfFirst { it.contains("EDGE_WEIGHT_TYPE") }
        values = lines[index].replace(" ", "").split(":")

        if(values[1] == "EXPLICIT") {
            index = lines.indexOfFirst { it.contains("EDGE_WEIGHT_SECTION") }
            weights = readExplicit(lines, index + 1)
        }
        else if (values[1] == "EUC_2D") {
            index = lines.indexOfFirst { it.contains("NODE_COORD_SECTION") }
            weights = readEuc2D(lines, index + 1)
        }
        if (cities.isNotEmpty()) {
            start = cities[0].copy()
        } else {
            throw IllegalStateException("No cities loaded. Ensure that the data file contains city information.")
        }
    }

    private fun readExplicit(lines: List<String>, index: Int): MutableList<DoubleArray> {
        var connections = MutableList(number) { DoubleArray(number) }
        for(i in 0 until number) {
            val line = lines[index + i]
            var values = line.split(" ")
            values = values.filterNot { it.isEmpty() }
            for (j in 0 until number) {
                connections[i][j] = values[j].toDouble()
            }
        }

        var index = lines.indexOfFirst { it.contains("DISPLAY_DATA_SECTION") }
        index++
        for (i in 0 until number) {
            val line = lines[index + i]
            var values = line.split(" ")
            values = values.filterNot { it.isEmpty() }
            cities.add(City(values[0].toInt(), values[1].toDouble(), values[2].toDouble()))
        }
        distanceType = DistanceType.WEIGHTED
        return connections
    }
    private fun readEuc2D(lines: List<String>, index: Int): MutableList<DoubleArray> {
        for(i in 0 until number) {
            val line = lines[index + i]
            var values = line.split(" ")
            values = values.filterNot { it.isEmpty() }
            val city = City(values[0].toInt(), values[1].toDouble(), values[2].toDouble())
            cities.add(city)
        }

        var connections = MutableList(number) { DoubleArray(number) }
        for(i in 0 until number) {
            for(j in 0 until number) {
                connections[i][j] = calculateEuclideanDistance(cities[i], cities[j]).toDouble()
            }
        }
        distanceType = DistanceType.WEIGHTED
        return connections
    }
}