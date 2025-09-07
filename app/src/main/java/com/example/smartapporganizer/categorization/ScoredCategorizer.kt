package com.example.smartapporganizer.categorization

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log  // ← ADD THIS IMPORT
import com.example.smartapporganizer.AppFolder
import com.example.smartapporganizer.appCategoryKeywords

/**
 * Intelligent scoring-based app categorization system
 * 
 * This class provides advanced keyword scoring for app categorization:
 * - Short keywords (1-3 chars) = 1 point
 * - Long keywords (4+ chars) = 3 points
 * - Minimum score of 3 required for suggestions
 * - Returns top 5 suggestions sorted by highest score
 */ 
class ScoredCategorizer {
    
    companion object {
        
        /**
         * Calculate category score for an app based on keyword matching
         * 
         * @param appName The name of the app to score
         * @param category The category to check against
         * @return Score based on keyword matches (0 if no matches)
         */
        fun getAppCategoryScore(appName: String, category: String): Int {
            val keywords = appCategoryKeywords[category] ?: return 0
            var score = 0
            val appNameLower = appName.lowercase()
            
            // Don't suggest AI apps for social folders specifically
            if (category == "Social" && (appName.contains("gpt", ignoreCase = true) || appName.contains("ai", ignoreCase = true) || appName.contains("claude", ignoreCase = true) || appName.contains("bard", ignoreCase = true))) {
                return 0 // Override social score for AI apps
            }

            keywords.forEach { keyword ->
                if (appNameLower.contains(keyword.lowercase())) {
                    score += when (keyword.length) {
                        1, 2, 3 -> 1  // Short keywords get lower score
                        else -> 3     // Longer keywords more reliable
                    }
                }
            }
            return score
        }
        
        /**
         * Find the best category for an app based on highest score
         * 
         * @param appName The name of the app to categorize
         * @return The category with highest score, or null if no category scores >= 3
         */
        fun getBestCategoryForApp(appName: String): String? {
            var bestCategory: String? = null
            var highestScore = 2 // Minimum score threshold is 3, so start below it
            
            appCategoryKeywords.keys.forEach { category ->
                val score = getAppCategoryScore(appName, category)
                if (score > highestScore) {
                    highestScore = score
                    bestCategory = category
                }
            }
            
            return bestCategory
        }
        
        /**
         * Get scored app suggestions for a folder based on intelligent analysis
         * 
         * @param folder The folder to find suggestions for
         * @param unassignedApps List of apps not yet assigned to folders
         * @param pm PackageManager for accessing app information
         * @return List of up to 5 suggested apps sorted by relevance score
         */
        fun getScoredSuggestions(
            folder: AppFolder, 
            unassignedApps: List<ApplicationInfo>, 
            pm: PackageManager
        ): List<ApplicationInfo> {
            
            // ← ADD LOGGING HERE
            Log.d("ScoredCategorizer", "🔍 Finding suggestions for folder: ${folder.name}")
            
            // Step 1: Identify target category from folder name
            val folderNameLower = folder.name.lowercase()
            var targetCategory: String? = null
            var targetCategoryScore = 0
            
            // Check folder name against all categories
            appCategoryKeywords.forEach { (category, keywords) ->
                val score = getAppCategoryScore(folder.name, category)
                if (score > targetCategoryScore) {
                    targetCategoryScore = score
                    targetCategory = category
                }
            }
            
            // Step 2: If no category from folder name, analyze existing apps in folder
            if (targetCategory == null && folder.apps.isNotEmpty()) {
                val categoryScores = mutableMapOf<String, Int>()
                
                // Analyze last 3 apps in folder (most recent additions)
                val appsToAnalyze = folder.apps.takeLast(3)
                
                appsToAnalyze.forEach { appInFolder ->
                    val appName = appInFolder.loadLabel(pm).toString()
                    val bestCategory = getBestCategoryForApp(appName)
                    
                    if (bestCategory != null) {
                        val score = getAppCategoryScore(appName, bestCategory)
                        categoryScores[bestCategory] = categoryScores.getOrDefault(bestCategory, 0) + score
                    }
                }
                
                // Find category with highest cumulative score
                targetCategory = categoryScores.maxByOrNull { it.value }?.key
            }
            
            // ← ADD LOGGING HERE
            Log.d("ScoredCategorizer", "📊 Target category identified: $targetCategory")
            
            // Step 3: If still no target category, return empty list
            if (targetCategory == null) {
                Log.d("ScoredCategorizer", "❌ No target category found, returning empty suggestions")
                return emptyList()
            }
            
            // Step 4: Score all unassigned apps against target category
            val scoredApps = mutableListOf<Pair<ApplicationInfo, Int>>()
            
            unassignedApps.forEach { app ->
                val appName = app.loadLabel(pm).toString()
                val score = getAppCategoryScore(appName, targetCategory!!)
                
                // Only include apps with minimum score of 3
                if (score >= 3) {
                    // Avoid duplicates - check if app is already in folder
                    if (!folder.apps.any { it.packageName == app.packageName }) {
                        scoredApps.add(Pair(app, score))
                    }
                }
            }
            
            // ← ADD LOGGING HERE - Before returning the results
            Log.d("ScoredCategorizer", "📊 ScoredCategorizer found ${scoredApps.size} suggestions:")
            scoredApps.sortedByDescending { it.second }.take(5).forEach { (app, score) ->
                Log.d("ScoredCategorizer", "   - ${app.loadLabel(pm)} (score: $score)")
            }
            
            // Step 5: Sort by score (highest first) and return top 5
            return scoredApps
                .sortedByDescending { it.second }
                .take(5)
                .map { it.first }
        }
        
        // ... rest of your existing methods remain unchanged
    }
}