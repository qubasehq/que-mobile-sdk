# Que Mobile SDK Performance Optimization Design

## Overview

This design document outlines a comprehensive optimization strategy for the Que Mobile SDK, addressing critical performance bottlenecks, robotic behavior patterns, and architectural inefficiencies identified in the current implementation. The optimization focuses on five key areas: LLM interaction efficiency, perception engine performance, intelligent agent behavior, memory management, and data structure optimization.

The current SDK implements a Sense → Think → Act loop with significant latency issues (2-8 seconds per step) primarily due to verbose prompts (3000-8000 tokens), inefficient UI parsing, and lack of predictive capabilities. This design transforms the architecture into a high-performance, intelligent automation system.

## Architecture

### Current Architecture Analysis

The existing Que Mobile SDK follows a modular architecture:

- **que-core**: Central orchestration with QueAgent, AgentMemoryManager, PerceptionEngine interfaces
- **que-platform-android**: Android-specific implementations with QueAccessibilityService, QuePerceptionEngine
- **que-vision**: Visual processing with SemanticParser and caching systems
- **que-llm**: LLM integration with GeminiClient and circuit breaker patterns
- **que-actions**: Action execution system with 30+ action types and data-driven parsing

### Optimized Architecture

The optimized architecture introduces several new components and enhances existing ones:

```
┌─────────────────────────────────────────────────────────┐
│                 OPTIMIZED AGENT LOOP                   │
│                                                         │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐ │
│  │    SENSE    │ →  │    THINK    │ →  │     ACT     │ │
│  │             │    │             │    │             │ │
│  │ Delta       │    │ Compressed  │    │ Batched     │ │
│  │ Perception  │    │ Prompts +   │    │ Execution   │ │
│  │ + Caching   │    │ Planning    │    │ + Pipeline  │ │
│  └─────────────┘    └─────────────┘    └─────────────┘ │
│         ↑                   ↑                   ↓       │
│         │                   │                   │       │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐ │
│  │ Incremental │    │ Hierarchical│    │ Predictive  │ │
│  │ Parser +    │    │ Memory +    │    │ Pre-loading │ │
│  │ Smart Cache │    │ Context Mgmt│    │ + Recovery  │ │
│  └─────────────┘    └─────────────┘    └─────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Enhanced Core Components

#### 1. OptimizedQueAgent
```kotlin
class OptimizedQueAgent(
    private val deltaPerception: DeltaPerceptionEngine,
    private val compactPromptBuilder: CompactPromptBuilder,
    private val batchedExecutor: BatchedActionExecutor,
    private val hierarchicalMemory: HierarchicalMemorySystem,
    private val predictivePlanner: EnhancedPredictivePlanner,
    private val performanceMonitor: PerformanceMonitor
) : Agent {
    private val batchSize = 3 // Execute up to 3 actions per LLM call
    private val contextWindow = SlidingWindowMemory(maxSize = 8)
    
    suspend fun optimizedLoop(task: String) {
        while (!isComplete && steps < maxSteps) {
            val stepStart = System.currentTimeMillis()
            
            // SENSE (with delta detection)
            val screenDelta = deltaPerception.captureDelta()
            performanceMonitor.recordSenseTime(System.currentTimeMillis() - stepStart)
            
            // THINK (with batched planning)
            val thinkStart = System.currentTimeMillis()
            val agentOutput = if (shouldReplan(screenDelta)) {
                generateBatchedPlan(task, screenDelta)
            } else {
                executeCachedPlan()
            }
            performanceMonitor.recordThinkTime(System.currentTimeMillis() - thinkStart)
            
            // ACT (with pipelining)
            val actStart = System.currentTimeMillis()
            val results = batchedExecutor.executeBatchWithPipelining(agentOutput.actions)
            performanceMonitor.recordActTime(System.currentTimeMillis() - actStart)
            
            // LEARN (with pattern recognition)
            updatePatterns(agentOutput, results)
            steps++
        }
    }
}
```

#### 2. DeltaPerceptionEngine
```kotlin
class DeltaPerceptionEngine : PerceptionEngine {
    private var lastSnapshot: ScreenSnapshot? = null
    private val structuralHasher = StructuralHasher()
    private val smartCache = SmartCache()
    
