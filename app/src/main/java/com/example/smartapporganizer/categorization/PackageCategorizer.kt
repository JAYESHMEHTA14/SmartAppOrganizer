/* package com.example.smartapporganizer.categorization

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.smartapporganizer.AppFolder

/**
 * Package name-based app categorization system
 *
 * This class analyzes app package names to suggest related apps for folders.
 * It complements # by focusing on package patterns rather than display names.
 *
 * Key features:
 * - Analyzes package name patterns for categorization
 * - Detects company groupings (Google, Microsoft, Adobe, etc.)
 * - Provides confidence scores for suggestions
 * - Catches apps that keyword analysis might miss
 */
class PackageCategorizer {

    /*
    companion object {

        // Package pattern mappings for different categories
        private val packagePatterns: Map<String, List<String>> = mapOf(
            "Banking" to listOf(
                "bank", "axis", "hdfc", "icici", "sbi", "kotak", "pnb", "canara", "union", "bob",
                "indusind", "idbi", "federal", "rbl", "yesbank", "bandhan", "idfc", "jupiter",
                "fintech", "wallet", "paytm", "phonepe", "gpay", "bhim", "upi", "mobikwik"
            ),

            "Social" to listOf(
                "whatsapp", "facebook", "instagram", "twitter", "telegram", "snapchat",
                "linkedin", "tiktok", "discord", "skype", "zoom", "pinterest", "reddit",
                "sharechat", "josh", "moj", "hike", "jiochat", "imo", "botim", "yalla"
            ),

            "Shopping" to listOf(
                "amazon", "flipkart", "myntra", "ajio", "meesho", "bigbasket", "grofers",
                "swiggy", "zomato", "dominos", "ubereats", "doordash", "grubhub", "justeat",
                "aliexpress", "ebay", "shein", "jumia", "takealot", "mercadolibre"
            ),

            "Video" to listOf(
                "youtube", "netflix", "hotstar", "primevideo", "disney", "hulu",
                "iqiyi", "tencent", "vimeo", "dailymotion", "twitch"
            ),

            "Music" to listOf(
                "spotify", "gaana", "wynk", "jiosaavn", "soundcloud", "deezer",
                "applemusic", "pandora", "tidal", "amazonmusic"
            ),

            "Productivity" to listOf(
                "google", "microsoft", "adobe", "evernote", "notion", "trello",
                "asana", "monday", "slack", "teams", "zoom", "dropbox", "onedrive"
            ),

            "Games" to listOf(
                "game", "gaming", "play", "candycrush", "clash", "pubg", "freefire",
                "amongus", "minecraft", "fortnite", "roblox", "cod", "genshin"
            ),

            "Travel" to listOf(
                "makemytrip", "goibibo", "cleartrip", "yatra", "irctc", "uber", "ola",
                "lyft", "airbnb", "booking", "expedia", "tripadvisor"
            ),

            "News" to listOf(
                "cnn", "bbc", "nytimes", "guardian", "wsj", "times", "hindu",
                "indianexpress", "news", "media", "press"
            )
        )

        // Company-specific package prefixes
        private val companyPrefixes: Map<String, String> = mapOf(
            "com.google" to "Google Apps",
            "com.microsoft" to "Microsoft",
            "com.adobe" to "Adobe",
            "com.facebook" to "Facebook",
            "com.whatsapp" to "WhatsApp",
            "com.instagram" to "Instagram",
            "com.twitter" to "Twitter",
            "com.snapchat" to "Snapchat",
            "com.linkedin" to "LinkedIn",
            "com.amazon" to "Amazon",
            "com.netflix" to "Netflix",
            "com.spotify" to "Spotify",
            "com.paypal" to "PayPal",
            "com.paytm" to "Paytm",
            "com.phonepe" to "PhonePe",
            "com.axisbank" to "Axis Bank",
            "com.hdfcbank" to "HDFC Bank",
            "com.icicibank" to "ICICI Bank",
            "com.sbi" to "SBI",
            "com.kotak" to "Kotak Mahindra Bank"
        )

        /**
         * Analyze a package name to determine its category
         *
         * @param packageName The package name to analyze
         * @return Category name or null if no match found
         */
        fun analyzePackageName(packageName: String): String? {
            val packageLower = packageName.lowercase()

            // First, check for exact company prefix matches
            for ((prefix, category) in companyPrefixes) {
                if (packageLower.startsWith(prefix.lowercase())) {
                    return category
                }
            }

            // Then check for pattern matches in package name
            for ((category, patterns) in packagePatterns) {
                for (pattern in patterns) {
                    if (packageLower.contains(pattern.lowercase())) {
                        return category
                    }
                }
            }

            return null
        }

