package com.que.platform.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

/**
 * Helper to check and request necessary permissions.
 */
object PermissionManager {

    fun hasOverlayPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        val expectedServiceName = serviceClass.name
        val expectedSimpleName = serviceClass.simpleName
        
        android.util.Log.d("PermissionManager", "Checking accessibility for: $expectedServiceName")
        android.util.Log.d("PermissionManager", "Enabled services count: ${enabledServices.size}")
        
        return enabledServices.any { service ->
            val componentName = service.resolveInfo.serviceInfo.name
            val packageName = service.resolveInfo.serviceInfo.packageName
            
            android.util.Log.d("PermissionManager", "Found service: $packageName / $componentName")
            
            // Check full name or simple name match
            componentName == expectedServiceName || 
            componentName.endsWith(".$expectedSimpleName") ||
            (packageName == context.packageName && componentName.contains(expectedSimpleName))
        }
    }

    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    fun hasAllPermissions(context: Context): Boolean {
        return hasOverlayPermission(context) && 
               isAccessibilityServiceEnabled(context, QueAccessibilityService::class.java)
    }
}
