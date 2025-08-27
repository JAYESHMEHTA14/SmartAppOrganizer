package com.example.smartapporganizer;

// AppRecyclerAdapter.kt

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull; // May not be strictly needed with ListAdapter, but good practice
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

// Define an interface for click listeners
// We'll define this outside the class or in a separate file if used by multiple adapters
interface OnAppClickListener {
    fun onAppClick(appItemInfo: AppItemInfo)
}

class AppRecyclerAdapter(private val onAppClickListener: OnAppClickListener) :
    ListAdapter<AppItemInfo, AppRecyclerAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_layout, parent, false) // Uses your item_app_layout.xml
        return AppViewHolder(itemView, onAppClickListener)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val currentApp = getItem(position)
        holder.bind(currentApp)
    }

    // The ViewHolder class
    class AppViewHolder(
        itemView: View,
        private val listener: OnAppClickListener
    ) : RecyclerView.ViewHolder(itemView) {
        // Get references to the views from item_app_layout.xml
        private val appIconImageView: ImageView = itemView.findViewById(R.id.app_icon_imageview)
        private val appNameTextView: TextView = itemView.findViewById(R.id.app_name_textview)
        private var currentAppItem: AppItemInfo? = null // To hold the item for the click listener

        init {
            // Set the click listener on the entire item view
            itemView.setOnClickListener {
                currentAppItem?.let { app -> // Ensure currentAppItem is not null
                    listener.onAppClick(app)
                }
            }
        }

        fun bind(appItem: AppItemInfo) {
            currentAppItem = appItem // Store the app item
            appNameTextView.text = appItem.label
            appIconImageView.setImageDrawable(appItem.icon)
            // Note: For very high-performance icon loading with many images,
            // you might consider an image loading library like Glide or Coil here,
            // but for system-loaded app icons, this direct approach is often sufficient
            // as the system does some level of caching.
        }
    }

    // DiffUtil.ItemCallback for efficient list updates
    class AppDiffCallback : DiffUtil.ItemCallback<AppItemInfo>() {
        override fun areItemsTheSame(oldItem: AppItemInfo, newItem: AppItemInfo): Boolean {
            // Check if items represent the same object (e.g., by unique ID)
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppItemInfo, newItem: AppItemInfo): Boolean {
            // Check if the content of the items is the same
            // For a data class, the default '==' implementation compares all properties.
            return oldItem == newItem
        }
    }
}
