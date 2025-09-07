package com.example.smartapporganizer
//
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.example.smartapporganizer.categorization.ScoredCategorizer

// Updated Folder model with custom image support
data class AppFolder(
    val name: String,
    val apps: MutableList<ApplicationInfo> = mutableListOf(),
    var color: Color = Color(0xFF2196F3),
    var iconType: FolderIconType = FolderIconType.DEFAULT,
    var customImageBase64: String? = null // Base64 encoded custom image
)

// Enum for different folder icon types
enum class FolderIconType {
    DEFAULT, GAMES, SOCIAL, WORK, MUSIC, VIDEO, SHOPPING, TRAVEL, FOOD, CUSTOM
}

// Data class for image cropping state
data class CropState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f
)

// SharedPreferences Constants
private const val PREFS_NAME = "SmartAppOrganizerPrefs"
private const val KEY_ALL_FOLDER_NAMES = "allFolderNames"
private const val KEY_FOLDERS_PREFIX = "folder_"
private const val KEY_FOLDER_COLOR_PREFIX = "folder_color_"
private const val KEY_FOLDER_ICON_PREFIX = "folder_icon_"
private const val KEY_FOLDER_CUSTOM_IMAGE_PREFIX = "folder_custom_image_"
private const val KEY_INTELLIGENT_POPUP_SHOWN = "intelligent_popup_shown"

// Predefined colors for folders
val folderColors = listOf(
    Color(0xFF2196F3), // Blue
    Color(0xFF4CAF50), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFFF44336), // Red
    Color(0xFF00BCD4), // Cyan
    Color(0xFF795548), // Brown
    Color(0xFF607D8B), // Blue Grey
    Color(0xFFFFEB3B)  // Yellow
)

// Icon options for folders
val folderIcons = mapOf(
    FolderIconType.DEFAULT to Icons.Filled.Folder,
    FolderIconType.GAMES to Icons.Filled.SportsEsports,
    FolderIconType.SOCIAL to Icons.Filled.People,
    FolderIconType.WORK to Icons.Filled.Work,
    FolderIconType.MUSIC to Icons.Filled.MusicNote,
    FolderIconType.VIDEO to Icons.Filled.VideoLibrary,
    FolderIconType.SHOPPING to Icons.Filled.ShoppingCart,
    FolderIconType.TRAVEL to Icons.Filled.Flight,
    FolderIconType.FOOD to Icons.Filled.Restaurant
)

// ADDED: For Smart Suggestions - App Categories and Keywords
val appCategoryKeywords: Map<String, List<String>> = mapOf(
    "Payment" to listOf("pay", "upi", "wallet", "gpay", "phonepe", "paytm", "bhim", "mobikwik", "bill", "finance", "bank", "payment", "send money"),
    "Shopping" to listOf("shop", "buy", "store", "deal", "cart", "amazon", "flipkart", "myntra", "commerce", "market", "order", "delivery"),
    "Social" to listOf("social", "message", "insta", "facebook", "twitter", "whatsapp", "connect", "friend", "network", "messenger", "telegram","tiktok", "linkedin","snap"),
    "Games" to listOf("game", "play", "rpg", "puzzle", "arcade", "gaming", "ludo", "chess"),
    "Travel" to listOf("travel", "trip", "hotel", "flight", "booking", "map", "navigation", "cab", "taxi", "irctc", "makemytrip"),
    "Music" to listOf("music", "song", "audio", "spotify", "wynk", "gaana", "jiosaavn", "player", "sound"),
    "Video" to listOf("video", "movie", "stream", "youtube", "netflix", "hotstar", "prime video", "player", "watch", "tv"),
    "Productivity" to listOf("work", "office", "doc", "note", "task", "drive", "sheet", "slide", "email", "organize", "tool", "utility", "scanner", "writer"),
    "Food" to listOf("food", "order", "delivery", "zomato", "swiggy", "restaurant", "eat", "recipe"),
    "News" to listOf("news", "article", "headline", "breaking", "times", "update")
)

    

// === PERMISSION-BASED CATEGORIZATION SYSTEM START ===

