package com.que.core

import android.view.accessibility.AccessibilityEvent

interface EventMonitor {
    /**
     * Waits for a specific event type that matches the predicate.
     * @return true if event occurred, false if timed out.
     */
    suspend fun waitForEvent(
        eventType: Int,
        timeout: Long = 5000,
        matcher: (AccessibilityEvent) -> Boolean = { true }
    ): Boolean
}
