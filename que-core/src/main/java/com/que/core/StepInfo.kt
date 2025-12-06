package com.que.core

/**
 * Step information for the agent loop.
 */
data class StepInfo(
    val currentStep: Int,
    val maxSteps: Int
) {
    override fun toString(): String = "Step $currentStep of $maxSteps"
}