// Permission-based categorization keywords for smart analysis
val permissionBasedKeywords: Map<String, List<String>> = mapOf(
    "Photography" to listOf("photo", "camera", "gallery", "picture", "image", "shot", "lens", "capture", "album"),
    "Social" to listOf("social", "chat", "message", "insta", "facebook", "twitter", "whatsapp", "connect", "friend", "network", "messenger", "telegram", "snap", "tiktok", "linkedin"),
    "Shopping" to listOf("shop", "buy", "store", "deal", "cart", "amazon", "flipkart", "myntra", "commerce", "market", "order", "delivery", "ebay", "aliexpress"),
    "Navigation" to listOf("map", "navigation", "gps", "location", "route", "direction", "travel", "uber", "ola", "lyft"),
    "Communication" to listOf("call", "phone", "contact", "dial", "message", "sms", "voip", "skype", "zoom", "teams"),
    "Music" to listOf("music", "song", "audio", "spotify", "wynk", "gaana", "jiosaavn", "player", "sound", "tune", "melody"),
    "Banking" to listOf("bank", "finance", "pay", "wallet", "upi", "gpay", "phonepe", "paytm", "bhim", "mobikwik", "account", "transaction", "deposit", "check"),
    "AI" to listOf("ai", "artificial", "intelligence", "chatgpt", "gpt", "claude", "bard", "gemini", "midjourney", "dall", "stable", "diffusion", "neural", "machine", "learning")
)

// === PERMISSION-BASED CATEGORIZATION SYSTEM END ===

// Utility functions for image handling
fun bitmapToBase64(bitmap: Bitmap): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

