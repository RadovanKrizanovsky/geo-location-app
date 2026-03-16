package com.example.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.text.SimpleDateFormat
import java.util.*

class UserFeedAdapter(
    private val context: Context,
    private val onUserClick: (UserEntity) -> Unit
) : RecyclerView.Adapter<UserFeedAdapter.UserViewHolder>() {

    private val differ = AsyncListDiffer(this, UserDiffCallback())

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userIcon: ImageView = itemView.findViewById(R.id.item_image)
        val userName: TextView = itemView.findViewById(R.id.item_text)
        val userUpdated: TextView = itemView.findViewById(R.id.item_updated)
        val rootView = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.feed_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = differ.currentList[position]
        
        holder.userName.text = user.name

        holder.userUpdated.text = if (user.updated.isNotBlank()) {
            formatUpdatedTime(user.updated)
        } else {
            context.getString(R.string.feed_item_recently_active)
        }
        
        val photo = user.photo
        if (!photo.isNullOrBlank()) {
            val url = if (photo.startsWith("http")) photo else "https://upload.mcomputing.eu/$photo"
            Glide.with(holder.userIcon.context)
                .load(url)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .into(holder.userIcon)
        } else {
            holder.userIcon.setImageResource(R.drawable.ic_profile)
        }
        
        holder.rootView.setOnClickListener {
            onUserClick(user)
        }
    }

    override fun getItemCount() = differ.currentList.size

    fun updateItems(newUsers: List<UserEntity>) {
        differ.submitList(newUsers)
    }
    
    private fun formatUpdatedTime(updated: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(updated)
            val now = System.currentTimeMillis()
            val diff = now - (date?.time ?: now)
            
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            
            when {
                seconds < 60 -> context.getString(R.string.feed_item_active_now)
                minutes < 60 -> context.getString(R.string.feed_item_active_minutes, minutes)
                hours < 24 -> context.getString(R.string.feed_item_active_hours, hours)
                days < 7 -> context.getString(R.string.feed_item_active_days, days)
                else -> context.getString(R.string.feed_item_active_recently)
            }
        } catch (e: Exception) {
            context.getString(R.string.feed_item_recently_active)
        }
    }
}

class UserDiffCallback : DiffUtil.ItemCallback<UserEntity>() {
    override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
        return oldItem == newItem
    }
}