    suspend fun captureDelta(): ScreenDelta {
        val currentHash = structuralHasher.computeHash(getRootNode())
        
        if (smartCache.isValid(currentHash)) {
            return ScreenDelta.NoChange
        }
        
        val current = captureFullSnapshot()
        val delta = computeDelta(lastSnapshot, current)
        lastSnapshot = current
        smartCache.update(currentHash, current)
        
        return delta
    }
    
    private fun computeDelta(old: ScreenSnapshot?, new: ScreenSnapshot): ScreenDelta {
        if (old == null) return ScreenDelta.FullRefresh(new)
        
        val newElements = new.interactiveElements.filter { newEl ->
            old.interactiveElements.none { oldEl -> oldEl.id == newEl.id }
        }
        
        val changedElements = new.interactiveElements.filter { newEl ->
            old.interactiveElements.any { oldEl -> 
                oldEl.id == newEl.id && oldEl.description != newEl.description 
            }
        }
        
        return ScreenDelta.Incremental(newElements, changedElements, new.activityName)
    }
}
```

#### 3. CompactPromptBuilder
```kotlin
class CompactPromptBuilder {
    fun buildSystemPrompt(): String = """
        You are QUE, an Android automation agent. Execute tasks via JSON actions.
        
        ACTIONS: ${getCompactActionList()}
        
        RULES:
        - Use element IDs from [N] format
        - Batch multiple actions in "actions" array
        - Respond: {"thought":"...", "actions":[...]}
        
        EFFICIENCY: Plan 2-5 actions ahead when possible.
    """.trimIndent()
    
    fun buildUserMessage(context: AgentContext): String = """
        TASK: ${context.task}
        ${if (context.hasChanges) "🔄 SCREEN CHANGED" else ""}
        ${context.progressSummary}
        
        ELEMENTS:
        ${context.compactElementList}
        
        NEXT: Plan your actions to make progress toward the task.
    """.trimIndent()
    
    private fun getCompactActionList(): String {
        return Action.getAllSpecs().joinToString(", ") { "${it.name}(${it.params.joinToString { p -> p.name }})" }
    }
}
```

#### 4. HierarchicalMemorySystem
```kotlin
class HierarchicalMemorySystem {
    private val shortTerm = LRUCache<String, Memory>(50) // Current session
    private val mediumTerm = LRUCache<String, Memory>(200) // Recent sessions
    private val longTerm = PersistentMemory() // Cross-session patterns
    
    suspend fun recall(query: String): List<Memory> {
        return shortTerm.search(query) + 
               mediumTerm.search(query) + 
               longTerm.search(query)
    }
    
    suspend fun remember(key: String, value: String, context: MemoryContext) {
        val memory = Memory(key, value, context, System.currentTimeMillis())
        
        shortTerm.put(key, memory)
        
        // Promote to medium-term if accessed frequently
        if (memory.accessCount > 3) {
            mediumTerm.put(key, memory)
        }
        
        // Promote to long-term if successful pattern
        if (memory.successRate > 0.8) {
            longTerm.store(memory)
        }
    }
}
```

#### 5. BatchedActionExecutor
```kotlin
class BatchedActionExecutor : ActionExecutor {
    suspend fun executeBatchWithPipelining(actions: List<Action>): List<ActionResult> {
        val results = mutableListOf<ActionResult>()
        
        for ((index, action) in actions.withIndex()) {
            // Start next perception capture while current action executes
            val nextPerceptionJob = if (index < actions.size - 1) {
                async { perceptionEngine.preloadCapture() }
            } else null
            
            val result = executeWithDelay(action, getOptimalDelay(action))
            results.add(result)
            
            // Stop batch if action fails
            if (!result.success) break
            
            nextPerceptionJob?.await()
        }
        
        return results
    }
    
    private fun getOptimalDelay(action: Action): Long = when (action) {
        is Action.Type -> 100L
        is Action.Tap -> 200L
        is Action.Scroll -> 300L
        else -> 150L
    }
}
```

## Data Models

### Optimized Data Structures

#### 1. CompactScreenSnapshot
```kotlin
data class CompactScreenSnapshot(
    val elementMap: Map<Int, CompactElement>, // O(1) lookup
    val layoutSignature: String, // For change detection
    val scrollState: ScrollInfo,
    val contextHints: List<String>, // App-specific context
    val timestamp: Long
)

