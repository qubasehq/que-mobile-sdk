package expo.modules.quemobilesdk.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.core.interfaces.services.EventEmitter

class TriggerExecutionModule : Module() {
    private var triggerReceiver: TriggerExecutionReceiver? = null
    private var eventEmitter: EventEmitter? = null

    override fun definition() = ModuleDefinition {
        Name("TriggerExecutionModule")

        OnCreate {
            eventEmitter = appContext.legacyModule<EventEmitter>()
            registerTriggerReceiver()
        }

        OnDestroy {
            unregisterTriggerReceiver()
        }

        Events("onTriggerExecution")

        AsyncFunction("requestNotificationListenerPermission") { ->
            // This would open settings to enable notification listener
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.reactContext?.startActivity(intent)
        }

        AsyncFunction("isNotificationListenerEnabled") { ->
            val context = appContext.reactContext ?: return@AsyncFunction false
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            val packageName = context.packageName
            enabledListeners?.contains(packageName) ?: false
        }
    }

    private fun registerTriggerReceiver() {
        val context = appContext.reactContext ?: return
        triggerReceiver = TriggerExecutionReceiver { triggerId, triggerType, metadata ->
            sendTriggerExecutionEvent(triggerId, triggerType, metadata)
        }
        
        val filter = IntentFilter("expo.modules.quemobilesdk.EXECUTE_TRIGGER")
        context.registerReceiver(triggerReceiver, filter)
        Log.d("TriggerExecution", "Registered trigger execution receiver")
    }

    private fun unregisterTriggerReceiver() {
        val context = appContext.reactContext ?: return
        if (triggerReceiver != null) {
            context.unregisterReceiver(triggerReceiver)
            triggerReceiver = null
            Log.d("TriggerExecution", "Unregistered trigger execution receiver")
        }
    }

    private fun sendTriggerExecutionEvent(
        triggerId: String,
        triggerType: String,
        metadata: Map<String, String>
    ) {
        val event = mapOf(
            "triggerId" to triggerId,
            "triggerType" to triggerType,
            "timestamp" to System.currentTimeMillis(),
            "metadata" to metadata
        )
        
        eventEmitter?.emit("onTriggerExecution", event)
        Log.d("TriggerExecution", "Sent trigger execution event for $triggerId")
    }
}

class TriggerExecutionReceiver(
    private val onTriggerExecution: (String, String, Map<String, String>) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "expo.modules.quemobilesdk.EXECUTE_TRIGGER") {
            val triggerId = intent.getStringExtra("triggerId") ?: return
            val triggerType = intent.getStringExtra("triggerType") ?: "unknown"
            
            val metadata = mutableMapOf<String, String>()
            
            // Add notification metadata if present
            intent.getStringExtra("notificationPackage")?.let {
                metadata["notificationPackage"] = it
            }
            intent.getStringExtra("notificationTitle")?.let {
                metadata["notificationTitle"] = it
            }
            intent.getStringExtra("notificationText")?.let {
                metadata["notificationText"] = it
            }
            
            Log.d("TriggerExecution", "Received trigger execution for $triggerId ($triggerType)")
            onTriggerExecution(triggerId, triggerType, metadata)
        }
    }
}
