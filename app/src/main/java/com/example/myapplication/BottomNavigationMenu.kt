package com.example.myapplication

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.findNavController

class BottomNavigationMenu(context: Context, attrs: AttributeSet? = null) : ConstraintLayout(context, attrs) {
    
    private val iconMap: ImageView
    private val iconFeed: ImageView
    private val iconProfile: ImageView
    
    init {
        LayoutInflater.from(context).inflate(R.layout.bottom_navigation_menu, this, true)
        
        iconMap = findViewById(R.id.iconMap)
        iconFeed = findViewById(R.id.iconFeed)
        iconProfile = findViewById(R.id.iconProfile)
        
        iconMap.setOnClickListener {
            findNavController().navigate(R.id.mapFragment)
        }
        
        iconFeed.setOnClickListener {
            findNavController().navigate(R.id.feedFragment)
        }
        
        iconProfile.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }
    }
    
    fun setActiveIcon(activeIcon: ActiveIcon) {
        iconMap.alpha = 0.5f
        iconFeed.alpha = 0.5f
        iconProfile.alpha = 0.5f
        
        when (activeIcon) {
            ActiveIcon.MAP -> iconMap.alpha = 1.0f
            ActiveIcon.FEED -> iconFeed.alpha = 1.0f
            ActiveIcon.PROFILE -> iconProfile.alpha = 1.0f
        }
    }
    
    enum class ActiveIcon {
        MAP, FEED, PROFILE
    }
}
