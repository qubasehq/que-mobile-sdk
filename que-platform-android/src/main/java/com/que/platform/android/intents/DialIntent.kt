package com.que.platform.android.intents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Intent to make a phone call.
 * Example: "Call mom at 555-1234"
 */
class DialIntent(private val context: Context) {
    
    fun execute(phoneNumber: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            if (cleanNumber.isBlank()) {
                Log.e("DialIntent", "Invalid phone number: $phoneNumber")
                return false
            }
            
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.i("DialIntent", "Opened dialer with number: $cleanNumber")
            true
        } catch (e: Exception) {
            Log.e("DialIntent", "Failed to open dialer", e)
            false
        }
    }
}
