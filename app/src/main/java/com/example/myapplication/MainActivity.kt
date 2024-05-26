package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

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
    }
}

class MainViewModel : ViewModel() {
    val scanResult: MutableState<String> = mutableStateOf("")
    val tokenResult: MutableState<String> = mutableStateOf("")
}

@Composable
fun MainContent(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

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
                openBox(stringArray, viewModel, context)
            } else {
                viewModel.scanResult.value = "No result"
                Log.d("MainContent", "No result from scan")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Greeting(
            name = "Android",
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = {
            val integrator = IntentIntegrator(context as Activity)
            integrator.setOrientationLocked(false)
            integrator.setPrompt("Scan a QR code")
            integrator.setBeepEnabled(true)
            launcher.launch(integrator.createScanIntent())
        }) {
            Text(text = "Scan QR Code")
        }
        Button(onClick = {
            val intent = Intent(context, HistoryActivity::class.java)
            context.startActivity(intent)
        }) {
            Text(text = "View History")
        }
        if (viewModel.scanResult.value.isNotEmpty()) {
            Text(text = "Scan result: ${viewModel.scanResult.value}")
        }
    }
}

fun openBox(qrCodeInfo: List<String>, viewModel: MainViewModel, context: Context) {
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
                                showSuccessDialog(context, viewModel)
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

fun showSuccessDialog(context: Context, viewModel: MainViewModel) {
    (context as? ComponentActivity)?.let { activity ->
        activity.setContent {
            var showDialog by remember { mutableStateOf(true) }
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Operation Success") },
                    text = { Text("Did the attempt succeed?") },
                    confirmButton = {
                        Button(onClick = {
                            showDialog = false
                            sendToDatabase(true, viewModel.scanResult.value)
                        }) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        Button(onClick = {
                            showDialog = false
                            sendToDatabase(false, viewModel.scanResult.value)
                        }) {
                            Text("No")
                        }
                    }
                )
            }
        }
    }
}

fun sendToDatabase(success: Boolean, scanResult: String) {
    val client = OkHttpClient()
    val url = "http://185.85.148.40:8080/api/usageHistory"
    val json = JSONObject().apply {
        put("id_pk","1947")
        put("userId", "123") // Replace with actual user ID
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
}
