package com.que.platform.android.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Dedicated activity to handle the ASSIST intent from the system.
 * This forwards the assistant trigger to the host app's launcher activity.
 */
class AssistantActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Find the launcher intent for the current package
        val launcherIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launcherIntent != null) {
            // Add a custom action to signal that we were launched via Assistant
            // Use ACTION_VOICE_ASSIST string from QueAgentService
            launcherIntent.action = "com.que.platform.android.action.VOICE_ASSIST"
            launcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launcherIntent)
        }
        
        // Assistant triggers shouldn't show a UI of their own
        finish()
    }
}
