package com.que.core

// Action is in com.que.core, no import needed if in same package
// RecoveryStrategy is in com.que.core, no import needed

class StuckStateDetector {
    private val stateHistory = mutableListOf<String>()
    private val maxHistorySize = 10
    
    fun recordState(screen: ScreenSnapshot) {
        // Create a signature based on activity and element count/structure
        // We can add a hash of simplifiedDescription for more accuracy
        val stateSignature = "${screen.activityName}_${screen.interactiveElements.size}_${screen.simplifiedDescription.hashCode()}"
        stateHistory.add(stateSignature)
        
        if (stateHistory.size > maxHistorySize) {
            stateHistory.removeAt(0)
        }
    }
    
    fun isStuck(): Boolean {
        if (stateHistory.size < 5) return false
        
        // Check if last 5 states are identical (agent not making progress)
        val last5 = stateHistory.takeLast(5)
        return last5.distinct().size == 1
    }
    
    fun suggestRecovery(screen: ScreenSnapshot): RecoveryStrategy? {
        return when {
            screen.activityName.contains("launcher", ignoreCase = true) -> {
                // Stuck on home screen - maybe try to reopen target app?
                // This logic is tricky without knowing the target app here
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
    }
}
