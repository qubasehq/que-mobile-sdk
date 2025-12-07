package com.que.platform.android.intents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Intent to open a URL in the browser.
 * Example: "Open google.com"
 */
class ViewUrlIntent(private val context: Context) {
    
    fun execute(url: String): Boolean {
        return try {
            var finalUrl = url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                finalUrl = "https://$url"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(finalUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.i("ViewUrlIntent", "Opened URL: $finalUrl")
            true
        } catch (e: Exception) {
            Log.e("ViewUrlIntent", "Failed to open URL", e)
            false
        }
    }
}
