package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CompressionInstrumentedTest {

    @Test
    fun testImageCompression() {
        // 1) We can get a Context from InstrumentationRegistry
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 2) Copy your img.jpg from assets -> a local file
        val originalFileName = "img.jpg"
        val compressedFileName = "img_compressed.bin"
        val decompressedFileName = "img_decompressed.jpg"

        val originalFile = File(context.filesDir, originalFileName)
        val compressedFile = File(context.filesDir, compressedFileName)
        val decompressedFile = File(context.filesDir, decompressedFileName)

        try {
            context.assets.open(originalFileName).use { input ->
                FileOutputStream(originalFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 3) Access your Python module
            val python = Python.getInstance()
            val module: PyObject = python.getModule("image_compression")

            // 4) Call your compression/decompression
            module.callAttr("compress_color_file",
                originalFile.absolutePath,
                compressedFile.absolutePath
            )
            Log.d("CompressionTest", "Compression completed via instrumented test.")

            module.callAttr("decompress_color_file",
                compressedFile.absolutePath,
                decompressedFile.absolutePath
            )
            Log.d("CompressionTest", "Decompression completed via instrumented test.")

            Log.d("CompressionTest", "Original size: ${originalFile.length()} bytes")
            Log.d("CompressionTest", "Compressed size: ${compressedFile.length()} bytes")
            Log.d("CompressionTest", "Decompressed size: ${decompressedFile.length()} bytes")

        } catch (e: Exception) {
            Log.e("CompressionTest", "Exception in instrumented test: ${e.message}", e)
            // Fail the test if something goes wrong:
            assert(false) { "Test failed: ${e.message}" }
        }
    }
}
