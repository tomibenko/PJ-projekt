package com.example.myapplication.utils

object StatUtil {
    fun min(values: List<Double>): Double {
        return values.minOrNull() ?: Double.NaN
    }

    fun avg(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        return values.average()
    }

    fun stdDev(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val mean = avg(values)
        return Math.sqrt(values.map { Math.pow(it - mean, 2.0) }.average())
    }
}