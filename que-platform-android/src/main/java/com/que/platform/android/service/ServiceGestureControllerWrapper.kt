package com.que.platform.android.service
import com.que.core.registry.ServiceManager
import com.que.platform.android.util.QueClient

import java.lang.ref.WeakReference
import kotlinx.coroutines.delay

/**
 * Wraps the static instance access to allow the Executor to be created
 * even if the Service isn't connected yet.
 * Shared between QueClient and QueAgentService.
 */
internal class ServiceGestureControllerWrapper : com.que.actions.GestureController {
    private var serviceRef: WeakReference<QueAccessibilityService>? = null
    private val checkInterval = 500L
    
    private class ServiceDisconnectedException(message: String) : Exception(message)

    private suspend fun getService(): QueAccessibilityService {
        // Check if current reference is still valid
        val current = serviceRef?.get()
        if (current != null && current.isConnected) {
            return current
        }
        
        // Clear stale reference
        serviceRef = null
        
        // Wait for new instance
        return waitForService()
    }
    
    private suspend fun waitForService(): QueAccessibilityService {
        repeat(20) { // 10 second timeout
            com.que.core.registry.ServiceManager.getService<QueAccessibilityService>()?.takeIf { it.isConnected }?.let {
                serviceRef = WeakReference(it)
                return it
            }
            delay(checkInterval)
        }
        throw ServiceDisconnectedException("Accessibility service unavailable")
    }

    override suspend fun dispatchGesture(path: android.graphics.Path, duration: Long): Boolean {
        return try {
            getService().dispatchGesture(path, duration)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun performGlobal(action: Int): Boolean {
        return try {
            getService().performGlobal(action)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun click(x: Int, y: Int): Boolean {
        return try {
            getService().click(x, y)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun scroll(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        return try {
            getService().scroll(x1, y1, x2, y2, duration)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun setText(text: String): Boolean {
        return try {
            getService().setText(text)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun openApp(packageName: String): Boolean {
        return try {
            getService().openApp(packageName)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun launchAppByName(appName: String): Boolean {
        return try {
            getService().launchAppByName(appName)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun speak(text: String): Boolean {
        return try {
            getService().speak(text)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun tap(x: Int, y: Int): Boolean {
        return click(x, y)
    }
    
    override suspend fun longPress(x: Int, y: Int, duration: Long): Boolean {
        return try {
            val path = android.graphics.Path().apply {
                moveTo(x.toFloat(), y.toFloat())
                lineTo(x.toFloat(), y.toFloat())
            }
            dispatchGesture(path, duration)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun doubleTap(x: Int, y: Int): Boolean {
        return try {
            click(x, y)
            delay(100)
            click(x, y)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        return scroll(x1, y1, x2, y2, duration)
    }
}
