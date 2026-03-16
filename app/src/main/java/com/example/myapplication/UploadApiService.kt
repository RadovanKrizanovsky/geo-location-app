package com.example.myapplication

import android.content.Context
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class PhotoUploadResponse(
    val id: String,
    val name: String,
    val photo: String
)

interface UploadApiService {
    
    @Multipart
    @POST("user/photo.php")
    suspend fun uploadPhoto(
        @Part image: MultipartBody.Part
    ): Response<PhotoUploadResponse>
    
    @DELETE("user/photo.php")
    suspend fun deletePhoto(): Response<PhotoUploadResponse>
    
    companion object {
        private const val BASE_URL = "https://upload.mcomputing.eu/"
        
        fun create(context: Context): UploadApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context))
                .authenticator(TokenAuthenticator(context))
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            return retrofit.create(UploadApiService::class.java)
        }
    }
}
