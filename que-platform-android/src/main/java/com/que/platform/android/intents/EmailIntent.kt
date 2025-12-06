package com.que.platform.android.intents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Intent to compose an email.
 * Example: "Email john@example.com about the meeting"
 */
class EmailIntent(private val context: Context) {
    
    fun execute(to: String, subject: String = "", body: String = ""): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.i("EmailIntent", "Opened email composer for: $to")
            true
        } catch (e: Exception) {
            Log.e("EmailIntent", "Failed to open email composer", e)
            false
        }
    }
}
