package com.plusgroup.pos.network

import android.content.Context
import com.plusgroup.pos.util.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // Chanje URL sa a si backend ou deplwaye sou yon lòt adrès.
    private const val BASE_URL = "https://plusgroup-lottery-api.onrender.com/api/v1/"

    private var retrofit: Retrofit? = null

    fun getService(context: Context): ApiService {
        if (retrofit == null) {
            val session = SessionManager(context.applicationContext)

            val authInterceptor = Interceptor { chain ->
                val token = session.getToken()
                val request = chain.request().newBuilder()
                if (!token.isNullOrEmpty()) {
                    request.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(request.build())
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}