package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class HistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                HistoryScreen()
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    var historyItems by remember { mutableStateOf(emptyList<HistoryItem>()) }

    LaunchedEffect(Unit) {
        fetchHistory { items ->
            historyItems = items
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (historyItems.isEmpty()) {
            Text(text = "Loading...", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(historyItems) { item ->
                    HistoryItemView(item)
                }
            }
        }
    }
}

@Composable
fun HistoryItemView(item: HistoryItem) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "User: ${item.user}", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Timestamp: ${item.timestamp}", style = MaterialTheme.typography.bodyMedium)
        Text(text = if (item.success) "Success" else "Failure", style = MaterialTheme.typography.bodyMedium)
    }
}

data class HistoryItem(val user: String, val timestamp: String, val success: Boolean)

private fun fetchHistory(onResult: (List<HistoryItem>) -> Unit) {
    val client = OkHttpClient()
    val url = "http://185.85.148.40:8080/api/usageHistory"
    val json = JSONObject().apply {
        put("userId", "664c3976393467d8beca0a32") // Replace with actual user ID
    }.toString()
    val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
    val request = Request.Builder()
        .url(url)
        .post(body)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseData = response.body?.string()
            val jsonArray = JSONArray(responseData)

            val historyList = mutableListOf<HistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                historyList.add(
                    HistoryItem(
                        item.getString("user"),
                        item.getString("timestamp"),
                        item.getBoolean("success")
                    )
                )
            }

            onResult(historyList)
        }
    })
}


@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    MyApplicationTheme {
        HistoryScreen()
    }
}
