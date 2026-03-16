package com.example.myapplication

import android.content.Context
import android.util.Log
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(private val context: Context) : Authenticator {
    
    companion object {
        private const val TAG = "TokenAuthenticator"
    }
    
    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d(TAG, "authenticate() called - response code: ${response.code}")
        
        if (response.code == 401) {
            Log.d(TAG, "401 Unauthorized detected, attempting token refresh...")

            val userItem = PreferenceData.getInstance().getUser(context)

            if (userItem == null || userItem.refresh.isEmpty()) {
                Log.e(TAG, "No user or empty refresh token, cannot refresh. Clearing data.")
                PreferenceData.getInstance().clearData(context)
                return null
            }
            
            Log.d(TAG, "Current user found: ${userItem.uid}, attempting refresh...")
            
            try {
                val tokenResponse = ApiService.create(context)
                    .refreshTokenBlocking(RefreshTokenRequest(userItem.refresh))
                    .execute()
                
                if (tokenResponse.isSuccessful) {
                    tokenResponse.body()?.let { newTokenData ->
                        Log.d(TAG, "Token refresh successful! New access token obtained.")
                        
                        val newUser = User(
                            username = userItem.username,
                            email = userItem.email,
                            uid = userItem.uid,
                            access = newTokenData.access,
                            refresh = newTokenData.refresh,
                            photo = userItem.photo
                        )
                        
                        PreferenceData.getInstance().putUser(context, newUser)
                        Log.d(TAG, "New tokens saved to PreferenceData")

                        return response.request.newBuilder()
                            .header("Authorization", "Bearer ${newUser.access}")
                            .build()
                    }
                } else {
                    Log.e(TAG, "Token refresh failed with code: ${tokenResponse.code()}")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Exception during token refresh", ex)
            }
            
            Log.d(TAG, "Token refresh failed completely, clearing user data (logout)")
            PreferenceData.getInstance().clearData(context)
            return null
        }
        
        return null
    }
}
