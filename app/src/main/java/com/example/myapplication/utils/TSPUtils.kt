package com.example.myapplication.utils

import android.util.Log
import com.example.myapplication.CitySelectionItem
import java.io.InputStream

object TSPUtils {
    fun parseCitiesFromTspFile(inputStream: InputStream): List<CitySelectionItem> {
        val cities = mutableListOf<CitySelectionItem>()
        val lines = inputStream.bufferedReader().readLines()
        val displaySectionIndex = lines.indexOfFirst { it.contains("DISPLAY_DATA_SECTION") }

        if (displaySectionIndex == -1) {
            Log.e("neke", "DISPLAY_DATA_SECTION not found in the .tsp file.")
            throw IllegalArgumentException("DISPLAY_DATA_SECTION not found in the .tsp file.")
        }

        Log.d("neke", "DISPLAY_DATA_SECTION found at line: $displaySectionIndex")

        for (i in displaySectionIndex + 1 until lines.size) {
            val line = lines[i].trim()
            if (line == "EOF") break
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 3) {
                val index = parts[0].toIntOrNull()
                val lat = parts[1].toDoubleOrNull()
                val lng = parts[2].toDoubleOrNull()

                if (index == null || lat == null || lng == null) {
                    Log.e("neke", "Failed to parse city from line: '$line'")
                    continue // Skip invalid lines
                }

                cities.add(CitySelectionItem(index, lat, lng, true))
                Log.d("neke", "Added city: $index ($lat, $lng)")
            } else {
                Log.e("neke", "Invalid city line format: '$line'")
            }
        }

        if (cities.isEmpty()) {
            Log.e("neke", "No valid cities found in DISPLAY_DATA_SECTION.")
            throw IllegalArgumentException("No valid cities found in DISPLAY_DATA_SECTION.")
        }

        Log.d("neke", "Parsed ${cities.size} cities from TSP file.")
        return cities
    }
}