data class CompactElement(
    val id: Int,
    val type: ElementType,
    val text: String?,
    val bounds: IntArray, // [x,y,w,h] - more compact than Rect
    val flags: Int // Packed boolean flags (clickable, enabled, etc.)
)
```

#### 2. ScreenDelta
```kotlin
sealed class ScreenDelta {
    object NoChange : ScreenDelta()
    data class FullRefresh(val snapshot: ScreenSnapshot) : ScreenDelta()
    data class Incremental(
        val newElements: List<InteractiveElement>,
        val changedElements: List<InteractiveElement>,
        val activityName: String
    ) : ScreenDelta()
}
```

#### 3. AgentContext
```kotlin
data class AgentContext(
    val task: String,
    val progressSummary: String,
    val compactElementList: String,
    val hasChanges: Boolean,
    val appContext: String,
    val relevantMemories: List<Memory>
)
```

#### 4. PerformanceMetrics
```kotlin
data class PerformanceMetrics(
    val senseTimeMs: Long,
    val thinkTimeMs: Long,
    val actTimeMs: Long,
    val totalStepTimeMs: Long,
    val tokenCount: Int,
    val cacheHitRate: Float,
    val memoryUsageMB: Float
)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Performance Properties

**Property 1: Agent Loop Performance**
*For any* user instruction and screen state, the complete Sense → Think → Act cycle should complete in under 5 seconds
**Validates: Requirements 1.1**

**Property 2: Prompt Compression**
*For any* screen state and conversation history, generated prompts should be under 2000 tokens while preserving essential context
**Validates: Requirements 1.2**

**Property 3: Delta Detection Optimization**
*For any* screen capture sequence, unchanged UI elements should trigger delta detection rather than full re-parsing
**Validates: Requirements 1.3**

**Property 4: Action Batching**
*For any* multi-action plan, related actions should be batched up to 5 actions per LLM call
**Validates: Requirements 1.4**

**Property 5: Rate Limiting Resilience**
*For any* rate limiting scenario, the system should implement intelligent backoff with circuit breaker patterns
**Validates: Requirements 1.5**

### Intelligence Properties

**Property 6: Multi-Step Planning**
*For any* complex task, the system should generate multi-step sequences that logically progress toward goals
**Validates: Requirements 2.1**

**Property 7: Pattern Learning**
*For any* repeated context, successful patterns should be remembered and reused in similar situations
**Validates: Requirements 2.2**

**Property 8: Contextual Recovery**
*For any* error condition, appropriate recovery strategies should be selected based on error type and app state
**Validates: Requirements 2.3**

**Property 9: Task Decomposition**
*For any* complex goal, the system should break it into logical sub-tasks with trackable progress
**Validates: Requirements 2.5**

### Memory Management Properties

**Property 10: Sliding Window Memory**
*For any* growing conversation, sliding window memory management should maintain appropriate size while preserving important context
**Validates: Requirements 3.1**

**Property 11: Structural Hashing Cache**
*For any* screen capture, identical screens should return cached results and different screens should trigger re-parsing
**Validates: Requirements 3.2**

**Property 12: Resource Recycling**
*For any* accessibility node processing, all AccessibilityNodeInfo objects should be properly recycled after use
**Validates: Requirements 3.3**

**Property 13: Hierarchical Memory Storage**
*For any* memory operation, memories should be properly categorized and stored in appropriate tiers (short/medium/long-term)
**Validates: Requirements 3.4**

**Property 14: Context Compression**
*For any* growing context approaching limits, compression should maintain critical information while staying within bounds
**Validates: Requirements 3.5**

### Error Recovery Properties

**Property 15: Error Pattern Analysis**
*For any* action failure, appropriate recovery strategies should be suggested based on error analysis
**Validates: Requirements 4.1**

**Property 16: Element Discovery**
*For any* missing UI element, scrolling and discovery attempts should occur before failure
**Validates: Requirements 4.2**

