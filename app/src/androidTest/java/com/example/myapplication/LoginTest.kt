package com.example.myapplication

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.theme.MyApplicationTheme
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.Rule
import java.net.HttpURLConnection

@RunWith(AndroidJUnit4::class)
class LoginTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<LoginActivity>()

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start(8080)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testLoginSuccess() {
        val response = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody("""{"_id": "userId123", "twoFactor": false}""")
        mockWebServer.enqueue(response)

        composeTestRule.setContent {
            MyApplicationTheme {
                LoginScreen()
            }
        }

        composeTestRule.onNodeWithText("Username").performTextInput("testUser")
        composeTestRule.onNodeWithText("Password").performTextInput("password")
        composeTestRule.onNodeWithText("Login").performClick()

        composeTestRule.onNodeWithText("Login successful").assertExists()
    }
}