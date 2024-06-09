package com.example.myapplication
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.focus.focusModifier
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager

class FaceLoginActivity : ComponentActivity() {
    private val requestImageCapture = 1
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                FaceLoginScreen()
            }
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 0)
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, requestImageCapture)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestImageCapture && resultCode == RESULT_OK) {
            val imageUri: Uri? = data?.data
            imageUri?.let { uri ->
                if (userId.isNotEmpty()) {
                    uploadImage(userId, uri)
                }
            }
        }
    }

    private fun fetchUserId(username: String, callback: (String?) -> Unit) {
        val client = OkHttpClient()
        val url = "http://185.85.148.40:8080/users/getId"

        val json = JSONObject().apply {
            put("username", username)
        }.toString()

        val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    callback(null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val jsonResponse = JSONObject(responseData ?: "{}")
                    val fetchedUserId = jsonResponse.optString("_id")
                    runOnUiThread {
                        callback(fetchedUserId)
                    }
                } else {
                    runOnUiThread {
                        callback(null)
                    }
                }
            }
        })
    }

    private fun uploadImage(userId: String, imageUri: Uri) {
        val contentResolver = contentResolver
        val file = File(cacheDir, "upload.jpg")
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val outputStream = FileOutputStream(file)

            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream?.close()

            val client = OkHttpClient()
            val mediaType = "image/jpeg".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("user_id", userId)
                .addFormDataPart("image", file.name, file.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("http://185.85.148.40:5000/login")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        val jsonResponse = JSONObject(responseData ?: "{}")
                        val match = jsonResponse.optBoolean("match", false)

                        runOnUiThread {
                            if (match) {
                                startActivity(Intent(this@FaceLoginActivity, MainActivity::class.java))
                            } else {
                                println("Face login failed: No match found")
                            }
                        }
                    } else {
                        println("Face login failed: ${response.message}")
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Composable
    fun FaceLoginScreen() {
        val context = LocalContext.current
        var username by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        var successMessage by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Face Login",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (username.isEmpty()) {
                                errorMessage = "Username cannot be empty"
                                successMessage = ""
                            } else {
                                fetchUserId(username) { fetchedUserId ->
                                    if (fetchedUserId.isNullOrEmpty()) {
                                        errorMessage = "User not found"
                                        successMessage = ""
                                    } else {
                                        userId = fetchedUserId
                                        dispatchTakePictureIntent()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Capture Image")
                    }
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    if (successMessage.isNotEmpty()) {
                        Text(
                            text = successMessage,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}