package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DataRepository private constructor(
    private val service: ApiService,
    private val uploadService: UploadApiService,
    private val cache: LocalCache,
    private val context: Context
) {
    companion object {
        const val TAG = "DataRepository"

        @Volatile
        private var INSTANCE: DataRepository? = null
        private val lock = Any()

        fun getInstance(context: Context): DataRepository =
            INSTANCE ?: synchronized(lock) {
                INSTANCE
                    ?: DataRepository(
                        ApiService.create(context),
                        UploadApiService.create(context),
                        LocalCache(AppRoomDatabase.getInstance(context).appDao()),
                        context.applicationContext
                    ).also { INSTANCE = it }
            }
    }

    suspend fun apiRegisterUser(username: String, email: String, password: String): Pair<String, User?> {
        if (username.isEmpty()) {
            return Pair(context.getString(R.string.error_username_empty), null)
        }
        if (email.isEmpty()) {
            return Pair(context.getString(R.string.error_email_empty), null)
        }
        if (password.isEmpty()) {
            return Pair(context.getString(R.string.error_password_empty), null)
        }
        
        try {
            val response = service.registerUser(UserRegistration(username, email, password))
            if (response.isSuccessful) {
                response.body()?.let { json_response ->
                    Log.d(TAG, "User registered successfully: ${json_response.uid}")
                    return Pair("", User(username, email, json_response.uid, json_response.access, json_response.refresh))
                }
            } else {
                Log.e(TAG, "Registration failed: ${response.code()} ${response.message()}")
            }
            return Pair(context.getString(R.string.error_registration_failed), null)
        } catch (ex: IOException) {
            ex.printStackTrace()
            Log.e(TAG, "Network error during registration", ex)
            return Pair(context.getString(R.string.error_network), null)
        } catch (ex: Exception) {
            ex.printStackTrace()
            Log.e(TAG, "Unexpected error during registration", ex)
        }
        
        return Pair(context.getString(R.string.error_fatal), null)
    }
    
    suspend fun apiLoginUser(email: String, password: String): Pair<String, User?> {
        Log.d(TAG, "Logging in user: $email")
        try {
            val response = service.loginUser(LoginRequest(email, password))
            
            if (response.isSuccessful) {
                response.body()?.let { loginResponse ->
                    if (loginResponse.uid.isEmpty() || loginResponse.uid == "-1") {
                        Log.e(TAG, "Login failed: invalid credentials (uid=-1)")
                        return Pair(context.getString(R.string.error_login_failed), null)
                    }
                    
                    Log.d(TAG, "Login successful: ${loginResponse.uid}")
                    
                    val user = User(
                        username = email,
                        email = email,
                        uid = loginResponse.uid,
                        access = loginResponse.access,
                        refresh = loginResponse.refresh,
                        photo = ""
                    )
                    
                    return Pair("", user)
                }
            }
            
            Log.e(TAG, "Login failed: ${response.code()} ${response.message()}")
            return Pair(context.getString(R.string.error_login_failed), null)
        } catch (ex: IOException) {
            ex.printStackTrace()
            Log.e(TAG, "Network error during login", ex)
            return Pair(context.getString(R.string.error_network), null)
        } catch (ex: Exception) {
            ex.printStackTrace()
            Log.e(TAG, "Fatal error during login", ex)
        }
        return Pair(context.getString(R.string.error_login_general), null)
    }
    
    suspend fun apiGeofenceUsers(): String {
        Log.d(TAG, "apiGeofenceUsers() started")
        
        val isSharing = PreferenceData.getInstance().getSharing(context)
        if (!isSharing) {
            Log.d(TAG, "Location sharing is disabled, clearing database and skipping API call")
            cache.deleteAllUsers()
            return ""
        }
        
        try {
            val response = service.listGeofence()
            Log.d(TAG, "API Response: isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { resp ->
                    Log.d(TAG, "API returned ${resp.list.size} users")
                    
                    val myLocation = UserEntity(
                        "me",
                        "Me",
                        "",
                        resp.me.lat,
                        resp.me.lon,
                        resp.me.radius.toDouble(),
                        ""
                    )
                    
                    val users = resp.list
                        .filter { it.uid != resp.me.uid }
                        .map { item ->
                            UserEntity(
                                item.uid, item.name, item.updated,
                                0.0, 0.0,
                                item.radius, item.photo
                            )
                        }
                    
                    cache.deleteAllUsers()
                    cache.insertUserItems(listOf(myLocation) + users)
                    Log.d(TAG, "Successfully saved my location (${resp.me.lat}, ${resp.me.lon}) and ${users.size} users to database (filtered out myself)")
                    
                    return ""
                }
                Log.e(TAG, "API response body is null")
            }

            Log.e(TAG, "Failed to load users: ${response.code()} ${response.message()}")
            return "Failed to load users"
        } catch (ex: IOException) {
            ex.printStackTrace()
            Log.e(TAG, "Network error while loading users", ex)
            return "Check internet connection. Failed to load users."
        } catch (ex: Exception) {
            ex.printStackTrace()
            Log.e(TAG, "Fatal error while loading users", ex)
        }
        return "Fatal error. Failed to load users."
    }



    suspend fun apiGetUserProfile(userId: String): Pair<String, UserProfileResponse?> {
        Log.d(TAG, "Fetching user profile for ID: $userId")
        try {
            val response = service.getUserProfile(userId)
            if (response.isSuccessful) {
                response.body()?.let { profile ->
                    Log.d(TAG, "User profile loaded: ${profile.name}")
                    return Pair("", profile)
                }
            }
            Log.e(TAG, "Failed to load profile: ${response.code()} ${response.message()}")
            return Pair("Failed to load profile", null)
        } catch (ex: IOException) {
            Log.e(TAG, "Network error while loading profile", ex)
            return Pair("Check internet connection", null)
        } catch (ex: Exception) {
            Log.e(TAG, "Error loading profile", ex)
            return Pair("Error loading profile", null)
        }
    }

    suspend fun apiUpdateGeofence(lat: Double, lon: Double, radius: Double = 1000.0): String {
        Log.d(TAG, "Updating geofence: lat=$lat, lon=$lon, radius=$radius")
        try {
            val response = service.updateGeofence(GeofenceUpdateRequest(lat, lon, radius.toInt()))
            if (response.isSuccessful) {
                response.body()?.let { result ->
                    if (result.success) {
                        Log.d(TAG, "Geofence updated successfully")
                        return ""
                    }
                }
            }
            Log.e(TAG, "Failed to update geofence: ${response.code()}")
            return "Failed to enable location sharing"
        } catch (ex: IOException) {
            Log.e(TAG, "Network error while updating geofence", ex)
            return "Check internet connection"
        } catch (ex: Exception) {
            Log.e(TAG, "Error updating geofence", ex)
            return "Error enabling location sharing"
        }
    }
    
    suspend fun apiDeleteGeofence(): String {
        Log.d(TAG, "Deleting geofence (disabling location sharing)")
        try {
            val response = service.deleteGeofence()
            if (response.isSuccessful) {
                response.body()?.let { result ->
                    if (result.success) {
                        Log.d(TAG, "Geofence deleted successfully")
                        return ""
                    }
                }
            }
            Log.e(TAG, "Failed to delete geofence: ${response.code()}")
            return "Failed to disable location sharing"
        } catch (ex: IOException) {
            Log.e(TAG, "Network error while deleting geofence", ex)
            return "Check internet connection"
        } catch (ex: Exception) {
            Log.e(TAG, "Error deleting geofence", ex)
            return "Error disabling location sharing"
        }
    }
    
    suspend fun apiUploadPhoto(imageUri: Uri): String {
        Log.d(TAG, "Uploading photo from URI: $imageUri")
        try {
            val file = uriToFile(imageUri) ?: return "Failed to read image file"

            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

            val response = uploadService.uploadPhoto(body)
            
            if (response.isSuccessful) {
                response.body()?.let { result ->
                    Log.d(TAG, "Photo uploaded successfully: ${result.photo}")
                    
                    PreferenceData.getInstance().getUser(context)?.let { user ->
                        val updatedUser = user.copy(photo = result.photo)
                        PreferenceData.getInstance().putUser(context, updatedUser)
                    }
                    
                    return ""
                }
            }
            
            Log.e(TAG, "Failed to upload photo: ${response.code()}")
            return "Failed to upload photo"
        } catch (ex: IOException) {
            Log.e(TAG, "Network error while uploading photo", ex)
            return "Check internet connection"
        } catch (ex: Exception) {
            Log.e(TAG, "Error uploading photo", ex)
            return "Error uploading photo"
        }
    }
    
    suspend fun apiDeletePhoto(): String {
        Log.d(TAG, "Deleting photo")
        try {
            val response = uploadService.deletePhoto()
            
            if (response.isSuccessful) {
                response.body()?.let { result ->
                    Log.d(TAG, "Photo deleted successfully")
                    
                    PreferenceData.getInstance().getUser(context)?.let { user ->
                        val updatedUser = user.copy(photo = "")
                        PreferenceData.getInstance().putUser(context, updatedUser)
                    }
                    
                    return ""
                }
            }
            
            Log.e(TAG, "Failed to delete photo: ${response.code()}")
            return "Failed to delete photo"
        } catch (ex: IOException) {
            Log.e(TAG, "Network error while deleting photo", ex)
            return "Check internet connection"
        } catch (ex: Exception) {
            Log.e(TAG, "Error deleting photo", ex)
            return "Error deleting photo"
        }
    }
    
    private fun uriToFile(uri: Uri): File? {
        return try {
            val resolver = context.contentResolver
            resolver.openInputStream(uri)?.use { inputStream ->
                var file = File(context.filesDir, "photo_upload.jpg")
                if (file.exists()) {
                    file.delete()
                }
                file = File(context.filesDir, "photo_upload.jpg")
                
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                Log.d(TAG, "File created at: ${file.absolutePath}")
                file
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to file", e)
            null
        }
    }
    
    suspend fun logout(): String {
        Log.d(TAG, "Logging out user")
        try {
            val sharingEnabled = PreferenceData.getInstance().getSharing(context)
            if (sharingEnabled) {
                apiDeleteGeofence()
            }

            cache.deleteAllUsers()
            Log.d(TAG, "Database cleared")

            PreferenceData.getInstance().clearData(context)
            Log.d(TAG, "User data cleared from preferences")
            
            return ""
        } catch (ex: Exception) {
            Log.e(TAG, "Error during logout", ex)
            return "Error during logout"
        }
    }
    
    suspend fun apiChangePassword(oldPassword: String, newPassword: String): String {
        Log.d(TAG, "Changing password")
        try {
            val response = service.changePassword(
                ChangePasswordRequest(oldPassword, newPassword)
            )
            
            if (response.isSuccessful) {
                response.body()?.let { changePasswordResponse ->
                    if (changePasswordResponse.status == "success") {
                        Log.d(TAG, "Password changed successfully")
                        return ""
                    } else {
                        Log.e(TAG, "Password change failed: ${changePasswordResponse.status}")
                        return "Nepodarilo sa zmeniť heslo"
                    }
                }
            } else {
                Log.e(TAG, "Password change failed with HTTP ${response.code()}")
                return if (response.code() == 401) {
                    "Nesprávne staré heslo"
                } else {
                    "Nepodarilo sa zmeniť heslo"
                }
            }
            
            return "Nepodarilo sa zmeniť heslo"
        } catch (ex: Exception) {
            Log.e(TAG, "Error changing password", ex)
            return "Chyba pri zmene hesla: ${ex.message}"
        }
    }

    fun getUsers() = cache.getUsers()
    
    suspend fun getUsersSync() = cache.getUsersSync()
    
    suspend fun getCachedUserById(userId: String): UserEntity? {
        Log.d(TAG, "Getting cached user by ID: $userId")
        return cache.getUserById(userId)
    }
}
