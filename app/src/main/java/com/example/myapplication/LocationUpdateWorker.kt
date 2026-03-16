package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

class LocationUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "LocationUpdateWorker"
        const val WORK_NAME = "location_update_work"
        private const val CHANNEL_ID = "location_updates_channel"
        private const val NOTIFICATION_ID = 1
        private const val USERS_CHANNEL_ID = "users_nearby_channel"
        private const val USERS_NOTIFICATION_ID = 2
        private const val PREFS_NAME = "user_count_prefs"
        private const val KEY_PREVIOUS_COUNT = "previous_user_count"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting location update work")

        return try {
            createNotificationChannel()
            createUsersNotificationChannel()

            val sharingEnabled = PreferenceData.getInstance().getSharing(applicationContext)
            if (!sharingEnabled) {
                Log.d(TAG, "Location sharing is disabled, skipping update")
                return Result.success()
            }

            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission")
                return Result.failure()
            }

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location: Location? = fusedLocationClient.lastLocation.await()

            if (location != null) {
                Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")

                val repository = DataRepository.getInstance(applicationContext)

                val previousUsers = repository.getUsersSync()
                val previousCount = previousUsers.filterNotNull().filter { it.uid != "me" }.size
                Log.d(TAG, "Previous user count: $previousCount")

                val error = repository.apiUpdateGeofence(
                    location.latitude,
                    location.longitude,
                    100.0
                )

                if (error.isEmpty()) {
                    Log.d(TAG, "Location updated successfully on server")
                    
                    val usersError = repository.apiGeofenceUsers()

                    if (usersError.isEmpty()) {
                        Log.d(TAG, "Users list updated successfully")

                        val newUsers = repository.getUsersSync()
                        val newCount = newUsers.filterNotNull().filter { it.uid != "me" }.size
                        Log.d(TAG, "New user count: $newCount")
                        
                        val diff = newCount - previousCount

                        showUsersNotification(newCount, diff)
                        
                    } else {
                        Log.e(TAG, "Failed to fetch users: $usersError")
                    }
                    
                    Result.success()
                } else {
                    Log.e(TAG, "Failed to update location on server: $error")
                    Result.retry()
                }
            } else {
                Log.e(TAG, "Failed to get location")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in location update work", e)
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Aktualizácie polohy"
            val descriptionText = "Notifikácie o aktualizáciách polohy na pozadí"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createUsersNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Používatelia v okolí"
            val descriptionText = "Notifikácie o používateľoch vo vašom okolí"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(USERS_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, text: String) {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID).apply {
            setContentTitle(title)
            setContentText(text)
            setSmallIcon(R.drawable.ic_profile)
            priority = NotificationCompat.PRIORITY_DEFAULT
            setAutoCancel(true)
        }

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Missing POST_NOTIFICATIONS permission")
            return
        }

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, builder.build())
    }
    
    private fun showUsersNotification(totalUsers: Int, diff: Int) {
        val title = when {
            totalUsers == 0 -> "Nikto v okolí"
            totalUsers == 1 -> "1 používateľ v okolí"
            totalUsers in 2..4 -> "$totalUsers používatelia v okolí"
            else -> "$totalUsers používateľov v okolí"
        }
        
        val text = when {
            diff > 0 -> "Pribudlo: $diff"
            diff < 0 -> "Ubudlo: ${-diff}"
            else -> "Bez zmeny"
        }
        
        Log.d(TAG, "Showing users notification: $title - $text")
        
        val builder = NotificationCompat.Builder(applicationContext, USERS_CHANNEL_ID).apply {
            setContentTitle(title)
            setContentText(text)
            setSmallIcon(R.drawable.ic_profile)
            priority = NotificationCompat.PRIORITY_HIGH
            setAutoCancel(true)
        }

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Missing POST_NOTIFICATIONS permission")
            return
        }

        NotificationManagerCompat.from(applicationContext).notify(USERS_NOTIFICATION_ID, builder.build())
    }
}