**Property 17: Smart Retry Patterns**
*For any* unresponsive app condition, smart retry with exponential backoff should be implemented
**Validates: Requirements 4.3**

**Property 18: Checkpoint Recovery**
*For any* device interruption, checkpoints should be created and state properly restored when conditions normalize
**Validates: Requirements 4.4**

**Property 19: Stuck State Detection**
*For any* repetitive pattern sequence, stuck states should be detected and alternative navigation attempted
**Validates: Requirements 4.5**

### Data Structure Efficiency Properties

**Property 20: O(1) Element Lookup**
*For any* UI hierarchy parsing, element lookups should be O(1) using compact data structures
**Validates: Requirements 5.1**

**Property 21: Lightweight Hashing**
*For any* screen change detection, structural hashing should occur before expensive parsing operations
**Validates: Requirements 5.2**

**Property 22: Context-Based Action Organization**
*For any* action registration, capabilities should be organized by context for efficient lookup
**Validates: Requirements 5.3**

**Property 23: Execution Pipelining**
*For any* gesture execution, preparation for next actions should occur concurrently with current execution
**Validates: Requirements 5.4**

**Property 24: Efficient Serialization**
*For any* memory operation, efficient serialization formats should be used with minimal string operations
**Validates: Requirements 5.5**

### Planning and Prediction Properties

**Property 25: Task Decomposition**
*For any* initiated task, complex goals should be decomposed into executable sub-task sequences
**Validates: Requirements 6.1**

**Property 26: Predictive Pre-loading**
*For any* action plan generation, likely next screens should be predicted and perception data pre-loaded
**Validates: Requirements 6.2**

**Property 27: Pattern Caching**
*For any* app-specific pattern identification, successful interaction sequences should be cached for reuse
**Validates: Requirements 6.3**

**Property 28: Intent Classification**
*For any* user intent, proper classification and routing to specialized handling logic should occur
**Validates: Requirements 6.4**

**Property 29: Adaptive Planning**
*For any* execution context change, planning strategies should adapt based on app behavior patterns
**Validates: Requirements 6.5**

### LLM Optimization Properties

**Property 30: Compact Prompt Generation**
*For any* system prompt building, compact action descriptions should be generated using data-driven registries
**Validates: Requirements 7.1**

**Property 31: Relevant Context Filtering**
*For any* screen context provision, only relevant UI elements should be included based on task context
**Validates: Requirements 7.2**

**Property 32: History Summarization**
*For any* conversation history inclusion, summaries should be created instead of full transcripts while preserving key information
**Validates: Requirements 7.3**

**Property 33: Multi-Format Parsing**
*For any* LLM response, both single and batched action formats should be parsed correctly
**Validates: Requirements 7.4**

**Property 34: Robust Response Processing**
*For any* LLM response processing, structured data should be extracted efficiently with proper error handling
**Validates: Requirements 7.5**

### Monitoring Properties

**Property 35: Timing Metrics Tracking**
*For any* agent loop execution, timing metrics should be properly tracked for all phases (Sense, Think, Act)
**Validates: Requirements 8.1**

**Property 36: Bottleneck Identification**
*For any* performance bottleneck, specific components causing delays should be properly identified
**Validates: Requirements 8.2**

**Property 37: Memory Usage Monitoring**
*For any* memory usage growth, consumption patterns should be properly monitored and reported
**Validates: Requirements 8.3**

**Property 38: Error Context Capture**
*For any* error occurrence, detailed context should be captured for debugging and pattern analysis
**Validates: Requirements 8.4**

**Property 39: Performance Recommendations**
*For any* performance degradation, actionable recommendations should be provided for optimization
**Validates: Requirements 8.5**

## Error Handling

The optimized SDK implements a multi-layered error handling strategy:

