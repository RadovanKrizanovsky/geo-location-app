package com.example.myapplication

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query

data class UserRegistration(
    val name: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val name: String,
    val password: String
)

data class RegistrationResponse(
    val uid: String,
    val access: String,
    val refresh: String
)

data class GeofenceResponse(
    val me: GeofenceMeResponse,
    val list: List<GeofenceUserResponse>
)

data class GeofenceUserResponse(
    val uid: String,
    val radius: Double,
    val updated: String,
    val name: String,
    val photo: String
)

data class GeofenceMeResponse(
    val uid: String,
    val lat: Double,
    val lon: Double,
    val radius: Double
)

data class UserProfileResponse(
    val id: String,
    val name: String,
    val photo: String
)

data class GeofenceUpdateRequest(
    val lat: Double,
    val lon: Double,
    val radius: Int
)

data class GeofenceUpdateResponse(
    val success: Boolean
)

data class RefreshTokenRequest(
    val refresh: String
)

data class RefreshTokenResponse(
    val uid: String,
    val access: String,
    val refresh: String
)

data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String
)

data class ChangePasswordResponse(
    val status: String
)

interface ApiService {
    
    @POST("user/create.php")
    suspend fun registerUser(@Body userInfo: UserRegistration): Response<RegistrationResponse>
    
    @POST("user/login.php")
    suspend fun loginUser(@Body loginInfo: LoginRequest): Response<RegistrationResponse>

    @GET("geofence/list.php")
    suspend fun listGeofence(): Response<GeofenceResponse>

    @GET("user/get.php")
    suspend fun getUserProfile(@Query("id") userId: String): Response<UserProfileResponse>

    @POST("user/refresh.php")
    fun refreshTokenBlocking(
        @Body refreshInfo: RefreshTokenRequest
    ): Call<RefreshTokenResponse>

    @POST("geofence/update.php")
    suspend fun updateGeofence(@Body request: GeofenceUpdateRequest): Response<GeofenceUpdateResponse>
    
    @DELETE("geofence/update.php")
    suspend fun deleteGeofence(): Response<GeofenceUpdateResponse>
    
    @POST("user/password.php")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ChangePasswordResponse>

    companion object {
        fun create(context: Context): ApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context))
                .authenticator(TokenAuthenticator(context))
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://zadanie.mpage.sk/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}