fun base64ToBitmap(base64String: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

fun cropBitmap(bitmap: Bitmap, cropState: CropState, targetSize: Int = 200): Bitmap {
    val scaledBitmap = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * cropState.scale).toInt(),
        (bitmap.height * cropState.scale).toInt(),
        true
    )

    val x = max(0, (-cropState.offsetX).toInt())
    val y = max(0, (-cropState.offsetY).toInt())
    val width = min(targetSize, scaledBitmap.width - x)
    val height = min(targetSize, scaledBitmap.height - y)

    val croppedBitmap = Bitmap.createBitmap(scaledBitmap, x, y, width, height)
    return Bitmap.createScaledBitmap(croppedBitmap, targetSize, targetSize, true)
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    return drawable.toBitmap()
}

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private fun saveFolders(foldersToSave: List<AppFolder>) {
        val editor = sharedPreferences.edit()
        val allFolderNames = foldersToSave.map { it.name }.toSet()
        editor.putStringSet(KEY_ALL_FOLDER_NAMES, allFolderNames)

        foldersToSave.forEach { folder ->
            val appPackageNames = folder.apps.map { it.packageName }.toSet()
            editor.putStringSet("$KEY_FOLDERS_PREFIX${folder.name}", appPackageNames)

            // Save folder customizations
            editor.putLong("$KEY_FOLDER_COLOR_PREFIX${folder.name}", folder.color.value.toLong())
            editor.putString("$KEY_FOLDER_ICON_PREFIX${folder.name}", folder.iconType.name)
            // Save custom image
            editor.putString("$KEY_FOLDER_CUSTOM_IMAGE_PREFIX${folder.name}", folder.customImageBase64)
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

            // Load folder customizations
            val savedColor = sharedPreferences.getLong("$KEY_FOLDER_COLOR_PREFIX$folderName", Color(0xFF2196F3).value.toLong())
            val savedIconType = try {
                FolderIconType.valueOf(sharedPreferences.getString("$KEY_FOLDER_ICON_PREFIX$folderName", "DEFAULT") ?: "DEFAULT")
            } catch (e: Exception) {
                FolderIconType.DEFAULT
            }
            val savedCustomImage = sharedPreferences.getString("$KEY_FOLDER_CUSTOM_IMAGE_PREFIX$folderName", null)

            val folder = AppFolder(folderName, appsInFolder, Color(savedColor.toULong()), savedIconType, savedCustomImage)
            loadedFolders.add(folder)
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
        // Use ScoredCategorizer for intelligent suggestions
        return ScoredCategorizer.getScoredSuggestions(folder, unassignedAppsList, pm)
    }


    // === SMART PERMISSION-BASED APP CATEGORIZATION START ===

    /**
     * Smart permission-based app categorization system
     * Uses multiple signals: permissions + app name keywords + exclusion rules
     *
     * @param app ApplicationInfo of the app to categorize
     * @param pm PackageManager to access app permissions
     * @return Most likely category as String, or "Other" if no match
     */
    fun categorizeAppByPermissions(app: ApplicationInfo, pm: PackageManager): String {
        try {
            val appName = app.loadLabel(pm).toString().lowercase()
            val permissions = getAppPermissions(app, pm)

            // === PERMISSION COMBINATION LOGIC START ===
            val hasCamera = permissions.any { it.contains("CAMERA") }
            val hasLocation = permissions.any { it.contains("LOCATION") || it.contains("GPS") }
            val hasContacts = permissions.any { it.contains("CONTACTS") || it.contains("READ_CONTACTS") }
            val hasPhone = permissions.any { it.contains("PHONE") || it.contains("CALL") }
            val hasMicrophone = permissions.any { it.contains("RECORD_AUDIO") || it.contains("MICROPHONE") }
            val hasStorage = permissions.any { it.contains("STORAGE") || it.contains("READ_EXTERNAL_STORAGE") }
            val hasInternet = permissions.any { it.contains("INTERNET") }
            val hasSms = permissions.any { it.contains("SMS") || it.contains("SEND_SMS") }
            // === PERMISSION COMBINATION LOGIC END ===

            // === KEYWORD MATCHING AGAINST APP NAMES START ===
            fun hasKeywords(category: String): Boolean {
                val keywords = permissionBasedKeywords[category] ?: return false
                return keywords.any { keyword -> appName.contains(keyword) }
            }
            // === KEYWORD MATCHING AGAINST APP NAMES END ===

            // === EXCLUSION RULES FOR FALSE POSITIVES START ===
            val isSocialApp = hasKeywords("Social")
            val isShoppingApp = hasKeywords("Shopping")
            val isBankingApp = hasKeywords("Banking")
            val isAIApp = hasKeywords("AI")
            val isNavigationApp = hasKeywords("Navigation")
            val isCommunicationApp = hasKeywords("Communication")
            val isMusicApp = hasKeywords("Music")
            val isPhotographyApp = hasKeywords("Photography")

            // Exclusion: If app has social keywords, it's probably not photography despite camera permission
            val excludePhotography = isSocialApp || isShoppingApp || isBankingApp
            // === EXCLUSION RULES FOR FALSE POSITIVES END ===

            // === PHOTOGRAPHY CATEGORY DETECTION ===
            if (hasCamera && hasStorage && !excludePhotography && !hasHeavyInternetUsage(permissions)) {
                return "Photography"
            }

            // === SOCIAL MEDIA CATEGORY DETECTION ===
            if (isSocialApp || (hasCamera && hasContacts && hasInternet)) {
                return "Social"
            }

            // === SHOPPING CATEGORY DETECTION ===
            if (isShoppingApp || (hasCamera && hasInternet && appName.contains(Regex("(shop|buy|store|market|commerce)")))) {
                return "Shopping"
            }

            // === NAVIGATION CATEGORY DETECTION ===
            if (isNavigationApp || (hasLocation && hasInternet && !hasCamera)) {
                return "Navigation"
            }

            // === COMMUNICATION CATEGORY DETECTION ===
            if (isCommunicationApp || (hasContacts && (hasPhone || hasSms))) {
                return "Communication"
            }

            // === MUSIC CATEGORY DETECTION ===
            if (isMusicApp || (hasMicrophone && !hasCamera && appName.contains(Regex("(music|song|audio|player|sound)")))) {
                return "Music"
            }

            // === BANKING CATEGORY DETECTION START ===
            if (isBankingApp || (hasCamera && appName.contains(Regex("(bank|pay|wallet|finance|account|deposit|check)")))) {
                return "Banking"
            }
            // === BANKING CATEGORY DETECTION END ===

            // === AI CATEGORY DETECTION START ===
            if (isAIApp || (hasInternet && appName.contains(Regex("(ai|artificial|intelligence|chatgpt|gpt|claude|bard|gemini|midjourney|dall|stable|diffusion|neural|machine|learning)")))) {
                return "AI"
            }
            // === AI CATEGORY DETECTION END ===

            // Fallback to existing simple categorization
            for ((category, keywords) in appCategoryKeywords) {
                if (keywords.any { keyword -> appName.contains(keyword) }) {
                    return category
                }
            }

            return "Other"

        } catch (e: Exception) {
            // Fallback for any errors
            return "Other"
        }
    }

    /**
     * Helper function to get app permissions
     */
    private fun getAppPermissions(app: ApplicationInfo, pm: PackageManager): List<String> {
        return try {
            val packageInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Helper function to detect heavy internet usage patterns
     */
    private fun hasHeavyInternetUsage(permissions: List<String>): Boolean {
        // Apps with these permissions likely have heavy internet usage
        val heavyInternetIndicators = listOf(
            "ACCESS_WIFI_STATE",
            "CHANGE_WIFI_STATE",
            "ACCESS_NETWORK_STATE",
            "CHANGE_NETWORK_STATE",
            "WAKE_LOCK" // Often used by apps that need constant connectivity
        )

        return heavyInternetIndicators.count { indicator ->
            permissions.any { it.contains(indicator) }
        } >= 2 // If 2+ heavy internet indicators, consider it heavy usage
    }

    // === SMART PERMISSION-BASED APP CATEGORIZATION END ===

    // === DEMONSTRATION FUNCTION START ===
    /**
     * Demonstration function showing how the smart categorization works
     * This shows how Instagram would be correctly classified as "Social"
     * despite having camera permissions
     */
    fun demonstrateSmartCategorization(pm: PackageManager) {
        // Example: Instagram with camera permissions but social keywords
        val mockInstagramApp = object : ApplicationInfo() {
            override fun loadLabel(pm: PackageManager): CharSequence {
                return "Instagram"
            }
        }.apply {
            packageName = "com.instagram.android"
        }

        // Mock Instagram permissions (camera + internet + storage + contacts)
        val mockInstagramPermissions = listOf(
            "android.permission.CAMERA",
            "android.permission.INTERNET",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_CONTACTS",
            "android.permission.ACCESS_NETWORK_STATE"
        )

        // Test the categorization
        val category = categorizeAppByPermissions(mockInstagramApp, pm)
        println("Instagram would be categorized as: $category")
        // Expected output: "Social" (not "Photography" despite camera permission)

        // Example: Camera app with photography keywords
        val mockCameraApp = object : ApplicationInfo() {
            override fun loadLabel(pm: PackageManager): CharSequence {
                return "Pro Camera"
            }
        }.apply {
            packageName = "com.camera.pro"
        }

        val category2 = categorizeAppByPermissions(mockCameraApp, pm)
        println("Pro Camera would be categorized as: $category2")
        // Expected output: "Photography"
    }
    // === DEMONSTRATION FUNCTION END ===
    

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
                        findSuggestions = { folder, currentUnassignedApps ->
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
    findSuggestions: (AppFolder, List<ApplicationInfo>) -> List<ApplicationInfo>
) {
    var selectedFolder by remember { mutableStateOf<AppFolder?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Smart Suggestions state
    var showSuggestionDialog by remember { mutableStateOf(false) }
    var suggestedAppsForDialog by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var activeFolderForSuggestion by remember { mutableStateOf<AppFolder?>(null) }


    // Educational Popup state
    var showEducationalPopup by remember { mutableStateOf(false) }
    var educationalPopupShown by remember {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INTELLIGENT_POPUP_SHOWN, false))
    }

    // Smart Suggestions Dialog
    if (showSuggestionDialog && activeFolderForSuggestion != null && suggestedAppsForDialog.isNotEmpty()) {
        val currentActiveFolder = activeFolderForSuggestion!!
        AlertDialog(
            onDismissRequest = { showSuggestionDialog = false },
            title = { Text("Smart Suggestions") },
            text = {
                Column {
                    Text("Add these related apps to '${currentActiveFolder.name}'?")
                    Spacer(Modifier.height(8.dp))
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
                        onFoldersUpdated()
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


    // Educational Popup for Intelligent Folder Suggestions
    if (showEducationalPopup) {
        AlertDialog(
            onDismissRequest = { showEducationalPopup = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFFFA000), // Amber color
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Discover Smart Organization!")
                }
            },
            text = {
                Column {
                    Text(
                        "Find similar apps automatically using Intelligent Folder Suggestions in the menu above.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "💡 Tip: Add a few apps to a folder and let the app suggest related ones for you!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showEducationalPopup = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50) // Green color
                    )
                ) {
                    Text("Got it!")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEducationalPopup = false }) {
                    Text("Skip")
                }
            }
        )
    }

    // Create Folder Dialog
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
            // Main folder view
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(folders) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { selectedFolder = folder }
                    )
                }
                items(unassignedApps) { app ->
                    AppIcon(app, pm, context, onClick = null)
                }
            }
        } else {
            // Folder detail view with kebab menu inside
            FolderDetailView(
                folder = selectedFolder!!,
                folders = folders,
                unassignedApps = unassignedApps,
                pm = pm,
                onBack = { selectedFolder = null },
                onFoldersUpdated = onFoldersUpdated,
                findSuggestions = findSuggestions,
                onShowSuggestions = { folder, suggestions ->
                    activeFolderForSuggestion = folder
                    suggestedAppsForDialog = suggestions
                    showSuggestionDialog = true
                },
                educationalPopupShown = educationalPopupShown,
                onEducationalPopupTriggered = {
                    showEducationalPopup = true
                    educationalPopupShown = true
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_INTELLIGENT_POPUP_SHOWN, true).apply()
                }
            )
        }
    }
}

