package com.example.myapplication
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                LoginScreen()
            }
        }
    }
}

@Composable
fun LoginScreen(){
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    fun loginUser(username: String, password: String){
        val client = OkHttpClient()
        val url = "http://185.85.148.40:8080/users/login"

        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }.toString()

        val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                errorMessage = "Login failed: ${e.message}"
                successMessage = ""
            }

            override fun onResponse(call: Call, response: Response) {
                if(response.isSuccessful){
                    val responseData = response.body?.string()
                    val jsonResponse = JSONObject(responseData ?: "{}")
                    val userId = jsonResponse.optString("_id")

                    if(userId.isNotEmpty()){
                        successMessage = "Login successful"
                        errorMessage = ""

                        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                        with(sharedPreferences.edit()){
                            putString("userId", userId)
                            apply()
                        }

                        context.startActivity(Intent(context, MainActivity::class.java))
                    }
                    else{
                        errorMessage = "Login failed: Invalid response"
                        successMessage = ""
                    }
                }
                else{
                    errorMessage = "Login failed: ${response.message}"
                    successMessage = ""
                }
            }
        })
    }

    Column (
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
            Column(modifier = Modifier.padding(16.dp)){
                Text(
                    text = "Login",
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(text = "Password")},
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        val image = if(passwordVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility

                        IconButton(onClick = { passwordVisible = !passwordVisible }){
                            Icon(
                                imageVector = image,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { loginUser(username, password) },
                    modifier = Modifier.fillMaxWidth()
                ){
                    Text("Login")
                }
                if(errorMessage.isNotEmpty()){
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                if(successMessage.isNotEmpty()){
                    Text(
                        text = successMessage,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { /*TODO implement logic for face recognition*/ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login with face recognition")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Don't have an account? Register",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, RegisterActivity::class.java))
                    }
                )
            }
        }
    }
}


