package com.example.myapplication

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.myapplication.databinding.FragmentProfileBinding
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private var binding: FragmentProfileBinding? = null
    private lateinit var viewModel: ProfileViewModel
    private val TAG = "ProfileFragment"
    
    private val geofencingClient: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(requireContext())
    }
    private var isRequestingPermissions = false
    
    private val FOREGROUND_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    private val BACKGROUND_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }
    
    private val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
    
    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            Log.d(TAG, "Photo selected: $it")
            uploadPhoto(it)
        }
    }
    
    private val requestPhotoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openPhotoPicker()
        } else {
            Snackbar.make(requireView(), getString(R.string.snackbar_photo_permission_denied), Snackbar.LENGTH_LONG).show()
        }
    }
    
    private val requestForegroundPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "Foreground location permissions granted")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundPermissionLauncher.launch(BACKGROUND_PERMISSION)
            } else {
                requestNotificationPermission()
            }
        } else {
            Log.d(TAG, "Foreground location permissions denied")
            isRequestingPermissions = false
            PreferenceData.getInstance().putSharing(requireContext(), false)
            viewModel.sharingLocation.value = false
            Snackbar.make(requireView(), getString(R.string.snackbar_location_permission_denied), Snackbar.LENGTH_LONG).show()
        }
    }
    
    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "Background location permission granted")
            requestNotificationPermission()
        } else {
            Log.d(TAG, "Background location permission denied")
            isRequestingPermissions = false
            PreferenceData.getInstance().putSharing(requireContext(), false)
            viewModel.sharingLocation.value = false
            Snackbar.make(requireView(), getString(R.string.snackbar_background_permission_denied), Snackbar.LENGTH_LONG).show()
        }
    }
    
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isRequestingPermissions = false
        val allGranted = permissions.values.all { it }
        
        if (allGranted) {
            Log.d(TAG, "All permissions granted including notifications")
        } else {
            Log.d(TAG, "Notification permission denied, but continuing")
        }

        PreferenceData.getInstance().putSharing(requireContext(), true)
        setupGeofence()
        scheduleLocationUpdates()
        Snackbar.make(requireView(), getString(R.string.snackbar_location_enabled), Snackbar.LENGTH_SHORT).show()
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && NOTIFICATION_PERMISSION.isNotEmpty()) {
            requestNotificationPermissionLauncher.launch(NOTIFICATION_PERMISSION)
        } else {
            isRequestingPermissions = false
            PreferenceData.getInstance().putSharing(requireContext(), true)
            setupGeofence()
            scheduleLocationUpdates()
            Snackbar.make(requireView(), getString(R.string.snackbar_location_enabled), Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun hasPermissions(context: Context): Boolean {
        val hasForeground = FOREGROUND_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasForeground) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackground = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasBackground) return false
        }

        return true
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ProfileViewModel(DataRepository.getInstance(requireContext())) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding = FragmentProfileBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel
        }.also { bnd ->
            bnd.bottomNavMenu.setActiveIcon(BottomNavigationMenu.ActiveIcon.PROFILE)

            val user = PreferenceData.getInstance().getUser(requireContext())

            if (user != null) {
                bnd.tvUserId.text = getString(R.string.profile_user_id, user.uid)
                bnd.tvUsername.text = user.username
                bnd.btnLogout.text = getString(R.string.profile_logout)

                if (user.photo.isNotBlank()) {
                    val url = if (user.photo.startsWith("http")) user.photo else "https://upload.mcomputing.eu/${user.photo}"
                    Glide.with(this@ProfileFragment)
                        .load(url)
                        .placeholder(R.drawable.ic_profile)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(bnd.ivProfilePhoto)
                }

                loadUserProfile(user.uid, bnd)
            } else {
                bnd.tvUserId.text = getString(R.string.profile_user_id_default)
                bnd.tvUsername.text = getString(R.string.profile_not_logged_in)
                bnd.ivProfilePhoto.setImageResource(R.drawable.ic_profile)
                bnd.btnLogout.text = getString(R.string.btn_login)
            }
            
            viewModel.sharingLocation.postValue(
                PreferenceData.getInstance().getSharing(requireContext())
            )
            
            viewModel.sharingLocation.observe(viewLifecycleOwner) { isSharing ->
                isSharing?.let {
                    val wasSharing = PreferenceData.getInstance().getSharing(requireContext())
                    
                    if (it) {
                        if (!hasPermissions(requireContext())) {
                            if (!isRequestingPermissions) {
                                isRequestingPermissions = true
                                requestForegroundPermissionsLauncher.launch(FOREGROUND_PERMISSIONS)
                            }
                        } else if (!wasSharing) {
                            PreferenceData.getInstance().putSharing(requireContext(), true)
                            setupGeofence()
                            scheduleLocationUpdates()
                            Snackbar.make(requireView(), getString(R.string.snackbar_location_enabled), Snackbar.LENGTH_SHORT).show()
                        }
                    } else {
                        PreferenceData.getInstance().putSharing(requireContext(), false)

                        if (wasSharing) {
                            lifecycleScope.launch {
                                val repository = DataRepository.getInstance(requireContext())
                                repository.apiDeleteGeofence()
                            }
                            removeGeofence()
                            cancelLocationUpdates()
                            Snackbar.make(requireView(), getString(R.string.snackbar_location_disabled), Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            bnd.btnUploadPhoto.setOnClickListener {
                selectPhoto()
            }

            bnd.btnDeletePhoto.setOnClickListener {
                deletePhoto()
            }

            bnd.btnLogout.setOnClickListener {
                val currentUser = PreferenceData.getInstance().getUser(requireContext())
                if (currentUser != null) {
                    logout()
                } else {
                    findNavController().popBackStack(R.id.nav_graph, false)
                    findNavController().navigate(R.id.introFragment)
                }
            }
            
            bnd.btnChangePassword.setOnClickListener {
                findNavController().navigate(R.id.action_profile_to_change_password)
            }
        }
    }
    
    private fun selectPhoto() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openPhotoPicker()
                }
                else -> {
                    requestPhotoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openPhotoPicker()
                }
                else -> {
                    requestPhotoPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }
    
    private fun openPhotoPicker() {
        pickMedia.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.SingleMimeType("image/jpeg")
            )
        )
    }
    
    private fun uploadPhoto(uri: Uri) {
        binding?.btnUploadPhoto?.isEnabled = false
        
        lifecycleScope.launch {
            val repository = DataRepository.getInstance(requireContext())
            val error = repository.apiUploadPhoto(uri)
            
            binding?.btnUploadPhoto?.isEnabled = true
            
            if (error.isEmpty()) {
                Snackbar.make(requireView(), "Fotka úspešne nahraná", Snackbar.LENGTH_SHORT).show()
                PreferenceData.getInstance().getUser(requireContext())?.let { user ->
                    binding?.let { loadUserProfile(user.uid, it) }
                }
            } else {
                Snackbar.make(requireView(), "Chyba: $error", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun deletePhoto() {
        binding?.btnDeletePhoto?.isEnabled = false
        
        lifecycleScope.launch {
            val repository = DataRepository.getInstance(requireContext())
            val error = repository.apiDeletePhoto()
            
            binding?.btnDeletePhoto?.isEnabled = true
            
            if (error.isEmpty()) {
                Snackbar.make(requireView(), "Fotka odstránená", Snackbar.LENGTH_SHORT).show()
                PreferenceData.getInstance().getUser(requireContext())?.let { user ->
                    binding?.let { loadUserProfile(user.uid, it) }
                }
            } else {
                Snackbar.make(requireView(), "Chyba: $error", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun loadUserProfile(userId: String, bnd: FragmentProfileBinding) {
        lifecycleScope.launch {
            val repository = DataRepository.getInstance(requireContext())
            
            val (error, profile) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.apiGetUserProfile(userId)
            }

            if (error.isEmpty() && profile != null) {
                bnd.tvUsername.text = profile.name
                Log.d(TAG, "Profile loaded: ${profile.name}")
                if (profile.photo.isNotEmpty()) {
                    Log.d(TAG, "User has photo: ${profile.photo}")
                    val url = if (profile.photo.startsWith("http")) profile.photo else "https://upload.mcomputing.eu/${profile.photo}"
                    Glide.with(this@ProfileFragment)
                        .load(url)
                        .placeholder(R.drawable.ic_profile)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(bnd.ivProfilePhoto)
                } else {
                    bnd.ivProfilePhoto.setImageResource(R.drawable.ic_profile)
                }
            } else {
                Log.e(TAG, "Failed to load profile: $error")
            }
        }
    }
    
    private fun logout() {
        lifecycleScope.launch {
            val repository = DataRepository.getInstance(requireContext())
            val error = repository.logout()
            
            if (error.isEmpty()) {
                Log.d(TAG, "Logout successful, navigating to intro")
                
                val authViewModel = ViewModelProvider(requireActivity(), object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return AuthViewModel(DataRepository.getInstance(requireContext())) as T
                    }
                })[AuthViewModel::class.java]
                authViewModel.clearForm()

                findNavController().popBackStack(R.id.nav_graph, false)
                findNavController().navigate(R.id.introFragment)
            } else {
                Snackbar.make(requireView(), "Chyba pri odhlasovaní: $error", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupGeofence() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission")
            return
        }
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                Log.d(TAG, "Creating geofence at: ${location.latitude}, ${location.longitude}")
                
                geofencingClient.removeGeofences(listOf("my-geofence")).run {
                    addOnCompleteListener {
                        Log.d(TAG, "Previous geofence removal completed")

                        val geofence = Geofence.Builder()
                            .setRequestId("my-geofence")
                            .setCircularRegion(
                                location.latitude,
                                location.longitude,
                                100f
                            )
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                            .build()
                        
                        val geofencingRequest = GeofencingRequest.Builder()
                            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                            .addGeofence(geofence)
                            .build()
                        
                        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
                        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val geofencePendingIntent = PendingIntent.getBroadcast(
                            requireContext(),
                            0,
                            intent,
                            flags
                        )
                        
                        if (ActivityCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return@addOnCompleteListener
                        }
                        
                        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
                            addOnSuccessListener {
                                Log.d(TAG, "Geofence created successfully")
                                lifecycleScope.launch {
                                    val repository = DataRepository.getInstance(requireContext())
                                    repository.apiUpdateGeofence(
                                        location.latitude,
                                        location.longitude,
                                        100.0
                                    )
                                }
                            }
                            addOnFailureListener { exception ->
                                Log.e(TAG, "Failed to create geofence", exception)
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Last location is null")
                Snackbar.make(
                    requireView(),
                    "Nepodarilo sa získať polohu",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to get last location", exception)
            Snackbar.make(
                requireView(),
                "Chyba pri získavaní polohy",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
    
    private fun scheduleLocationUpdates() {
        Log.d(TAG, "Scheduling periodic location updates")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val repeatingRequest = PeriodicWorkRequestBuilder<LocationUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            LocationUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
        
        Log.d(TAG, "Periodic location updates scheduled successfully")
    }
    
    private fun cancelLocationUpdates() {
        Log.d(TAG, "Cancelling periodic location updates")
        WorkManager.getInstance(requireContext()).cancelUniqueWork(LocationUpdateWorker.WORK_NAME)
        Log.d(TAG, "Periodic location updates cancelled")
    }
    
    private fun removeGeofence() {
        Log.d(TAG, "Removing geofence")
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val geofencePendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            flags
        )
        
        geofencingClient.removeGeofences(geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d(TAG, "Geofence removed successfully")
            }
            addOnFailureListener { exception ->
                Log.e(TAG, "Failed to remove geofence", exception)
            }
        }
    }
    
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    
    }
}
