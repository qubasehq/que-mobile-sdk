package com.que.core.strategy
import com.que.core.service.Action
import com.que.core.service.ScreenSnapshot

// Action is in com.que.core, no import needed if in same package
// RecoveryStrategy is in com.que.core, no import needed

class StuckStateDetector {
    private val stateHistory = mutableListOf<String>()
    private val actionHistory = mutableListOf<String>()
    private val maxHistorySize = 10
    
    fun recordState(screen: ScreenSnapshot, lastActionName: String = "None") {
        // Create a signature based on activity and element count/structure
        // We can add a hash of simplifiedDescription for more accuracy
        val stateSignature = "${screen.activityName}_${screen.interactiveElements.size}_${screen.simplifiedDescription.hashCode()}"
        stateHistory.add(stateSignature)
        actionHistory.add(lastActionName)
        
        if (stateHistory.size > maxHistorySize) {
            stateHistory.removeAt(0)
            actionHistory.removeAt(0)
        }
    }
    
    fun isStuck(): Boolean {
        if (stateHistory.size < 4) return false
        
        // Check if last 4 states are identical (agent not making progress)
        val last4States = stateHistory.takeLast(4)
        val last4Actions = actionHistory.takeLast(4)
        
        // If we've done the exact same thing 4 times in the exact same state, we are stuck
        return last4States.distinct().size == 1 && last4Actions.distinct().size == 1
    }
    
    fun suggestRecovery(screen: ScreenSnapshot): RecoveryStrategy? {
        val lastAction = actionHistory.lastOrNull() ?: "Unknown"
        
        return when {
            lastAction == "WaitForIdle" -> {
                // If we are stuck spamming WaitForIdle, we need the user to know or we need to try something else
                RecoveryStrategy.AlternativeAction(listOf(
                    Action.Narrate("I appear to be stuck waiting.", "warning"),
                    Action.Back 
                ))
            }
            screen.activityName.contains("launcher", ignoreCase = true) -> {
                null 
            }
            else -> {
                // Try going back to break the loop
                RecoveryStrategy.AlternativeAction(listOf(Action.Back))
            }
        }
    }
    
    fun clear() {
        stateHistory.clear()
        actionHistory.clear()
    }
}
