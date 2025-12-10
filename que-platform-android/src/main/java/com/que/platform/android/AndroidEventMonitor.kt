package com.que.platform.android

import android.view.accessibility.AccessibilityEvent
import com.que.core.EventMonitor
import com.que.core.ServiceManager
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class AndroidEventMonitor : EventMonitor {
    override suspend fun waitForEvent(
        eventType: Int, 
        timeout: Long, 
        matcher: (AccessibilityEvent) -> Boolean
    ): Boolean {
        return withTimeoutOrNull(timeout) {
            val service = ServiceManager.getService<QueAccessibilityService>()
            if (service == null) {
                return@withTimeoutOrNull false
            }
            
            service.eventFlow.first { event -> 
                event.eventType == eventType && matcher(event)
            }
            true
        } ?: false
    }
}
