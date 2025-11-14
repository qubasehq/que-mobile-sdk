package expo.modules.quemobilesdk.triggers

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject

class QueNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "QueNotificationListener"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        try {
            val packageName = sbn.packageName
            val notification = sbn.notification
            val extras = notification.extras

            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            Log.d(TAG, "Notification from $packageName: $title - $text")

            // Check if any triggers match this notification
            val database = TriggerDatabase(applicationContext)
            val triggers = database.getEnabledTriggers()

            for (trigger in triggers) {
                if (trigger.type == "notification" && trigger.notificationConfigJson != null) {
                    if (matchesNotificationTrigger(trigger, packageName, title, text)) {
                        Log.d(TAG, "Trigger ${trigger.id} matched notification")
                        
                        // Send broadcast to execute trigger
                        val executeIntent = Intent("expo.modules.quemobilesdk.EXECUTE_TRIGGER").apply {
                            putExtra("triggerId", trigger.id)
                            putExtra("triggerType", "notification")
                            putExtra("notificationPackage", packageName)
                            putExtra("notificationTitle", title)
                            putExtra("notificationText", text)
                        }
                        sendBroadcast(executeIntent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // Could be used for trigger cleanup if needed
    }

    private fun matchesNotificationTrigger(
        trigger: TriggerEntity,
        packageName: String,
        title: String,
        text: String
    ): Boolean {
        try {
            val config = JSONObject(trigger.notificationConfigJson ?: return false)
            val targetPackage = config.getString("packageName")

            // Check package name match
            if (targetPackage != packageName) {
                return false
            }

            // Check title pattern if specified
            if (config.has("titlePattern")) {
                val titlePattern = config.getString("titlePattern")
                if (titlePattern.isNotEmpty() && !title.contains(titlePattern, ignoreCase = true)) {
                    return false
                }
            }

            // Check text pattern if specified
            if (config.has("textPattern")) {
                val textPattern = config.getString("textPattern")
                if (textPattern.isNotEmpty() && !text.contains(textPattern, ignoreCase = true)) {
                    return false
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error matching notification trigger", e)
            return false
        }
    }
}
