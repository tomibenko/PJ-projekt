package com.example.myapplication.utils

import java.io.File

class ParserUtil(val string: String) {
    fun parse(): List<String> {
        val file = File(string)
        val lines = mutableListOf<String>()
        file.useLines { lines.addAll(it) }
        return lines
    }
}