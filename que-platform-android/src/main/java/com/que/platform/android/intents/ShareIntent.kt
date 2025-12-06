package com.que.platform.android.intents

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Intent to share text content.
 * Example: "Share this link on social media"
 */
class ShareIntent(private val context: Context) {
    
    fun execute(text: String, title: String = "Share"): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, title)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val chooser = Intent.createChooser(intent, title).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(chooser)
            Log.i("ShareIntent", "Opened share dialog")
            true
        } catch (e: Exception) {
            Log.e("ShareIntent", "Failed to open share dialog", e)
            false
        }
    }
}
