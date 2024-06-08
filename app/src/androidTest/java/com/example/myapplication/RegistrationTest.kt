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



/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class RegistrationTest {

    @get:Rule
    val composeTestRoute = createAndroidComposeRule<RegisterActivity>()

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup(){
        mockWebServer = MockWebServer()
        mockWebServer.start(8080)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testRegistrationSuccess() {

        val response = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_CREATED)
            .setBody("""{"username": "newUser", "email": "user@example.com", "password": "encryptedPassword"}""")
        mockWebServer.enqueue(response)

        composeTestRoute.setContent {
            MyApplicationTheme {
                RegisterScreen()
            }
        }

        composeTestRoute.onNodeWithText("Email").performTextInput("user@example.com")
        composeTestRoute.onNodeWithText("Username").performTextInput("newUser")
        composeTestRoute.onNodeWithText("Password").performTextInput("password123")

        composeTestRoute.onNodeWithText("Register").performClick()

        composeTestRoute.onNodeWithText("Registration successful").assertExists()
    }
}