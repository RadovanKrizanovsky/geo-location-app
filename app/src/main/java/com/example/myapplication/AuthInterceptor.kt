package com.example.myapplication

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
            .newBuilder()
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")

        val url = chain.request().url.toUrl().path
        if (url.contains("/user/create.php", true)
            || url.contains("/user/login.php", true)
            || url.contains("/user/refresh.php", true)
        ) {
            if (url.contains("/user/refresh.php", true)) {
                val user = PreferenceData.getInstance().getUser(context)
                user?.uid?.let { uid ->
                    request.addHeader("x-user", uid)
                }
            }
        } else {
            val user = PreferenceData.getInstance().getUser(context)
            user?.access?.let { token ->
                request.header("Authorization", "Bearer $token")
            }
        }

        request.addHeader("x-apikey", AppConfig.API_KEY)

        return chain.proceed(request.build())
    }
}
