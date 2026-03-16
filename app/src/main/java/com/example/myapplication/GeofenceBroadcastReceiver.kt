package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
        private const val GEOFENCE_RADIUS = 100f
        private const val CHANNEL_ID = "geofence_exit_channel"
        private const val NOTIFICATION_ID = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) {
            Log.e(TAG, "Intent is null")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofencing error: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val triggeringLocation = geofencingEvent.triggeringLocation
            if (context == null || triggeringLocation == null) {
                Log.e(TAG, "Context or triggering location is null")
                return
            }
            
            Log.d(TAG, "Exited geofence at: ${triggeringLocation.latitude}, ${triggeringLocation.longitude}")
            
            scope.launch {
                val repository = DataRepository.getInstance(context.applicationContext)

                val previousUsers = repository.getUsersSync()
                val previousCount = previousUsers.filterNotNull().filter { it.uid != "me" }.size

                repository.apiUpdateGeofence(
                    triggeringLocation.latitude,
                    triggeringLocation.longitude,
                    GEOFENCE_RADIUS.toDouble()
                )
                
                repository.apiGeofenceUsers()

                val newUsers = repository.getUsersSync()
                val newCount = newUsers.filterNotNull().filter { it.uid != "me" }.size

                showGeofenceExitNotification(context, newCount, newCount - previousCount)
            }
            
            setupGeofence(triggeringLocation, context)
        }
    }

    private fun setupGeofence(location: Location, context: Context) {
        Log.d(TAG, "Setting up geofence at: ${location.latitude}, ${location.longitude}")

        val geofencingClient = LocationServices.getGeofencingClient(context.applicationContext)

        val geofence = Geofence.Builder()
            .setRequestId("my-geofence")
            .setCircularRegion(location.latitude, location.longitude, GEOFENCE_RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission")
            return
        }
        
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d(TAG, "New geofence created successfully")
            }
            addOnFailureListener { exception ->
                Log.e(TAG, "Failed to create geofence", exception)
            }
        }
    }
    
    private fun showGeofenceExitNotification(context: Context, totalUsers: Int, diff: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Opustenie kruhu"
            val descriptionText = "Notifikácie pri opustení geofence kruhu"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        val title = "Opustili ste krúh"
        val text = when {
            totalUsers == 0 -> "Nikto v novom okolí"
            totalUsers == 1 -> "1 používateľ v novom okolí"
            totalUsers in 2..4 -> "$totalUsers používatelia v novom okolí" + if (diff != 0) " (${if (diff > 0) "+" else ""}$diff)" else ""
            else -> "$totalUsers používateľov v novom okolí" + if (diff != 0) " (${if (diff > 0) "+" else ""}$diff)" else ""
        }
        
        Log.d(TAG, "Showing geofence exit notification: $title - $text")
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle(title)
            setContentText(text)
            setSmallIcon(R.drawable.ic_profile)
            priority = NotificationCompat.PRIORITY_HIGH
            setAutoCancel(true)
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Missing POST_NOTIFICATIONS permission")
            return
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
