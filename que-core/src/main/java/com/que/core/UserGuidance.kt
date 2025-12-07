package com.que.core

import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Interface for providing real-time feedback and guidance to the user.
 */
interface UserGuidance {
    fun showProgress(step: Int, total: Int, description: String)
    fun showDecision(thought: String, confidence: Float)
    fun showAlternatives(alternatives: List<String>)
    suspend fun askForHelp(question: String, options: List<String>): String
}

/**
 * No-op implementation for when guidance is not available.
 */
object NoOpUserGuidance : UserGuidance {
    override fun showProgress(step: Int, total: Int, description: String) {}
    override fun showDecision(thought: String, confidence: Float) {}
    override fun showAlternatives(alternatives: List<String>) {}
    override suspend fun askForHelp(question: String, options: List<String>): String = ""
}
