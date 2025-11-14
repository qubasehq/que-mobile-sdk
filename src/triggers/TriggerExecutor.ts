import { Agent } from '../core/Agent'
import { TriggerManager } from './TriggerManager'
import { TriggerConfig, TriggerExecutionEvent } from './types'
import { AgentResult } from '../types'

export interface TriggerExecutorConfig {
  triggerManager: TriggerManager
  onExecutionStart?: (triggerId: string) => void
  onExecutionComplete?: (triggerId: string, result: AgentResult) => void
  onExecutionError?: (triggerId: string, error: Error) => void
}

export class TriggerExecutor {
  private triggerManager: TriggerManager
  private executingTriggers: Set<string> = new Set()
  private config: TriggerExecutorConfig

  constructor(config: TriggerExecutorConfig) {
    this.config = config
    this.triggerManager = config.triggerManager
    this.setupExecutionListener()
  }

  private setupExecutionListener(): void {
    // Listen for trigger execution events
    this.triggerManager.onTriggerExecution(async (event: TriggerExecutionEvent) => {
      await this.executeTrigger(event.triggerId, event)
    })
  }

  async executeTrigger(
    triggerId: string,
    event?: TriggerExecutionEvent
  ): Promise<AgentResult | null> {
    // Prevent concurrent execution of same trigger
    if (this.executingTriggers.has(triggerId)) {
      console.warn(`Trigger ${triggerId} is already executing, skipping`)
      return null
    }

    try {
      this.executingTriggers.add(triggerId)

      // Get trigger config
      const triggerConfig = await this.triggerManager.getTrigger(triggerId)
      if (!triggerConfig) {
        throw new Error(`Trigger ${triggerId} not found`)
      }

      if (!triggerConfig.enabled) {
        console.warn(`Trigger ${triggerId} is disabled, skipping execution`)
        return null
      }

      // Notify execution start
      this.config.onExecutionStart?.(triggerId)

      // Create agent with trigger's config
      const agent = new Agent(triggerConfig.agentConfig)

      // Build task with context from event
      const task = this.buildTaskWithContext(triggerConfig, event)

      // Execute agent
      const result = await agent.run(task)

      // Record execution
      this.triggerManager.recordExecution(
        triggerId,
        result.success,
        result.success ? undefined : result.message,
        result
      )

      // Notify execution complete
      this.config.onExecutionComplete?.(triggerId, result)

      return result
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error)
      console.error(`Error executing trigger ${triggerId}:`, error)

      // Record failed execution
      this.triggerManager.recordExecution(triggerId, false, errorMessage)

      // Notify execution error
      this.config.onExecutionError?.(triggerId, error as Error)

      return null
    } finally {
      this.executingTriggers.delete(triggerId)
    }
  }

  private buildTaskWithContext(
    triggerConfig: TriggerConfig,
    event?: TriggerExecutionEvent
  ): string {
    let task = triggerConfig.task

    // Add context from notification trigger
    if (event?.triggerType === 'notification' && event.metadata) {
      const { notificationPackage, notificationTitle, notificationText } = event.metadata
      
      task += `\n\nContext: This task was triggered by a notification.`
      if (notificationPackage) {
        task += `\nApp: ${notificationPackage}`
      }
      if (notificationTitle) {
        task += `\nNotification Title: ${notificationTitle}`
      }
      if (notificationText) {
        task += `\nNotification Text: ${notificationText}`
      }
    }

    // Add context from schedule trigger
    if (event?.triggerType === 'schedule') {
      task += `\n\nContext: This task was triggered by a scheduled alarm at ${new Date(event.timestamp).toLocaleString()}.`
    }

    return task
  }

  async executeMultipleTriggers(triggerIds: string[]): Promise<Map<string, AgentResult | null>> {
    const results = new Map<string, AgentResult | null>()

    // Sort by priority
    const triggers = await Promise.all(
      triggerIds.map(async id => ({
        id,
        config: await this.triggerManager.getTrigger(id)
      }))
    )

    const sortedTriggers = triggers
      .filter(t => t.config !== null)
      .sort((a, b) => (b.config?.priority || 0) - (a.config?.priority || 0))

    // Execute in priority order
    for (const trigger of sortedTriggers) {
      const result = await this.executeTrigger(trigger.id)
      results.set(trigger.id, result)
    }

    return results
  }

  isExecuting(triggerId: string): boolean {
    return this.executingTriggers.has(triggerId)
  }

  getExecutingTriggers(): string[] {
    return Array.from(this.executingTriggers)
  }

  async stopExecution(triggerId: string): Promise<void> {
    // This would require agent to support stopping
    // For now, we just remove from executing set
    this.executingTriggers.delete(triggerId)
  }
}
