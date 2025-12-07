package com.que.expo

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.que.platform.android.QueClient
import com.que.platform.android.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QueExpoModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var queClient: QueClient? = null
    private var apiKey: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun getName(): String = "QueMobileSDK"

    @ReactMethod
    fun hasRequiredPermissions(promise: Promise) {
        try {
            val context = reactApplicationContext
            val overlay = PermissionManager.hasOverlayPermission(context)
            val accessibility = PermissionManager.isAccessibilityServiceEnabled(
                context,
                com.que.platform.android.QueAccessibilityService::class.java
            )
            promise.resolve(overlay && accessibility)
        } catch (e: Exception) {
            promise.reject("PERMISSION_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun requestPermissions(promise: Promise) {
        try {
            val context = reactApplicationContext
            if (!PermissionManager.hasOverlayPermission(context)) {
                PermissionManager.requestOverlayPermission(context)
            }
            PermissionManager.requestAccessibilityPermission(context)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("PERMISSION_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setApiKey(key: String, promise: Promise) {
        try {
            apiKey = key
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("API_KEY_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun startAgent(task: String, promise: Promise) {
        scope.launch {
            try {
                val context = reactApplicationContext
                val key = apiKey ?: run {
                    promise.reject("API_KEY_MISSING", "API key not set", null)
                    return@launch
                }

                if (!PermissionManager.hasAllPermissions(context)) {
                    promise.reject("PERMISSIONS_MISSING", "Required permissions not granted", null)
                    return@launch
                }

                // Send state updates
                sendEvent("onStateChange", "Perceiving")

                queClient = QueClient.Builder(context)
                    .setApiKey(key)
                    .build()

                // Simulate agent execution
                sendEvent("onStateChange", "Thinking")
                kotlinx.coroutines.delay(1000)
                sendEvent("onStateChange", "Acting")
                kotlinx.coroutines.delay(1000)
                sendEvent("onStateChange", "Finished")

                promise.resolve("Task completed")
            } catch (e: Exception) {
                sendEvent("onStateChange", "Error")
                promise.reject("AGENT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun stopAgent(promise: Promise) {
        try {
            queClient = null
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("STOP_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for RCTDeviceEventEmitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for RCTDeviceEventEmitter
    }

    private fun sendEvent(eventName: String, data: String) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, data)
    }
}
