package com.que.platform.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.que.actions.GestureController
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
// import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The core Android Service that powers the SDK.
 * It must be registered in the host app's AndroidManifest.xml.
 */
class QueAccessibilityService : AccessibilityService(), GestureController {

    // Removed unsafe static instance
    // companion object {
    //     var instance: QueAccessibilityService? = null
    //         private set
    // }
    
    var isConnected: Boolean = false
        private set

    // Event Flow for synchronization
    private val _eventFlow = kotlinx.coroutines.flow.MutableSharedFlow<AccessibilityEvent>(extraBufferCapacity = 64, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val eventFlow: kotlinx.coroutines.flow.SharedFlow<AccessibilityEvent> = _eventFlow

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        // instance = this // Removed unsafe static assignment
        com.que.core.ServiceManager.registerAccessibilityService(this)
        debugOverlayController = DebugOverlayController(this)
        cosmicOverlay = CosmicOverlayController(this)
        appLauncher = AppLauncher(this)
        speechCoordinator = SpeechCoordinator.getInstance(this)
        
        // Health check job
        scope.launch {
            while (isConnected) {
                kotlinx.coroutines.delay(5000)
                try {
                    if (rootInActiveWindow == null) {
                        // Log but don't immediately disconnect, could be transient
                        android.util.Log.w("QueAccessibilityService", "Root node unavailable - service may be disconnecting or locked")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("QueAccessibilityService", "Health check failed", e)
                    isConnected = false
                }
            }
        }
    }

    var debugOverlayController: DebugOverlayController? = null
        private set
    
    var cosmicOverlay: CosmicOverlayController? = null
        private set
        
    private var appLauncher: AppLauncher? = null
    private var speechCoordinator: SpeechCoordinator? = null
    
    /**
     * Show cosmic overlay when agent starts
     */
    fun showCosmicOverlay() {
        cosmicOverlay?.show()
    }
    
    /**
     * Hide cosmic overlay when agent stops
     */
    fun hideCosmicOverlay() {
        cosmicOverlay?.hide()
    }
    
    /**
     * Update cosmic overlay state based on agent activity
     */
    fun setCosmicState(state: CosmicOverlayController.AgentVisualState) {
        cosmicOverlay?.setAgentState(state)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hide and cleanup cosmic overlay
        cosmicOverlay?.hide()
        cosmicOverlay = null
        // Don't shutdown speechCoordinator here as it's a singleton used by others?
        // Actually, if the service is destroyed, the context might leak if we don't be careful.
        // But SpeechCoordinator uses applicationContext.
        // Let's just null out the reference.
        speechCoordinator = null
        isConnected = false
        // instance = null // Removed
        com.que.core.ServiceManager.unregisterAccessibilityService()
        scope.cancel()
    }

    var currentActivityName: String = "Unknown"
        private set

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            // Emit event for synchronization
            _eventFlow.tryEmit(event)
            
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (event.packageName != null && event.className != null) {
                    currentActivityName = "${event.packageName}/${event.className}"
                }
            }
        }
    }

    override fun onInterrupt() {
        isConnected = false
        // instance = null // Removed
        com.que.core.ServiceManager.unregisterAccessibilityService()
        scope.cancel()
    }



    // --- GestureController Implementation ---

    override suspend fun dispatchGesture(path: Path, duration: Long): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }



    override suspend fun click(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatchGesture(path, 100)
    }

    override suspend fun scroll(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return dispatchGesture(path, duration)
    }

    override suspend fun setText(text: String): Boolean {
        // Find the focused node and set text
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        
        val arguments = android.os.Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override suspend fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun launchAppByName(appName: String): Boolean {
        return appLauncher?.launch(appName) ?: false
    }

    override suspend fun speak(text: String): Boolean {
        speechCoordinator?.speakToUser(text)
        return true
    }

    // --- Helper for Perception ---

    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    suspend fun captureScreenshot(): android.graphics.Bitmap? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return kotlin.coroutines.suspendCoroutine { continuation ->
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer,
                                result.colorSpace
                            )
                            // Copy to software bitmap to be safe for processing/sending
                            val softwareBitmap = bitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            result.hardwareBuffer.close()
                            bitmap?.recycle()
                            continuation.resumeWith(Result.success(softwareBitmap))
                        }

                        override fun onFailure(errorCode: Int) {
                            continuation.resumeWith(Result.success(null))
                        }
                    }
                )
            }
        }
        return null
    }
    // Implement GestureController method
    override suspend fun performGlobal(action: Int): Boolean {
        return super.performGlobalAction(action)
    }
    
    override suspend fun tap(x: Int, y: Int): Boolean {
        return click(x, y)
    }
    
    override suspend fun longPress(x: Int, y: Int, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        return dispatchGesture(path, duration)
    }
    
    override suspend fun doubleTap(x: Int, y: Int): Boolean {
        click(x, y)
        kotlinx.coroutines.delay(100)
        return click(x, y)
    }
    
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        return scroll(x1, y1, x2, y2, duration)
    }
}
