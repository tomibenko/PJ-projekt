package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.zxing.integration.android.IntentIntegrator
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import android.Manifest
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

class MainViewModel : ViewModel() {
    val scanResult: MutableState<String> = mutableStateOf("")
    val tokenResult: MutableState<String> = mutableStateOf("")
}

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val requestVideoCapture = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        mainViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
        }
    }

    fun dispatchTakeVideoIntent() {
        val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        if(takeVideoIntent.resolveActivity(packageManager) != null){
            startActivityForResult(takeVideoIntent, requestVideoCapture)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == requestVideoCapture && resultCode == RESULT_OK){
            val videoUri: Uri? = data?.data
            videoUri?.let {
                uploadVideoToBackend(it)
            }
        }
    }

    private fun uploadVideoToBackend(videoUri: Uri) {
        val contentResolver = contentResolver
        val file = File(cacheDir, "upload.mp4")
        val userId = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE).getString("userId", null)

        try {
            val inputStream = contentResolver.openInputStream(videoUri)
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)  // Enable retry mechanism
                .build()

            val mediaType = "video/mp4".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("video", file.name, file.asRequestBody(mediaType))
                .addFormDataPart("user_id", userId ?: "")
                .build()

            val request = Request.Builder()
                .url("http://92.63.28.41:8089/upload")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    println("Network failure: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (it.isSuccessful) {
                            val responseBody = it.body?.string()
                            if (responseBody != null) {
                                val jsonResponse = JSONObject(responseBody)
                                val status = jsonResponse.getString("status")
                                val message = jsonResponse.getString("message")
                                println("Upload successful: $status, $message")
                            } else {
                                println("Response body is null")
                            }
                        } else {
                            println("Upload failed: ${it.message}")
                        }
                    }
                }
            })
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            println("File not found: ${e.message}")
        } catch (e: IOException) {
            e.printStackTrace()
            println("IO exception: ${e.message}")
        } catch (e: JSONException) {
            e.printStackTrace()
            println("JSON exception: ${e.message}")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Unexpected error: ${e.message}")
        }
    }


@Composable
fun MainContent(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val scanResult = viewModel.scanResult.value

    val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
    val userId = sharedPreferences.getString("userId", null)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            val resultString = IntentIntegrator.parseActivityResult(result.resultCode, intent)?.contents
            Log.d("MainContent", "Scanned result: $resultString")
            if (resultString != null) {
                viewModel.scanResult.value = resultString
                val stringArray: List<String> = resultString.split("/")
                openBox(stringArray, viewModel, context) {
                    showDialog = true
                }
            } else {
                viewModel.scanResult.value = "No result"
                Log.d("MainContent", "No result from scan")
            }
        }
    }

    Column(
        modifier = modifier
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
                Greeting(
                    name = "Android",
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )
                if (userId != null) {
                    Button(
                        onClick = {
                            val integrator = IntentIntegrator(context as Activity)
                            integrator.setOrientationLocked(false)
                            integrator.setPrompt("Scan a QR code")
                            integrator.setBeepEnabled(true)
                            launcher.launch(integrator.createScanIntent())
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Scan QR Code")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, HistoryActivity::class.java)
                            context.startActivity(intent)
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "View History")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { (context as MainActivity).dispatchTakeVideoIntent() },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan your face")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            logout(context)
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ){
                        Text(text = "Logout")
                    }
                }
                else{
                    Button(
                        onClick = {
                            val intent = Intent(context, LoginActivity::class.java)
                            context.startActivity(intent)
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = modifier.fillMaxWidth()
                    ) {
                        Text(text = "Login")
                    }
                }
                if (scanResult.isNotEmpty()) {
                    Text(text = "Scan result: $scanResult")
                }
            }
        }
    }

    if (showDialog) {
        ShowSuccessDialog(viewModel.scanResult.value) {
            val stringArray: List<String> = scanResult.split("/")
            showDialog = false
            sendToDatabase(it, stringArray[4], userId)
        }
    }
}

fun logout(context: Context){
    val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
    with (sharedPreferences.edit()){
        remove("userId")
        apply()
    }

    val client = OkHttpClient()
    val url = "http://92.63.28.41:8080/users/logout"
    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    client.newCall(request).enqueue(object: Callback{
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Logout", "Failed to call logout API", e)
        }

        override fun onResponse(call: Call, response: Response) {
            if(response.isSuccessful){
                Log.d("Logout", "Logout successful")
            }
            else{
                Log.e("Logout", "Logout failed: ${response.message}")
            }
        }
    })

    val intent = Intent(context, LoginActivity::class.java)
    context.startActivity(intent)
    (context as Activity).finish()
}