### 1. Circuit Breaker Pattern
```kotlin
class EnhancedCircuitBreaker {
    private var state = CircuitState.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L
    
    suspend fun <T> execute(operation: suspend () -> T): T {
        when (state) {
            CircuitState.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > timeoutMs) {
                    state = CircuitState.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            CircuitState.HALF_OPEN -> {
                try {
                    val result = operation()
                    state = CircuitState.CLOSED
                    failureCount = 0
                    return result
                } catch (e: Exception) {
                    state = CircuitState.OPEN
                    lastFailureTime = System.currentTimeMillis()
                    throw e
                }
            }
            CircuitState.CLOSED -> {
                try {
                    return operation()
                } catch (e: Exception) {
                    failureCount++
                    if (failureCount >= threshold) {
                        state = CircuitState.OPEN
                        lastFailureTime = System.currentTimeMillis()
                    }
                    throw e
                }
            }
        }
    }
}
```

### 2. Intelligent Recovery Strategies
```kotlin
class EnhancedRecoverySystem {
    suspend fun analyzeAndRecover(error: ActionResult, context: ExecutionContext): RecoveryStrategy? {
        val errorSignature = generateErrorSignature(error, context)
        
        // Check learned patterns first
        val learnedStrategy = memorySystem.getSuccessfulRecovery(errorSignature)
        if (learnedStrategy != null) return learnedStrategy
        
        // Apply heuristic-based recovery
        return when {
            error.message.contains("element not found") -> {
                RecoveryStrategy.ScrollAndRetry(inferScrollDirection(context))
            }
            error.message.contains("timeout") -> {
                RecoveryStrategy.Retry(maxAttempts = 2, delay = calculateBackoff(context))
            }
            context.consecutiveFailures > 3 -> {
                RecoveryStrategy.RestartApp(context.appName)
            }
            else -> RecoveryStrategy.Retry(maxAttempts = 1, delay = 1000)
        }
    }
}
```

### 3. Graceful Degradation
```kotlin
class GracefulDegradationManager {
    suspend fun handleDegradation(performanceMetrics: PerformanceMetrics): DegradationStrategy {
        return when {
            performanceMetrics.totalStepTimeMs > 10000 -> {
                DegradationStrategy.ReduceBatchSize(newSize = 1)
            }
            performanceMetrics.memoryUsageMB > 500 -> {
                DegradationStrategy.AggressiveMemoryCleanup
            }
            performanceMetrics.cacheHitRate < 0.3 -> {
                DegradationStrategy.OptimizeCaching
            }
            else -> DegradationStrategy.Continue
        }
    }
}
```

## Testing Strategy

The optimization implementation follows a dual testing approach combining unit tests and property-based testing:

### Unit Testing Approach
- **Component Integration Tests**: Verify interactions between optimized components
- **Performance Regression Tests**: Ensure optimizations don't break existing functionality
- **Error Scenario Tests**: Validate error handling and recovery mechanisms
- **Memory Leak Tests**: Confirm proper resource cleanup and recycling

### Property-Based Testing Approach
- **Performance Properties**: Verify timing constraints across random inputs using QuickCheck for Android
- **Memory Management Properties**: Test memory behavior with varying load patterns
- **Error Recovery Properties**: Validate recovery strategies across different failure scenarios
- **Data Structure Properties**: Confirm efficiency guarantees with large datasets

### Testing Framework Configuration
```kotlin
// Property-based testing configuration
@RunWith(QuickCheckRunner::class)
class OptimizationPropertiesTest {
    
    @Property(trials = 100)
    fun agentLoopPerformance(
        @ForAll instruction: String,
        @ForAll screenState: ScreenSnapshot
    ) {
        val startTime = System.currentTimeMillis()
        val result = optimizedAgent.processSingleStep(instruction, screenState)
        val duration = System.currentTimeMillis() - startTime
        
        assertThat(duration).isLessThan(5000) // Property 1
        assertThat(result.success).isTrue()
    }
    
    @Property(trials = 100)
    fun promptCompression(
        @ForAll context: AgentContext
    ) {
        val prompt = compactPromptBuilder.buildUserMessage(context)
        val tokenCount = tokenizer.count(prompt)
        
        assertThat(tokenCount).isLessThan(2000) // Property 2
        assertThat(prompt).contains(context.task) // Context preservation
    }
}
```

This comprehensive optimization design addresses all identified bottlenecks while maintaining the SDK's robust architecture and adding intelligent behavior patterns that will significantly improve both performance and user experience.

<function_calls>
<invoke name="prework">
<parameter name="featureName">que-mobile-sdk-optimization