        /**
         * Get package-based app suggestions for a folder
         *
         * @param folder The folder to find suggestions for
         * @param unassignedApps List of apps not yet assigned to folders
         * @param pm PackageManager for accessing app information
         * @return List of suggested apps with confidence scores
         */
        fun getPackageSuggestions(
            folder: AppFolder,
            unassignedApps: List<ApplicationInfo>,
            pm: PackageManager
        ): List<ApplicationInfo> {
            val suggestions = mutableListOf<Pair<ApplicationInfo, Int>>()

            // Determine target category from folder
            val targetCategory = determineTargetCategory(folder, pm) ?: return emptyList()

            // Analyze each unassigned app
            unassignedApps.forEach { app ->
                val confidence = getConfidenceScore(app.packageName, targetCategory)

                // Only include apps with confidence >= 3
                if (confidence >= 3) {
                    // Avoid duplicates
                    if (!folder.apps.any { it.packageName == app.packageName }) {
                        suggestions.add(Pair(app, confidence))
                    }
                }
                */
            }

            // Sort by confidence (highest first) and return top 5
            return suggestions
                .sortedByDescending { it.second }
                .take(5)
                .map { it.first }
        }

        /**
         * Get confidence score for a package name against a target category
         *
         * @param packageName The package name to score
         * @param targetCategory The category to check against
         * @return Confidence score (0-10 scale)
         */
        fun getConfidenceScore(packageName: String, targetCategory: String): Int {
            val packageLower = packageName.lowercase()
            var confidence = 0

            // Check for exact company prefix match (highest confidence)
            for ((prefix, category) in companyPrefixes) {
                if (packageLower.startsWith(prefix.lowercase()) && category == targetCategory) {
                    confidence += 8 // High confidence for exact company match
                    break
                }
            }

            // Check for pattern matches
            val patterns = packagePatterns[targetCategory] ?: return confidence

            for (pattern in patterns) {
                if (packageLower.contains(pattern.lowercase())) {
                    confidence += when (pattern.length) {
                        1, 2, 3 -> 2  // Short patterns get lower confidence
                        4, 5, 6 -> 3  // Medium patterns
                        else -> 4     // Long patterns get higher confidence
                    }
                }
            }

            // Cap at 10 and return
            return minOf(confidence, 10)
        }

        /**
         * Determine the target category for a folder based on its contents
         *
         * @param folder The folder to analyze
         * @param pm PackageManager for accessing app information
         * @return Target category or null if undetermined
         */
        private fun determineTargetCategory(folder: AppFolder, pm: PackageManager): String? {
            if (folder.apps.isEmpty()) {
                // Try to infer from folder name
                return inferCategoryFromFolderName(folder.name)
            }

            // Analyze existing apps in folder
            val categoryCounts = mutableMapOf<String, Int>()

            // Analyze last 3 apps for better accuracy
            val appsToAnalyze = folder.apps.takeLast(3)

            appsToAnalyze.forEach { app ->
                val category = analyzePackageName(app.packageName)
                if (category != null) {
                    categoryCounts[category] = categoryCounts.getOrDefault(category, 0) + 1
                }
            }

            // Return category with highest count
            return categoryCounts.maxByOrNull { it.value }?.key
        }

        /**
         * Infer category from folder name
         *
         * @param folderName The name of the folder
         * @return Inferred category or null
         */
        private fun inferCategoryFromFolderName(folderName: String): String? {
            val nameLower = folderName.lowercase()

            return when {
                nameLower.contains("bank") || nameLower.contains("finance") || nameLower.contains("pay") -> "Banking"
                nameLower.contains("social") || nameLower.contains("chat") || nameLower.contains("message") -> "Social"
                nameLower.contains("shop") || nameLower.contains("buy") || nameLower.contains("store") -> "Shopping"
                nameLower.contains("video") || nameLower.contains("movie") || nameLower.contains("watch") -> "Video"
                nameLower.contains("music") || nameLower.contains("song") || nameLower.contains("audio") -> "Music"
                nameLower.contains("work") || nameLower.contains("office") || nameLower.contains("tool") -> "Productivity"
                nameLower.contains("game") || nameLower.contains("play") -> "Games"
                nameLower.contains("travel") || nameLower.contains("trip") -> "Travel"
                nameLower.contains("news") || nameLower.contains("article") -> "News"
                else -> null
            }
        }

        /**
         * Get detailed analysis of a package name
         *
         * @param packageName The package name to analyze
         * @return Map of categories to confidence scores
         */
        fun getPackageAnalysis(packageName: String): Map<String, Int> {
            val analysis = mutableMapOf<String, Int>()

            packagePatterns.keys.forEach { category ->
                val confidence = getConfidenceScore(packageName, category)
                if (confidence > 0) {
                    analysis[category] = confidence
                }
            }

            return analysis
        }

        /**
         * Check if a package belongs to a specific company
         *
         * @param packageName The package name to check
         * @return Company name or null if not recognized
         */
        fun getCompanyFromPackage(packageName: String): String? {
            val packageLower = packageName.lowercase()

            for ((prefix, company) in companyPrefixes) {
                if (packageLower.startsWith(prefix.lowercase())) {
                    return company
                }
            }

            return null
        }

        /**
         * Get all apps from the same company as a given app
         *
         * @param referenceApp The reference app
         * @param allApps List of all available apps
         * @return List of apps from the same company
         */
        fun getCompanyApps(referenceApp: ApplicationInfo, allApps: List<ApplicationInfo>): List<ApplicationInfo> {
            val company = getCompanyFromPackage(referenceApp.packageName) ?: return emptyList()

            return allApps.filter { app ->
                getCompanyFromPackage(app.packageName) == company && app.packageName != referenceApp.packageName
            }
        }
    }
} */