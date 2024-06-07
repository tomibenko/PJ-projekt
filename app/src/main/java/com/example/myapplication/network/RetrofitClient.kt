package com.example.myapplication.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

object RetrofitClient {
    private const val BASE_URL = "http://your-flask-server-url"

    val instance: Api by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(Api::class.java)
    }
}

interface Api {
    @Multipart
    @POST("/upload")
    fun uploadImage(
        @Part image: MultipartBody.Part
    ): Call<Void>
}
