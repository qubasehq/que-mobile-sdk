package com.que.core

import android.content.Context
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.util.Log

/**
 * Detects various types of interruptions that might occur during agent execution.
 */
class InterruptionDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "InterruptionDetector"
    }
    
    /**
     * Check if the device is currently locked
     */
    fun isDeviceLocked(): Boolean {
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.isKeyguardLocked
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check keyguard state", e)
            false
        }
    }
    
    /**
     * Check if required permissions are still granted
     */
    fun arePermissionsGranted(): Boolean {
        return try {
            // Check accessibility permission
            val accessibilityPermission = context.checkCallingOrSelfPermission(
                android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
            
            // Check overlay permission
            val overlayPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
            
            accessibilityPermission && overlayPermission
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check permissions", e)
            false
        }
    }
    
    /**
     * Detect the type of interruption based on current system state
     */
    fun detectInterruption(): InterruptionType? {
        return when {
            isDeviceLocked() -> InterruptionType.DEVICE_LOCKED
            !arePermissionsGranted() -> InterruptionType.PERMISSION_REVOKED
            else -> null // No interruption detected
        }
    }
    
    /**
     * Check if a system dialog or overlay is present
     */
    fun isSystemDialogPresent(): Boolean {
        // This is a simplified check - in a real implementation,
        // you might monitor window types or accessibility events
        return false
    }
}