fun openBox(qrCodeInfo: List<String>, viewModel: MainViewModel, context: Context, onCompletion: () -> Unit) {
    val client = OkHttpClient()
    val url = "https://api-d4me-stage.direct4.me/sandbox/v1/Access/openbox"
    val json = """
        {
            "boxId": ${qrCodeInfo[4].toInt()},
            "tokenFormat": 2
        }
    """
    Log.d("openBox", "Request payload: $json")
    val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
    val request = Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Authorization", "Bearer 9ea96945-3a37-4638-a5d4-22e89fbc998f")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("openBox", "Failed to call API", e)
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            Log.d("openBox", "API Response: $responseBody")
            if (responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                if (jsonResponse.has("data")) {
                    val data = jsonResponse.getString("data")
                    Log.d("openBox", "Data (Base64): $data")
                    try {
                        val decodedBytes = Base64.decode(data, Base64.DEFAULT)
                        Log.d("openBox", "Decoded bytes length: ${decodedBytes.size}")
                        val zipFile = File(context.cacheDir, "token.zip")
                        FileOutputStream(zipFile).use {
                            it.write(decodedBytes)
                        }
                        val tokenFile = unzipToken(zipFile, context)
                        if (tokenFile != null) {
                            playToken(tokenFile, context) {
                                onCompletion()
                            }
                        } else {
                            Log.e("openBox", "Failed to extract token.wav from the zip file")
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e("openBox", "Base64 decoding failed", e)
                    }
                } else {
                    Log.e("openBox", "No data in API response: $responseBody")
                    if (jsonResponse.has("validationErrors")) {
                        val validationErrors = jsonResponse.getJSONObject("validationErrors")
                        validationErrors.keys().forEach {
                            Log.e("openBox", "Validation error on $it: ${validationErrors.getString(it)}")
                        }
                    }
                }
            }
        }
    })
}

fun unzipToken(zipFile: File, context: Context): File? {
    val tokenFile = File(context.cacheDir, "token.wav")
    ZipInputStream(zipFile.inputStream()).use { zis ->
        var entry: ZipEntry?
        while (zis.nextEntry.also { entry = it } != null) {
            Log.d("unzipToken", "Found entry: ${entry?.name}")
            if (entry?.name == "token.wav") {
                FileOutputStream(tokenFile).use { fos ->
                    zis.copyTo(fos)
                }
                Log.d("unzipToken", "Extracted token.wav successfully")
                return tokenFile
            }
        }
    }
    Log.e("unzipToken", "token.wav not found in the zip file")
    return null
}

fun playToken(tokenFile: File, context: Context, onCompletion: () -> Unit) {
    val mediaPlayer = MediaPlayer().apply {
        setDataSource(context, Uri.fromFile(tokenFile))
        setOnPreparedListener {
            Log.d("playToken", "MediaPlayer is prepared, starting playback")
            start()
        }
        setOnCompletionListener {
            Log.d("playToken", "Playback completed, releasing MediaPlayer")
            release()
            onCompletion()
        }
        setOnErrorListener { mp, what, extra ->
            Log.e("playToken", "Error occurred: what=$what, extra=$extra")
            true
        }
        prepareAsync()
    }
}

fun sendToDatabase(success: Boolean, scanResult: String?, userId: String?) {
    scanResult?.let {
        val client = OkHttpClient()
        val url = "http://92.63.28.41:8080/api/addUsageHistory"
        val json = JSONObject().apply {
            put("id_pk", scanResult) // Replace with actual id_pk
            put("userId", userId) // Replace with actual user ID
            put("success", success)
        }.toString()
        val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("sendToDatabase", "Failed to send data", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("sendToDatabase", "Unexpected response: ${response.body?.string()}")
                } else {
                    Log.d("sendToDatabase", "Data sent successfully")
                }
            }
        })
    }
}

@Composable
fun ShowSuccessDialog(scanResult: String, onDismiss: (Boolean) -> Unit) {
    val stringArray: List<String> = scanResult.split("/")
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { onDismiss(false) },
            title = { Text("Operation Success") },
            text = { Text("Did the attempt succeed?") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    onDismiss(true)
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showDialog = false
                    onDismiss(false)
                }) {
                    Text("No")
                }
            }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}}
