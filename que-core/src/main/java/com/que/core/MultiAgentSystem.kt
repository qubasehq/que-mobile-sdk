package com.que.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/**
 * Roles that an agent can specialize in.
 */
enum class AgentRole {
    ORCHESTRATOR, // Main coordinator
    NAVIGATOR,    // Finds and clicks elements
    DATA_EXTRACTOR, // Scrapes and summarizes
    VALIDATOR,    // Checks outcomes
    RECOVERY      // Handles errors
}

/**
 * Defines what an agent can do.
 */
data class AgentCapability(
    val role: AgentRole,
    val skills: Set<String>,
    val maxConcurrentTasks: Int = 1
)

/**
 * Context for a delegated task.
 */
data class TaskContext(
    val parentTaskId: String? = null,
    val timeoutMs: Long = 60000,
    val priority: Int = 0
)

/**
 * Result of a delegated task.
 */
data class TaskResult(
    val success: Boolean,
    val output: String,
    val artifacts: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Interface for coordinating multiple agents.
 */
interface MultiAgentCoordinator {
    suspend fun delegateTask(
        subtask: String,
        requiredRole: AgentRole,
        context: TaskContext
    ): TaskResult
    
    suspend fun parallelExecute(
        tasks: List<Pair<String, AgentRole>>,
        context: TaskContext
    ): List<TaskResult>
}

/**
 * Implementation of a swarm of agents working together.
 */
class AgentSwarm(
    private val agents: Map<AgentRole, Agent>
) : MultiAgentCoordinator {
    
    override suspend fun delegateTask(
        subtask: String,
        requiredRole: AgentRole,
        context: TaskContext
    ): TaskResult {
        val agent = agents[requiredRole] 
            ?: throw IllegalArgumentException("No agent for role $requiredRole")
        
        return try {
            withTimeout(context.timeoutMs) {
                // In a real system, we'd start a new session/thread for the agent
                // For now, we assume the agent can run the subtask 
                // Note: This blocks unless Agent.run is async and returns a Job we wait on, 
                // or returns a result. Our Agent.run is suspend and returns Unit.
                // We need to capture the state AFTER run.
                
                agent.run(subtask)
                
                // Inspect state to determine result
                val state = agent.state.value
                when (state) {
                    is AgentState.Finished -> TaskResult(true, state.result)
                    is AgentState.Error -> TaskResult(false, "", error = state.message)
                    else -> TaskResult(false, "Task finished in unknown state: $state")
                }
            }
        } catch (e: Exception) {
            TaskResult(false, "", error = e.message)
        }
    }
    
    override suspend fun parallelExecute(
        tasks: List<Pair<String, AgentRole>>,
        context: TaskContext
    ): List<TaskResult> = coroutineScope {
        tasks.map { (task, role) ->
            async {
                delegateTask(task, role, context)
            }
        }.awaitAll()
    }
}
