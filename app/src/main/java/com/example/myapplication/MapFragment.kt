package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentMapBinding
import com.mapbox.geojson.Point
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlinx.coroutines.launch
import kotlin.random.Random

class MapFragment : Fragment(R.layout.fragment_map) {
    private var binding: FragmentMapBinding? = null
    private lateinit var viewModel: MapViewModel
    private var lastLocation: Point? = null
    private var lastUpdateLocation: Point? = null
    private var annotationManager: CircleAnnotationManager? = null
    private var userMarkerManager: PointAnnotationManager? = null
    private val addedMarkers = mutableSetOf<String>()
    private var isRefreshing = false
    private var locationListenersAdded = false
    private var cameraTrackingEnabled = true
    
    private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private val UPDATE_DISTANCE_THRESHOLD = 50.0
    private val TAG = "MapFragment"
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initLocationComponent()
            addLocationListeners()
        }
    }
    
    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MapViewModel(DataRepository.getInstance(requireContext())) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[MapViewModel::class.java]
        
        binding = FragmentMapBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            fragment = this@MapFragment
        }.also { bnd ->
            bnd.bottomNavMenu.setActiveIcon(BottomNavigationMenu.ActiveIcon.MAP)
            
            annotationManager = bnd.mapView.annotations.createCircleAnnotationManager()
            userMarkerManager = bnd.mapView.annotations.createPointAnnotationManager()
            Log.d(TAG, "Annotation managers created successfully")
            
            userMarkerManager?.addClickListener { annotation ->
                annotation.getData()?.let { data ->
                    val userId = data.asString
                    Log.d(TAG, "User marker clicked: $userId")

                    val bundle = Bundle().apply {
                        putString("userId", userId)
                    }
                    findNavController().navigate(R.id.action_map_to_other_profile, bundle)
                }
                true
            }

            val hasPermission = hasPermissions(requireContext())
            if (!hasPermission) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            onMapReady(hasPermission)
            
            viewModel.users.observe(viewLifecycleOwner) { users ->
                Log.d(TAG, "Users LiveData changed: users=${users?.size}")
                users?.let { userList ->
                    val filteredUsers = userList.filterNotNull()
                    Log.d(TAG, "Received ${filteredUsers.size} users to display on map")
                    
                    val meLocation = filteredUsers.find { it.uid == "me" }
                    if (meLocation != null) {
                        val myPoint = Point.fromLngLat(meLocation.lon, meLocation.lat)
                        val myRadius = meLocation.radius
                        Log.d(TAG, "Found my location from API: ${meLocation.lat}, ${meLocation.lon}, radius=${myRadius}m")
                        
                        binding?.mapView?.getMapboxMap()?.setCamera(
                            CameraOptions.Builder().center(myPoint).zoom(14.0).build()
                        )
                        
                        drawGeofenceCircle(myPoint, myRadius)

                        val otherUsers = filteredUsers
                            .filter { it.uid != "me" }
                            .distinctBy { it.uid }
                        
                        if (otherUsers.isNotEmpty()) {
                            displayUsersOnMap(otherUsers, myPoint, myRadius)
                        } else {
                            userMarkerManager?.deleteAll()
                            Log.d(TAG, "No users nearby - cleared all markers")
                        }
                    } else {
                        Log.w(TAG, "My location not found in database")
                    }
                } ?: Log.w(TAG, "Users LiveData is null")
            }
            
            bnd.myLocation.setOnClickListener {
                if (!hasPermissions(requireContext())) {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    cameraTrackingEnabled = true
                    lastLocation?.let { point ->
                        binding?.mapView?.getMapboxMap()?.setCamera(
                            CameraOptions.Builder().center(point).zoom(14.0).build()
                        )
                    } ?: run {
                        initLocationComponent()
                        addLocationListeners()
                    }
                    Log.d(TAG, "Camera tracking re-enabled")
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (hasPermissions(requireContext())) {
            Log.d(TAG, "onResume: Starting location tracking automatically")
            cameraTrackingEnabled = true
            binding?.mapView?.post {
                initLocationComponent()
                addLocationListeners()
                viewModel.refreshUsers()
            }
        } else {
            Log.d(TAG, "onResume: No location permissions yet")
        }
    }
    
    private fun onMapReady(enabled: Boolean) {
        binding?.mapView?.getMapboxMap()?.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(17.1077, 48.1486))
                .zoom(10.0)
                .build()
        )
        binding?.mapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS) {
            if (hasPermissions(requireContext())) {
                Log.d(TAG, "Map loaded, automatically starting location tracking")
                initLocationComponent()
                addLocationListeners()
            } else {
                Log.d(TAG, "Map loaded, but no location permissions - requesting")
            }
        }
    }
    
    private fun initLocationComponent() {
        val locationComponentPlugin = binding?.mapView?.location ?: return
        
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.pulsingEnabled = true
        }
    }
    
    private fun addLocationListeners() {
        if (locationListenersAdded) {
            Log.d(TAG, "Location listeners already added, skipping")
            return
        }
        
        val locationPlugin = binding?.mapView?.location ?: return
        val gesturesPlugin = binding?.mapView?.gestures ?: return
        
        locationPlugin.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        gesturesPlugin.addOnMoveListener(onMoveListener)
        locationListenersAdded = true
        Log.d(TAG, "Location listeners added successfully")
    }
    
    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener { point ->
        lastLocation = point
        addMarker(point)
        
        val sharingEnabled = PreferenceData.getInstance().getSharing(requireContext())
        if (sharingEnabled) {
            val shouldUpdate = lastUpdateLocation?.let { lastUpdate ->
                val distance = calculateDistance(
                    lastUpdate.latitude(), lastUpdate.longitude(),
                    point.latitude(), point.longitude()
                )
                distance >= UPDATE_DISTANCE_THRESHOLD
            } ?: true
            
            if (shouldUpdate && !isRefreshing) {
                isRefreshing = true
                lastUpdateLocation = point
                lifecycleScope.launch {
                    val repository = DataRepository.getInstance(requireContext())
                    repository.apiUpdateGeofence(point.latitude(), point.longitude(), 1000.0)
                    viewModel.refreshUsers()
                    Log.d(TAG, "Geofence updated and users refresh triggered")
                    kotlinx.coroutines.delay(500)
                    isRefreshing = false
                }
            }
        }
        
        if (cameraTrackingEnabled) {
            binding?.mapView?.getMapboxMap()?.setCamera(
                CameraOptions.Builder().center(point).zoom(14.0).build()
            )
            binding?.mapView?.gestures?.focalPoint = 
                binding?.mapView?.getMapboxMap()?.pixelForCoordinate(point)
        }
    }
    
    private fun refreshLocation(point: Point) {
    }
    
    private fun addMarker(point: Point) {
        annotationManager?.deleteAll()
        val circleAnnotationOptions = CircleAnnotationOptions()
            .withPoint(point)
            .withCircleRadius(8.0)
            .withCircleColor("#3BB2D0")
            .withCircleStrokeWidth(2.0)
            .withCircleStrokeColor("#FFFFFF")
        annotationManager?.create(circleAnnotationOptions)
    }
    
    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: com.mapbox.android.gestures.MoveGestureDetector) {
            cameraTrackingEnabled = false
            Log.d(TAG, "Camera tracking disabled - map moved by user")
        }
        
        override fun onMove(detector: com.mapbox.android.gestures.MoveGestureDetector): Boolean {
            return false
        }
        
        override fun onMoveEnd(detector: com.mapbox.android.gestures.MoveGestureDetector) {}
    }
    
    private fun onCameraTrackingDismissed() {
        cameraTrackingEnabled = false
        Log.d(TAG, "Camera tracking disabled, but listeners remain active")
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
    
    private fun drawGeofenceCircle(center: Point, radiusMeters: Double) {
        val mapboxMap = binding?.mapView?.getMapboxMap()
        if (mapboxMap == null) {
            Log.w(TAG, "Cannot draw geofence - MapView is null")
            return
        }
        
        val style = mapboxMap.getStyle()
        if (style == null) {
            mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) {
                drawGeofenceCircle(center, radiusMeters)
            }
            return
        }
        
        val circleCoordinates = createCirclePoints(
            center.latitude(), center.longitude(), radiusMeters
        )
        val circlePolygon = com.mapbox.geojson.Polygon.fromLngLats(listOf(circleCoordinates))
        val circleFeature = Feature.fromGeometry(circlePolygon)
        val featureCollection = FeatureCollection.fromFeature(circleFeature)
        
        val sourceId = "geofence-circle-source"
        val layerId = "geofence-circle-layer"
        val strokeLayerId = "${layerId}-stroke"
        
        try {
            style.removeStyleLayer(strokeLayerId)
        } catch (e: Exception) { }
        try {
            style.removeStyleLayer(layerId)
        } catch (e: Exception) { }
        try {
            style.removeStyleSource(sourceId)
        } catch (e: Exception) { }

        style.addSource(
            geoJsonSource(sourceId) {
                featureCollection(featureCollection)
            }
        )
        
        try {
            style.addLayerAbove(
                fillLayer(layerId, sourceId) {
                    fillColor("#4088FF")
                    fillOpacity(0.15)
                },
                "road-label"
            )
        } catch (e: Exception) {
            style.addLayer(
                fillLayer(layerId, sourceId) {
                    fillColor("#4088FF")
                    fillOpacity(0.15)
                }
            )
        }
        
        try {
            style.addLayerAbove(
                lineLayer(strokeLayerId, sourceId) {
                    lineColor("#4088FF")
                    lineWidth(3.0)
                },
                "road-label"
            )
        } catch (e: Exception) {
            style.addLayer(
                lineLayer(strokeLayerId, sourceId) {
                    lineColor("#4088FF")
                    lineWidth(3.0)
                }
            )
        }
        
        Log.d(TAG, "Drew geofence circle at ${center.latitude()}, ${center.longitude()}, radius=${radiusMeters}m")
    }
    
    private fun createCirclePoints(lat: Double, lon: Double, radiusInMeters: Double): List<Point> {
        val points = mutableListOf<Point>()
        val earthRadius = 6371000.0
        val numPoints = 64
        
        for (i in 0 until numPoints) {
            val angle = Math.toRadians((i * 360.0 / numPoints))
            val dx = radiusInMeters * Math.cos(angle)
            val dy = radiusInMeters * Math.sin(angle)
            
            val newLat = lat + (dy / earthRadius) * (180 / Math.PI)
            val newLon = lon + (dx / earthRadius) * (180 / Math.PI) / Math.cos(Math.toRadians(lat))
            
            points.add(Point.fromLngLat(newLon, newLat))
        }
        
        if (points.isNotEmpty()) {
            points.add(points[0])
        }
        
        return points
    }
    
    private fun displayUsersOnMap(users: List<UserEntity>, myLocation: Point, radiusMeters: Double) {
        Log.d(TAG, "displayUsersOnMap called with ${users.size} users, radius=${radiusMeters}m")
        userMarkerManager?.deleteAll()
        addedMarkers.clear()
        
        val defaultDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_profile)
        if (defaultDrawable == null) {
            Log.e(TAG, "Failed to load default profile icon drawable")
            return
        }
        
        val defaultBitmap = Bitmap.createBitmap(
            defaultDrawable.intrinsicWidth,
            defaultDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(defaultBitmap)
        defaultDrawable.setBounds(0, 0, canvas.width, canvas.height)
        defaultDrawable.draw(canvas)
        
        val circularDefaultBitmap = getCircularBitmap(defaultBitmap)
        Log.d(TAG, "Default bitmap created: ${defaultBitmap.width}x${defaultBitmap.height}")
        
        users.forEach { user ->
            val randomPoint = generateRandomPointInCircle(
                myLocation.latitude(), myLocation.longitude(), radiusMeters
            )
            
            if (user.photo.isNotBlank()) {
                val url = if (user.photo.startsWith("http")) user.photo 
                          else "https://upload.mcomputing.eu/${user.photo}"
                
                Glide.with(this)
                    .asBitmap()
                    .load(url)
                    .override(100, 100)
                    .circleCrop()
                    .timeout(5000)
                    .into(object : CustomTarget<Bitmap>(100, 100) {
                        override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                            if (!addedMarkers.contains(user.uid)) {
                                addUserMarker(randomPoint, bitmap, user.uid)
                            }
                        }
                        
                        override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        }
                        
                        override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                            if (!addedMarkers.contains(user.uid)) {
                                addUserMarker(randomPoint, circularDefaultBitmap, user.uid)
                            }
                        }
                    })
            } else {
                Log.d(TAG, "Adding marker for user ${user.uid} at random position")
                addUserMarker(randomPoint, circularDefaultBitmap, user.uid)
            }
        }
        
        Log.d(TAG, "Displayed ${users.size} users on map")
    }
    
    private fun addUserMarker(point: Point, bitmap: Bitmap, userId: String) {
        if (addedMarkers.contains(userId)) {
            Log.d(TAG, "Marker for user $userId already exists, skipping")
            return
        }
        
        Log.d(TAG, "addUserMarker called for user $userId at (${point.latitude()}, ${point.longitude()})")
        
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(point)
            .withIconImage(bitmap)
        
        val annotation = userMarkerManager?.create(pointAnnotationOptions)
        
        Log.d(TAG, "Marker created: annotation=$annotation")
        
        annotation?.let {
            it.setData(com.google.gson.JsonPrimitive(userId))
            addedMarkers.add(userId)
        }
    }
    
    private fun generateRandomPointInCircle(centerLat: Double, centerLon: Double, radiusInMeters: Double): Point {
        val earthRadius = 6371000.0

        val randomDistance = Math.sqrt(Random.nextDouble()) * radiusInMeters

        val randomAngle = Random.nextDouble() * 2 * Math.PI
        
        val dx = randomDistance * Math.cos(randomAngle)
        val dy = randomDistance * Math.sin(randomAngle)
        
        val newLat = centerLat + (dy / earthRadius) * (180 / Math.PI)
        val newLon = centerLon + (dx / earthRadius) * (180 / Math.PI) / Math.cos(Math.toRadians(centerLat))
        
        return Point.fromLngLat(newLon, newLat)
    }
    
    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint()
        val rect = Rect(0, 0, size, size)
        
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        
        return output
    }
    
    override fun onDestroyView() {
        binding?.mapView?.apply {
            location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
            gestures.removeOnMoveListener(onMoveListener)
        }
        locationListenersAdded = false
        cameraTrackingEnabled = true
        annotationManager?.deleteAll()
        userMarkerManager?.deleteAll()
        addedMarkers.clear()
        binding = null
        super.onDestroyView()
    }
}