@Composable
fun FolderDetailView(
    folder: AppFolder,
    folders: MutableList<AppFolder>,
    unassignedApps: List<ApplicationInfo>,
    pm: PackageManager,
    onBack: () -> Unit,
    onFoldersUpdated: () -> Unit,
    findSuggestions: (AppFolder, List<ApplicationInfo>) -> List<ApplicationInfo>,
    onShowSuggestions: (AppFolder, List<ApplicationInfo>) -> Unit,
    educationalPopupShown: Boolean,
    onEducationalPopupTriggered: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var showDropdownMenu by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filteredUnassignedApps = unassignedApps.filter {
        it.loadLabel(pm).toString().contains(searchText, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with back button and kebab menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Folder: ${folder.name}",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Kebab menu - moved inside folder view
            Box {
                IconButton(onClick = { showDropdownMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                }

                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Color Palette")
                            }
                        },
                        onClick = {
                            showDropdownMenu = false
                            showCustomizeDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Set Custom Image")
                            }
                        },
                        onClick = {
                            showDropdownMenu = false
                            showCustomizeDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Intelligent Folder Suggestions")
                            }
                        },
                        onClick = {
                            showDropdownMenu = false
                            // Trigger smart suggestions for current folder
                            val suggestions = findSuggestions(folder, unassignedApps)
                            if (suggestions.isNotEmpty()) {
                                onShowSuggestions(folder, suggestions)
                            } else {
                                Toast.makeText(context, "No suggestions available for this folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Apps in folder
        Text("Apps in folder (tap to remove):", style = MaterialTheme.typography.titleSmall)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier.heightIn(min = 100.dp, max = 200.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(folder.apps.toList()) { appInFolder ->
                AppIcon(
                    app = appInFolder,
                    pm = pm,
                    context = context,
                    onClick = {
                        folder.apps.remove(appInFolder)
                        onFoldersUpdated()
                        Toast.makeText(context, "'${appInFolder.loadLabel(pm)}' removed from '${folder.name}'", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Search and add apps
        TextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Search unassigned apps to add") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text("Tap app to add to '${folder.name}':", style = MaterialTheme.typography.titleSmall)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(filteredUnassignedApps) { appToAdd ->
                AppIcon(
                    app = appToAdd,
                    pm = pm,
                    context = context,
                    onClick = {
                        if (!folder.apps.any { it.packageName == appToAdd.packageName }) {
                            folder.apps.add(appToAdd)
                            Toast.makeText(context, "'${appToAdd.loadLabel(pm)}' added to '${folder.name}'", Toast.LENGTH_SHORT).show()

                            // Check if this is the second app added and popup hasn't been shown yet
                            if (folder.apps.size == 2 && !educationalPopupShown) {
                                onEducationalPopupTriggered()
                            }

                            val potentialSuggestions = findSuggestions(folder, unassignedApps.filterNot { it.packageName == appToAdd.packageName })
                            if (potentialSuggestions.isNotEmpty()) {
                                onShowSuggestions(folder, potentialSuggestions)
                            }
                            onFoldersUpdated()
                        } else {
                            Toast.makeText(context, "'${appToAdd.loadLabel(pm)}' is already in this folder", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = onBack) {
            Text("Back to All Folders")
        }
    }

    // Folder Customization Dialog
    if (showCustomizeDialog) {
        FolderCustomizationDialog(
            folder = folder,
            onDismiss = { showCustomizeDialog = false },
            onSave = { updatedFolder ->
                val index = folders.indexOf(folder)
                if (index != -1) {
                    // Update the folder in the list
                    folder.color = updatedFolder.color
                    folder.iconType = updatedFolder.iconType
                    folder.customImageBase64 = updatedFolder.customImageBase64
                    onFoldersUpdated()
                    Toast.makeText(context, "Folder '${updatedFolder.name}' customized", Toast.LENGTH_SHORT).show()
                }
                showCustomizeDialog = false
            }
        )
    }
}

@Composable
fun FolderCard(
    folder: AppFolder,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(16.dp)
                .height(100.dp)
                .fillMaxWidth()
        ) {
            // Display custom image or icon
            if (folder.customImageBase64 != null) {
                val bitmap = remember(folder.customImageBase64) {
                    base64ToBitmap(folder.customImageBase64!!)
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Custom folder icon",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Icon(
                    imageVector = folderIcons[folder.iconType] ?: Icons.Filled.Folder,
                    contentDescription = "Folder",
                    modifier = Modifier.size(40.dp),
                    tint = folder.color
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                folder.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                if (folder.apps.size == 1) "${folder.apps.size} app" else "${folder.apps.size} apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun FolderCustomizationDialog(
    folder: AppFolder,
    onDismiss: () -> Unit,
    onSave: (AppFolder) -> Unit
) {
    var selectedColor by remember { mutableStateOf(folder.color) }
    var selectedIconType by remember { mutableStateOf(folder.iconType) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageCropper by remember { mutableStateOf(false) }
    var croppedImageBase64 by remember { mutableStateOf(folder.customImageBase64) }
    var currentTab by remember { mutableStateOf(0) } // 0 = Colors, 1 = Icons, 2 = Custom Image

    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            showImageCropper = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Customize '${folder.name}'",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tab Row
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("Colors") }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("Icons") }
                    )
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        text = { Text("Custom Image") }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Tab Content
                when (currentTab) {
                    0 -> {
                        // Color Selection
                        Text(
                            text = "Choose Folder Color",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.height(120.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(folderColors) { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColor == color) 3.dp else 1.dp,
                                            color = if (selectedColor == color) Color.White else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = color },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColor == color) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Icon Selection
                        Text(
                            text = "Choose Folder Icon",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.height(200.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(folderIcons.entries.toList()) { (iconType, icon) ->
                                Card(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clickable {
                                            selectedIconType = iconType
                                            croppedImageBase64 = null // Clear custom image when selecting predefined icon
                                        },
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (selectedIconType == iconType && croppedImageBase64 == null) 6.dp else 2.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedIconType == iconType && croppedImageBase64 == null)
                                            selectedColor.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = iconType.name,
                                            modifier = Modifier.size(28.dp),
                                            tint = selectedColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // Custom Image Section
                        Text(
                            text = "Custom Folder Image",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Current custom image preview
                        if (croppedImageBase64 != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current Image:")
                                val bitmap = remember(croppedImageBase64) {
                                    base64ToBitmap(croppedImageBase64!!)
                                }
                                bitmap?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "Current custom folder image",
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Upload buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload New Image")
                            }

                            if (croppedImageBase64 != null) {
                                OutlinedButton(
                                    onClick = { croppedImageBase64 = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Remove Custom Image")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Note: Custom images will override icon selection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                folder.copy(
                                    color = selectedColor,
                                    iconType = selectedIconType,
                                    customImageBase64 = croppedImageBase64
                                )
                            )
                        }
                    ) {
                        Text("Apply Changes")
                    }
                }
            }
        }
    }

    // Image Cropper Dialog
    if (showImageCropper && selectedImageUri != null) {
        ImageCropperDialog(
            imageUri = selectedImageUri!!,
            onDismiss = {
                showImageCropper = false
                selectedImageUri = null
            },
            onImageCropped = { base64Image ->
                croppedImageBase64 = base64Image
                showImageCropper = false
                selectedImageUri = null
            }
        )
    }
}

@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onImageCropped: (String) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropState by remember { mutableStateOf(CropState()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load bitmap from URI
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // Resize if too large to prevent memory issues
                val maxSize = 1024
                val resizedBitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                    val ratio = min(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (originalBitmap.width * ratio).toInt(),
                        (originalBitmap.height * ratio).toInt(),
                        true
                    )
                } else {
                    originalBitmap
                }

                withContext(Dispatchers.Main) {
                    bitmap = resizedBitmap
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Crop & Resize Image",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    bitmap?.let { bmp ->
                        // Image cropper area
                        Box(
                            modifier = Modifier
                                .size(300.dp)
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            // Display the image
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Image to crop",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = cropState.scale,
                                        scaleY = cropState.scale,
                                        translationX = cropState.offsetX,
                                        translationY = cropState.offsetY
                                    )
                                    .pointerInput(Unit) {
                                        detectDragGestures { _, dragAmount ->
                                            cropState = cropState.copy(
                                                offsetX = (cropState.offsetX + dragAmount.x).coerceIn(-size.width.toFloat(), size.width.toFloat()),
                                                offsetY = (cropState.offsetY + dragAmount.y).coerceIn(-size.height.toFloat(), size.height.toFloat())
                                            )
                                        }
                                    },
                                contentScale = ContentScale.Fit
                            )

                            // Crop overlay
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cropSizePx = 200.dp.toPx() // Fixed crop size
                                val centerX = size.width / 2
                                val centerY = size.height / 2
                                val cropLeft = centerX - cropSizePx / 2
                                val cropTop = centerY - cropSizePx / 2

                                // Draw semi-transparent overlay over entire area
                                drawRect(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                    size = size
                                )

                                // Clear the crop area (make it transparent)
                                drawRect(
                                    color = androidx.compose.ui.graphics.Color.Transparent,
                                    topLeft = Offset(cropLeft, cropTop),
                                    size = Size(cropSizePx, cropSizePx),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                                )

                                // Draw crop border
                                drawRect(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    topLeft = Offset(cropLeft - 1, cropTop - 1),
                                    size = Size(cropSizePx + 2, cropSizePx + 2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                )

                                // Draw corner indicators
                                val cornerSize = 20.dp.toPx()
                                val corners = listOf(
                                    Offset(cropLeft, cropTop),
                                    Offset(cropLeft + cropSizePx - cornerSize, cropTop),
                                    Offset(cropLeft, cropTop + cropSizePx - cornerSize),
                                    Offset(cropLeft + cropSizePx - cornerSize, cropTop + cropSizePx - cornerSize)
                                )

                                corners.forEach { corner ->
                                    drawRect(
                                        color = androidx.compose.ui.graphics.Color.White,
                                        topLeft = corner,
                                        size = Size(cornerSize, cornerSize),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Scale controls
                        Column {
                            Text("Zoom Level: ${(cropState.scale * 100).roundToInt()}%")
                            Slider(
                                value = cropState.scale,
                                onValueChange = { scale ->
                                    cropState = cropState.copy(scale = scale.coerceIn(0.1f, 3f))
                                },
                                valueRange = 0.1f..3f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Reset button
                        OutlinedButton(
                            onClick = { cropState = CropState() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Position & Zoom")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            bitmap?.let { bmp ->
                                val croppedBitmap = cropAndResizeBitmap(bmp, cropState)
                                val base64 = bitmapToBase64(croppedBitmap)
                                onImageCropped(base64)
                            }
                        },
                        enabled = !isLoading && bitmap != null
                    ) {
                        Text("Apply Crop")
                    }
                }
            }
        }
    }


}

// Helper function to crop and resize bitmap
fun cropAndResizeBitmap(bitmap: Bitmap, cropState: CropState, targetSize: Int = 200): Bitmap {
    val sourceWidth = bitmap.width.toFloat()
    val sourceHeight = bitmap.height.toFloat()

    // Calculate the scaled dimensions
    val scaledWidth = sourceWidth * cropState.scale
    val scaledHeight = sourceHeight * cropState.scale

    // Calculate crop area in bitmap coordinates
    val cropX = max(0f, -cropState.offsetX / cropState.scale)
    val cropY = max(0f, -cropState.offsetY / cropState.scale)
    val cropWidth = min(sourceWidth - cropX, targetSize / cropState.scale)
    val cropHeight = min(sourceHeight - cropY, targetSize / cropState.scale)

    // Create cropped bitmap
    val croppedBitmap = Bitmap.createBitmap(
        bitmap,
        cropX.toInt(),
        cropY.toInt(),
        cropWidth.toInt(),
        cropHeight.toInt()
    )

    // Scale to target size
    return Bitmap.createScaledBitmap(croppedBitmap, targetSize, targetSize, true)
}

@Composable
fun SmartAppOrganizerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
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
            .padding(6.dp)
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.loadLabel(pm).toString(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}// trigger CodeRabbit
