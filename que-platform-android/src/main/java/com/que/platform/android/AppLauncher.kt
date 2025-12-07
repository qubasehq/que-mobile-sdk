package com.que.platform.android

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Helper to resolve app names to package names and launch them.
 * Uses the same approach as Blurr - searches ALL installed apps, not just launcher apps.
 */
class AppLauncher(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    companion object {
        private const val TAG = "AppLauncher"
    }

    fun launch(appName: String): Boolean {
        val query = appName.lowercase().trim()
        Log.d(TAG, "Attempting to launch app: $query")
        
        // Find package name from app name
        val packageName = findPackageNameFromAppName(query)
        
        if (packageName != null) {
            Log.d(TAG, "Found package: $packageName for app: $query")
            return launchPackage(packageName)
        }
        
        // If still not found, try as direct package name
        if (query.contains(".")) {
            Log.d(TAG, "Trying as package name: $query")
            return launchPackage(query)
        }

        Log.w(TAG, "App not found: $query")
        return false
    }
    
    /**
     * Finds package name from app name by searching ALL installed applications.
     * Filters out system apps and prioritizes user-installed apps.
     */
    private fun findPackageNameFromAppName(appName: String): String? {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }

        Log.d(TAG, "Searching for '$appName' through ${packages.size} installed apps")

        // Separate user apps from system apps
        val userApps = mutableListOf<android.content.pm.ApplicationInfo>()
        val systemApps = mutableListOf<android.content.pm.ApplicationInfo>()
        
        for (appInfo in packages) {
            // Check if it's a user-installed app (not a system app)
            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystemApp) {
                systemApps.add(appInfo)
            } else {
                userApps.add(appInfo)
            }
        }

        Log.d(TAG, "Found ${userApps.size} user apps and ${systemApps.size} system apps")

        // 1. First, try exact match in USER apps only
        for (appInfo in userApps) {
            val label = packageManager.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                Log.d(TAG, "Exact match in user apps: $label -> ${appInfo.packageName}")
                return appInfo.packageName
            }
        }

        // 2. Then try exact match in system apps
        for (appInfo in systemApps) {
            val label = packageManager.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                Log.d(TAG, "Exact match in system apps: $label -> ${appInfo.packageName}")
                return appInfo.packageName
            }
        }

        // 3. Try partial match in USER apps only (to avoid matching system apps)
        val userMatches = mutableListOf<Pair<String, String>>()
        for (appInfo in userApps) {
            val label = packageManager.getApplicationLabel(appInfo).toString()
            if (label.contains(appName, ignoreCase = true)) {
                userMatches.add(Pair(label, appInfo.packageName))
            }
        }

        if (userMatches.isNotEmpty()) {
            // Sort by label length to prefer more specific matches
            val bestMatch = userMatches.minByOrNull { it.first.length }
            if (bestMatch != null) {
                Log.d(TAG, "Best partial match in user apps: ${bestMatch.first} -> ${bestMatch.second}")
                return bestMatch.second
            }
        }

        // 4. Only if no user app matches, try partial match in system apps
        for (appInfo in systemApps) {
            val label = packageManager.getApplicationLabel(appInfo).toString()
            if (label.contains(appName, ignoreCase = true)) {
                Log.d(TAG, "Partial match in system apps: $label -> ${appInfo.packageName}")
                return appInfo.packageName
            }
        }

        Log.w(TAG, "No app found matching: $appName")
        return null
    }
    
    private fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Successfully launched: $packageName")
                true
            } else {
                Log.w(TAG, "No launch intent for: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching $packageName", e)
            false
        }
    }
    
    // Removed: No longer needed since we search all apps dynamically
    fun refreshAppList() {
        // This method is no longer needed but kept for compatibility
        Log.d(TAG, "refreshAppList() called but not needed - we search all apps dynamically")
    }
}
