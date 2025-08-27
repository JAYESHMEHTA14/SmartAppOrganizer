// MainActivity.kt
package com.example.smartapporganizer; // <<--- ENSURE THIS IS YOUR PACKAGE NAME

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity; // Import AppCompatActivity
import androidx.lifecycle.lifecycleScope; // For coroutines
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;
import kotlinx.coroutines.withContext;
import java.util.Collections; // For sorting
import java.util.ArrayList;
// Removed java.util.List as it's not strictly needed with type inference for resolvedInfos

// MainActivity now implements OnAppClickListener from your AppRecyclerAdapter
class MainActivity : AppCompatActivity(), OnAppClickListener {

    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var appAdapter: AppRecyclerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to your XML layout
        setContentView(R.layout.activity_main)

        // Initialize views from the XML layout
        appsRecyclerView = findViewById(R.id.apps_recyclerview)
        loadingProgressBar = findViewById(R.id.loading_progressbar)

        setupRecyclerView()
        loadInstalledApps()
    }

    private fun setupRecyclerView() {
        // Create the adapter, passing 'this' (the Activity) as the click listener
        appAdapter = AppRecyclerAdapter(this)

        // Configure the GridLayoutManager
        // You can adjust spanCount based on your preference or screen size
        val spanCount = 4 // Example: 4 columns
        val gridLayoutManager = GridLayoutManager(this, spanCount)
        appsRecyclerView.layoutManager = gridLayoutManager

        appsRecyclerView.adapter = appAdapter

        // Performance Optimization: Set if item size doesn't change
        // For a grid of app icons, this is usually true.
        appsRecyclerView.setHasFixedSize(true)

        // You can also add ItemDecorations for spacing if needed, e.g.,
        // appsRecyclerView.addItemDecoration(GridSpacingItemDecoration(spanCount, spacingInPixels, includeEdge))
    }

    private fun loadInstalledApps() {
        loadingProgressBar.visibility = View.VISIBLE // Show loading indicator

        // Use lifecycleScope to launch a coroutine
        lifecycleScope.launch(Dispatchers.IO) { // Perform potentially long-running work on the IO dispatcher
            val pm: PackageManager = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

            // Get all launchable apps
            // Consider adding PackageManager.MATCH_ALL for more comprehensive results on newer Android versions if needed
            val resolvedInfos = pm.queryIntentActivities(mainIntent, 0) // Type inference will handle this
            val appList = ArrayList<AppItemInfo>()

            for (resolveInfo in resolvedInfos) {
                val appInfo = resolveInfo.activityInfo.applicationInfo // Get ApplicationInfo
                val label = pm.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName
                val icon = pm.getApplicationIcon(appInfo) // Load the icon
                appList.add(AppItemInfo(label, packageName, icon))
            }

            // Sort the app list alphabetically by label (case-insensitive)
            Collections.sort(appList, compareBy { it.label.lowercase() })

            // Switch back to the Main dispatcher to update the UI
            withContext(Dispatchers.Main) {
                appAdapter.submitList(appList) // Submit the list to the ListAdapter
                loadingProgressBar.visibility = View.GONE // Hide loading indicator
            }
        }
    }

    // Implementation of the OnAppClickListener interface
    override fun onAppClick(appItemInfo: AppItemInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(appItemInfo.packageName)
        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                // Log the exception or show a more specific error
                Toast.makeText(this, "Could not launch ${appItemInfo.label}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Cannot find launch intent for ${appItemInfo.label}", Toast.LENGTH_SHORT).show()
        }
    }
}

