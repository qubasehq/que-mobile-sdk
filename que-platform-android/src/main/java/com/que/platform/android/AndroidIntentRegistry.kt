package com.que.platform.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.que.core.IntentRegistry

/**
 * Android implementation of IntentRegistry.
 * Handles launching system intents like Dial, View URL, Share, etc.
 */
class AndroidIntentRegistry(private val context: Context) : IntentRegistry {

    override fun launch(intentName: String, parameters: Map<String, String>): Boolean {
        return try {
            when (intentName.lowercase()) {
                "dial" -> launchDial(parameters["phone_number"])
                "view_url" -> launchUrl(parameters["url"])
                "share" -> launchShare(parameters["text"])
                "email" -> launchEmail(parameters["to"], parameters["subject"], parameters["body"])
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun listIntents(): List<String> {
        return listOf("dial", "view_url", "share", "email")
    }

    private fun launchDial(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun launchUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun launchShare(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }

    private fun launchEmail(to: String?, subject: String?, body: String?): Boolean {
        if (to.isNullOrBlank()) return false
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (!body.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
