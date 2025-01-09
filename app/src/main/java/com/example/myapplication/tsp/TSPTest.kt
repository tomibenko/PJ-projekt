package com.example.myapplication.tsp

import com.example.myapplication.utils.StatUtil
import java.io.File
import java.util.Locale

object TSPTest {
    fun runTest() {
        val populationSize = 100
        val crossoverChance = 0.8
        val mutationChance = 0.1

        val maxFeValues = listOf(1000, 10_000, 100_000, 1_000_000)

        val tspFilePath = "C:\\sola\\drugi_letnik\\drugi_semester\\PROJEKT\\prj\\app\\src\\main\\assets\\bays29.tsp"

        val tspFile = File(tspFilePath)
        if (!tspFile.exists()) {
            println("The .tsp file was not found at the specified path: $tspFilePath")
            return
        }

        for (maxFe in maxFeValues) {
            println("======================================")
            println("Testing with maxFe = $maxFe")
            println("======================================")

            val bestDistances = mutableListOf<Double>()

            for (run in 1..30) {

                val tsp = TSP(tspFilePath, maxFe)

                val ga = GA(
                    populationSize = populationSize,
                    crossoverChance = crossoverChance,
                    mutationChance = mutationChance
                )

                val bestTour = ga.run(tsp)

                bestDistances.add(bestTour.distance)
            }

            val minDistance = StatUtil.min(bestDistances)
            val avgDistance = StatUtil.avg(bestDistances)
            val stdDevDistance = StatUtil.stdDev(bestDistances)

            println("\n--- Statistics for maxFe = $maxFe ---")
            println("MIN: ${"%.2f".format(Locale.US, minDistance)}")
            println("AVG: ${"%.2f".format(Locale.US, avgDistance)}")
            println("STD: ${"%.2f".format(Locale.US, stdDevDistance)}")
            println("--------------------------------------\n")
        }

        RandomUtils.setSeedFromTime()
    }
}