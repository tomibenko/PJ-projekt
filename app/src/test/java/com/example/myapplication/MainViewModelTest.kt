package com.example.myapplication

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class MainViewModelTest {
    @Test
    fun testScanResultInit() {
        val viewModel = MainViewModel()
        assertEquals("", viewModel.scanResult.value)
    }
    @Test
    fun testTokenResultInit() {
        val viewModel = MainViewModel()
        assertEquals("", viewModel.tokenResult.value)
    }
}