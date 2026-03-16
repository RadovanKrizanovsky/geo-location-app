package com.example.myapplication

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.databinding.FragmentOtherUserProfileBinding
import com.google.android.material.snackbar.Snackbar
import com.mapbox.geojson.Point
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OtherUserProfileFragment : Fragment(R.layout.fragment_other_user_profile) {
    
    private var binding: FragmentOtherUserProfileBinding? = null
    private lateinit var viewModel: OtherUserProfileViewModel
    private val TAG = "OtherUserProfile"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OtherUserProfileViewModel(DataRepository.getInstance(requireContext())) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[OtherUserProfileViewModel::class.java]
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding = FragmentOtherUserProfileBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            model = viewModel
        }
        
        val userId = arguments?.getString("userId")
        if (userId == null) {
            Snackbar.make(view, "Chyba: ID používateľa nebolo poskytnuté", Snackbar.LENGTH_LONG).show()
            return
        }
        
        viewModel.loadUserProfile(userId)
        
        viewModel.userEntity.observe(viewLifecycleOwner) { user ->
            user?.let {
                binding?.let { bnd ->
                    bnd.tvOtherUsername.text = it.name
                    
                    val lastSeenText = "Naposledy videný: ${formatDate(it.updated)}"
                    bnd.tvLastSeen.text = lastSeenText

                    if (it.photo.isNotBlank()) {
                        val url = if (it.photo.startsWith("http")) it.photo 
                                  else "https://upload.mcomputing.eu/${it.photo}"
                        Glide.with(this@OtherUserProfileFragment)
                            .load(url)
                            .placeholder(R.drawable.ic_profile)
                            .circleCrop()
                            .into(bnd.ivOtherUserPhoto)
                    } else {
                        bnd.ivOtherUserPhoto.setImageResource(R.drawable.ic_profile)
                    }
                }
            }
        }
        
        viewModel.myGeofence.observe(viewLifecycleOwner) { myLocation ->
            myLocation?.let {
                if (it.lat != 0.0 && it.lon != 0.0) {
                    Log.d(TAG, "My geofence loaded: lat=${it.lat}, lon=${it.lon}, radius=${it.radius}")
                    setupMap(it.lat, it.lon, it.radius)
                }
            }
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(view, it, Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupMap(lat: Double, lon: Double, radius: Double) {
        binding?.mapViewOtherUser?.let { mapView ->
            mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
                try {
                    Log.d(TAG, "Setting up map with geofence: lat=$lat, lon=$lon, radius=$radius")
                    
                    mapView.getMapboxMap().setCamera(
                        CameraOptions.Builder()
                            .center(Point.fromLngLat(lon, lat))
                            .zoom(14.0)
                            .build()
                    )
                    
                    val circlePoints = createCirclePoints(lat, lon, radius)

                    val circleFeature = Feature.fromGeometry(
                        com.mapbox.geojson.Polygon.fromLngLats(listOf(circlePoints))
                    )
                    val featureCollection = FeatureCollection.fromFeatures(listOf(circleFeature))
                    
                    style.addSource(
                        geoJsonSource("geofence-source") {
                            featureCollection(featureCollection)
                        }
                    )
                    
                    style.addLayer(
                        fillLayer("geofence-fill", "geofence-source") {
                            fillColor("#4088FF")
                            fillOpacity(0.3)
                        }
                    )
                    
                    style.addLayer(
                        lineLayer("geofence-outline", "geofence-source") {
                            lineColor("#4088FF")
                            lineWidth(2.0)
                        }
                    )
                    
                    val annotationApi = mapView.annotations
                    val pointAnnotationManager = annotationApi.createPointAnnotationManager()
                    val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_profile)
                    
                    val pointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(lon, lat))
                        .withIconImage(bitmap)
                    
                    pointAnnotationManager.create(pointAnnotationOptions)
                    
                    Log.d(TAG, "Map setup complete with GeoJSON geofence circle")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up map", e)
                }
            }
        }
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
    
    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting date", e)
            dateString
        }
    }
    
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
