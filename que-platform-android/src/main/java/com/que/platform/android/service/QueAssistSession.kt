package com.que.platform.android.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

class QueAssistSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "QueAssistSession"
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "Assistance triggered via power button or gesture")
        
        // Notify the agent service that assist was activated
        // Launch the main app Activity
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }

        // Hide the default unhelpful translucent window of VoiceInteractionSession
        hide()
    }
}
