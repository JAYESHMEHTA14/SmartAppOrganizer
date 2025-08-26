package com.example.smartapporganizer

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartapporganizer.ui.theme.SmartAppOrganizerTheme
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// ✅ Step 1: Folder model
data class AppFolder(
    val name: String,
    val apps: MutableList<ApplicationInfo> = mutableListOf()
)

// SharedPreferences Constants
private const val PREFS_NAME = "SmartAppOrganizerPrefs"
private const val KEY_ALL_FOLDER_NAMES = "allFolderNames"
private const val KEY_FOLDERS_PREFIX = "folder_"

// ADDED: For Smart Suggestions - App Categories and Keywords
val appCategoryKeywords: Map<String, List<String>> = mapOf(
    "Payment" to listOf("pay", "upi", "wallet", "gpay", "phonepe", "paytm", "bhim", "mobikwik", "bill", "finance", "bank", "payment", "send money"),
    "Shopping" to listOf("shop", "buy", "store", "deal", "cart", "amazon", "flipkart", "myntra", "commerce", "market", "order", "delivery"),
    "Social" to listOf("social", "chat", "message", "insta", "facebook", "twitter", "whatsapp", "connect", "friend", "network", "messenger", "telegram"),
    "Games" to listOf("game", "play", "rpg", "puzzle", "arcade", "gaming", "ludo", "chess"),
    "Travel" to listOf("travel", "trip", "hotel", "flight", "booking", "map", "navigation", "cab", "taxi", "irctc", "makemytrip"),
    "Music" to listOf("music", "song", "audio", "spotify", "wynk", "gaana", "jiosaavn", "player", "sound"),
    "Video" to listOf("video", "movie", "stream", "youtube", "netflix", "hotstar", "prime video", "player", "watch", "tv"),
    "Productivity" to listOf("work", "office", "doc", "note", "task", "drive", "sheet", "slide", "email", "organize", "tool", "utility", "scanner", "writer"),
    "Food" to listOf("food", "order", "delivery", "zomato", "swiggy", "restaurant", "eat", "recipe"),
    "News" to listOf("news", "article", "headline", "breaking", "times", "update")
)

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private fun saveFolders(foldersToSave: List<AppFolder>) {
        val editor = sharedPreferences.edit()
        val allFolderNames = foldersToSave.map { it.name }.toSet()
        editor.putStringSet(KEY_ALL_FOLDER_NAMES, allFolderNames)

        foldersToSave.forEach { folder ->
            val appPackageNames = folder.apps.map { it.packageName }.toSet()
            editor.putStringSet("$KEY_FOLDERS_PREFIX${folder.name}", appPackageNames)
        }
        editor.apply()
    }

    private fun loadFolders(pm: PackageManager, allInstalledLaunchableApps: List<ApplicationInfo>): Pair<List<AppFolder>, List<ApplicationInfo>> {
        val loadedFolders = mutableListOf<AppFolder>()
        val assignedAppPackageNames = mutableSetOf<String>()
        val allFolderNames = sharedPreferences.getStringSet(KEY_ALL_FOLDER_NAMES, emptySet()) ?: emptySet()

        allFolderNames.forEach { folderName ->
            val appPackageNamesInFolder = sharedPreferences.getStringSet("$KEY_FOLDERS_PREFIX$folderName", emptySet()) ?: emptySet()
            val appsInFolder = appPackageNamesInFolder.mapNotNull { packageName ->
                allInstalledLaunchableApps.find { it.packageName == packageName }
            }.toMutableList()
            loadedFolders.add(AppFolder(folderName, appsInFolder))
            assignedAppPackageNames.addAll(appPackageNamesInFolder)
        }
        val unassignedApps = allInstalledLaunchableApps.filterNot { assignedAppPackageNames.contains(it.packageName) }
        return Pair(loadedFolders, unassignedApps)
    }

    // ADDED: For Smart Suggestions - Helper function to find suggestions
    private fun findSuggestionsForFolder(
        folder: AppFolder,
        unassignedAppsList: List<ApplicationInfo>,
        pm: PackageManager
    ): List<ApplicationInfo> {
        val suggestions = mutableListOf<ApplicationInfo>()
        var identifiedCategoryKey: String? = null
        val folderNameLower = folder.name.lowercase()

        // 1. Try to identify category from folder name
        for ((categoryKey, keywords) in appCategoryKeywords) {
            if (folderNameLower.contains(categoryKey.lowercase()) ||
                keywords.any { keyword -> folderNameLower.contains(keyword) }) {
                identifiedCategoryKey = categoryKey
                break
            }
        }

        // 2. If not from folder name, try from apps already in the folder
        if (identifiedCategoryKey == null && folder.apps.isNotEmpty()) {
            // Check the last added app first for relevance, or a few apps for broader context
            val appsToScanInFolder = folder.apps.takeLast(2) // Check last few apps
            for (appInFolder in appsToScanInFolder) {
                val appLabel = appInFolder.loadLabel(pm).toString().lowercase()
                for ((categoryKey, keywords) in appCategoryKeywords) {
                    if (keywords.any { keyword -> appLabel.contains(keyword) }) {
                        identifiedCategoryKey = categoryKey
                        break
                    }
                }
                if (identifiedCategoryKey != null) break
            }
        }

        if (identifiedCategoryKey != null) {
            val categoryKeywords = appCategoryKeywords[identifiedCategoryKey] ?: return emptyList()
            unassignedAppsList.forEach { unassignedApp ->
                val appLabel = unassignedApp.loadLabel(pm).toString().lowercase()
                // Check against keywords of the identified category
                if (categoryKeywords.any { keyword -> appLabel.contains(keyword) }) {
                    // Ensure it's not already in the folder or already suggested
                    if (!folder.apps.any { it.packageName == unassignedApp.packageName } &&
                        !suggestions.any { it.packageName == unassignedApp.packageName }) {
                        suggestions.add(unassignedApp)
                    }
                }
            }
        }
        return suggestions.take(5) // Limit suggestions
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val pm: PackageManager = packageManager
        val allInstalledApps: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val launchableApps = allInstalledApps.filter { app ->
            pm.getLaunchIntentForPackage(app.packageName) != null
        }
        val sortedApps = launchableApps.sortedBy { it.loadLabel(pm).toString() }
        val (initialFolders, initialUnassignedApps) = loadFolders(pm, sortedApps)

        setContent {
            val folders = remember { mutableStateListOf<AppFolder>().apply { addAll(initialFolders) } }
            val unassignedApps = remember { mutableStateListOf<ApplicationInfo>().apply { addAll(initialUnassignedApps) } }

            val onFoldersUpdated: () -> Unit = {
                saveFolders(folders.toList())
                val allAppPackageNamesInFolders = folders.flatMap { folder -> folder.apps.map { it.packageName } }.toSet()
                val newUnassignedApps = sortedApps.filterNot { allAppPackageNamesInFolders.contains(it.packageName) }
                unassignedApps.clear()
                unassignedApps.addAll(newUnassignedApps)
            }

            SmartAppOrganizerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FoldersScreen(
                        folders = folders,
                        unassignedApps = unassignedApps,
                        pm = pm,
                        modifier = Modifier.padding(innerPadding),
                        onFoldersUpdated = onFoldersUpdated,
                        findSuggestions = { folder, currentUnassignedApps -> // ADDED: For Smart Suggestions
                            findSuggestionsForFolder(folder, currentUnassignedApps, pm)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(
    folders: MutableList<AppFolder>,
    unassignedApps: MutableList<ApplicationInfo>,
    pm: PackageManager,
    modifier: Modifier = Modifier,
    onFoldersUpdated: () -> Unit,
    findSuggestions: (AppFolder, List<ApplicationInfo>) -> List<ApplicationInfo> // ADDED: For Smart Suggestions
) {
    var selectedFolder by remember { mutableStateOf<AppFolder?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val context = LocalContext.current

    // ADDED: For Smart Suggestions - State for suggestion dialog
    var showSuggestionDialog by remember { mutableStateOf(false) }
    var suggestedAppsForDialog by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var activeFolderForSuggestion by remember { mutableStateOf<AppFolder?>(null) }

    // ADDED: For Smart Suggestions - Suggestion Dialog
    if (showSuggestionDialog && activeFolderForSuggestion != null && suggestedAppsForDialog.isNotEmpty()) {
        val currentActiveFolder = activeFolderForSuggestion!!
        AlertDialog(
            onDismissRequest = { showSuggestionDialog = false },
            title = { Text("Smart Suggestions") },
            text = {
                Column {
                    Text("Add these related apps to '${currentActiveFolder.name}'?")
                    Spacer(Modifier.height(8.dp))
                    // Scrollable list of suggested app names
                    Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                        suggestedAppsForDialog.forEach { appInfo ->
                            Text("- ${appInfo.loadLabel(pm)}")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    var appsAddedCount = 0
                    suggestedAppsForDialog.forEach { appToAdd ->
                        if (!currentActiveFolder.apps.any { it.packageName == appToAdd.packageName }) {
                            currentActiveFolder.apps.add(appToAdd)
                            appsAddedCount++
                        }
                    }
                    if (appsAddedCount > 0) {
                        onFoldersUpdated() // Call to save and refresh unassigned apps
                        Toast.makeText(context, "$appsAddedCount suggested apps added to '${currentActiveFolder.name}'", Toast.LENGTH_SHORT).show()
                    }
                    showSuggestionDialog = false
                }) { Text("Add All (${suggestedAppsForDialog.size})") }
            },
            dismissButton = {
                Button(onClick = { showSuggestionDialog = false }) { Text("No, Thanks") }
            }
        )
    }


    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        if (folders.none { it.name.equals(newFolderName, ignoreCase = true) }) {
                            folders.add(AppFolder(newFolderName))
                            onFoldersUpdated()
                            Toast.makeText(context, "Folder '$newFolderName' created", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Folder name '$newFolderName' already exists", Toast.LENGTH_SHORT).show()
                        }
                        newFolderName = ""
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                Button(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
            title = { Text("Create New Folder") },
            text = {
                TextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") }
                )
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { showCreateDialog = true }) {
            Text("Create New Folder")
        }
        Spacer(Modifier.height(16.dp))

        if (selectedFolder == null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp), // MODIFIED: Adaptive columns
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(folders) { folder ->
                    FolderCard(folder = folder, onClick = { selectedFolder = folder }) // MODIFIED: Extracted to FolderCard
                }
                items(unassignedApps) { app ->
                    AppIcon(app, pm, context, onClick = null)
                }
            }
        } else {
            val currentSelectedFolder = selectedFolder!!
            var searchText by remember { mutableStateOf("") }
            val filteredUnassignedApps = unassignedApps.filter {
                it.loadLabel(pm).toString().contains(searchText, ignoreCase = true)
            }

            Text("Folder: ${currentSelectedFolder.name}", style = MaterialTheme.typography.titleLarge) // MODIFIED: Style
            Spacer(Modifier.height(8.dp))

            Text("Apps in folder (tap to remove):", style = MaterialTheme.typography.titleSmall) // MODIFIED: Style
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp), // MODIFIED: Adaptive columns
                modifier = Modifier.heightIn(min = 100.dp, max = 200.dp), // MODIFIED: Height constraint
                contentPadding = PaddingValues(8.dp)
            ) {
                items(currentSelectedFolder.apps.toList()) { appInFolder ->
                    AppIcon(
                        app = appInFolder,
                        pm = pm,
                        context = context,
                        onClick = {
                            currentSelectedFolder.apps.remove(appInFolder)
                            onFoldersUpdated()
                            Toast.makeText(context, "'${appInFolder.loadLabel(pm)}' removed from '${currentSelectedFolder.name}'", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp)) // MODIFIED: Spacing

            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Search unassigned apps to add") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Tap app to add to '${currentSelectedFolder.name}':", style = MaterialTheme.typography.titleSmall) // MODIFIED: Style
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp), // MODIFIED: Adaptive columns
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(filteredUnassignedApps) { appToAdd ->
                    AppIcon(
                        app = appToAdd,
                        pm = pm,
                        context = context,
                        onClick = {
                            if (!currentSelectedFolder.apps.any { it.packageName == appToAdd.packageName }) {
                                currentSelectedFolder.apps.add(appToAdd)
                                Toast.makeText(context, "'${appToAdd.loadLabel(pm)}' added to '${currentSelectedFolder.name}'", Toast.LENGTH_SHORT).show()
                                
                                // MODIFIED: For Smart Suggestions - Trigger suggestion logic
                                val potentialSuggestions = findSuggestions(currentSelectedFolder, unassignedApps.filterNot { it.packageName == appToAdd.packageName }) // Pass updated unassigned list
                                if (potentialSuggestions.isNotEmpty()) {
                                    suggestedAppsForDialog = potentialSuggestions
                                    activeFolderForSuggestion = currentSelectedFolder
                                    showSuggestionDialog = true
                                }
                                onFoldersUpdated() // Call after manual add & suggestion check
                            } else {
                                Toast.makeText(context, "'${appToAdd.loadLabel(pm)}' is already in this folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { selectedFolder = null }) {
                Text("Back to All Folders")
            }
        }
    }
}

// ADDED: Extracted FolderCard Composable for better structure
@Composable
fun FolderCard(folder: AppFolder, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Added elevation
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp).height(100.dp) // Increased padding and fixed height
        ) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(if (folder.apps.size == 1) "${folder.apps.size} app" else "${folder.apps.size} apps", style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
fun AppIcon(
    app: ApplicationInfo,
    pm: PackageManager,
    context: android.content.Context,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .padding(6.dp) // Reduced padding
            .width(80.dp)
            .clickable {
                if (onClick != null) {
                    onClick()
                } else {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    } else {
                        Toast.makeText(context, "Cannot launch this app", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.loadIcon(pm).toBitmap().asImageBitmap(),
            contentDescription = app.loadLabel(pm).toString(),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(4.dp)) // Added spacer
        Text(
            text = app.loadLabel(pm).toString(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2, // Allow two lines for app names
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis // Added ellipsis
        )
    }
}
