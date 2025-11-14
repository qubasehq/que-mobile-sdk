import { NativeModules, NativeEventEmitter, Platform, EmitterSubscription } from 'react-native'
import {
  TriggerConfig,
  ScheduleTriggerConfig,
  NotificationTriggerConfig,
  TriggerStatus,
  TriggerHistoryItem,
  TriggerExecutionEvent
} from './types'
import { ScheduleTrigger } from './ScheduleTrigger'
import { NotificationTrigger } from './NotificationTrigger'

const { TriggerExecutionModule } = NativeModules

interface TriggerDatabase {
  [triggerId: string]: {
    config: TriggerConfig
    status: TriggerStatus
    history: TriggerHistoryItem[]
  }
}

export class TriggerManager {
  private database: TriggerDatabase = {}
  private eventEmitter: NativeEventEmitter | null = null
  private listeners: Map<string, (event: TriggerExecutionEvent) => void> = new Map()
  private nativeSubscription: EmitterSubscription | null = null

  constructor() {
    if (Platform.OS === 'android') {
      // Initialize event emitter for trigger execution events
      this.setupEventListeners()
    }
  }

  private setupEventListeners(): void {
    if (TriggerExecutionModule) {
      this.eventEmitter = new NativeEventEmitter(TriggerExecutionModule)
      this.nativeSubscription = this.eventEmitter.addListener(
        'onTriggerExecution',
        (event: TriggerExecutionEvent) => {
          this.notifyTriggerExecution(event)
        }
      )
    }
  }

  destroy(): void {
    if (this.nativeSubscription) {
      this.nativeSubscription.remove()
      this.nativeSubscription = null
    }
  }

  async createTrigger(config: TriggerConfig): Promise<string> {
    try {
      // Validate config
      this.validateTriggerConfig(config)

      // Store in database
      this.database[config.id] = {
        config,
        status: {
          id: config.id,
          enabled: config.enabled,
          executionCount: 0
        },
        history: []
      }

      // Schedule if enabled
      if (config.enabled) {
        await this.enableTrigger(config.id)
      }

      return config.id
    } catch (error) {
      console.error('Error creating trigger:', error)
      throw error
    }
  }

  async updateTrigger(id: string, updates: Partial<TriggerConfig>): Promise<void> {
    try {
      const trigger = this.database[id]
      if (!trigger) {
        throw new Error(`Trigger ${id} not found`)
      }

      // Update config
      const updatedConfig = { ...trigger.config, ...updates } as TriggerConfig
      this.validateTriggerConfig(updatedConfig)

      // If schedule changed and trigger is enabled, reschedule
      if (updatedConfig.type === 'schedule' && trigger.config.enabled) {
        await this.disableTrigger(id)
        trigger.config = updatedConfig
        await this.enableTrigger(id)
      } else {
        trigger.config = updatedConfig
      }
    } catch (error) {
      console.error('Error updating trigger:', error)
      throw error
    }
  }

  async deleteTrigger(id: string): Promise<void> {
    try {
      const trigger = this.database[id]
      if (!trigger) {
        throw new Error(`Trigger ${id} not found`)
      }

      // Disable if enabled
      if (trigger.config.enabled) {
        await this.disableTrigger(id)
      }

      // Remove from database
      delete this.database[id]
    } catch (error) {
      console.error('Error deleting trigger:', error)
      throw error
    }
  }

  async enableTrigger(id: string): Promise<void> {
    try {
      const trigger = this.database[id]
      if (!trigger) {
        throw new Error(`Trigger ${id} not found`)
      }

      if (trigger.config.type === 'schedule') {
        const scheduleTrigger = new ScheduleTrigger(trigger.config as ScheduleTriggerConfig)
        await scheduleTrigger.schedule()
        
        // Update next execution time
        const nextTime = await scheduleTrigger.getNextExecutionTime()
        trigger.status.nextExecution = nextTime
      }

      trigger.config.enabled = true
      trigger.status.enabled = true
    } catch (error) {
      console.error('Error enabling trigger:', error)
      throw error
    }
  }

  async disableTrigger(id: string): Promise<void> {
    try {
      const trigger = this.database[id]
      if (!trigger) {
        throw new Error(`Trigger ${id} not found`)
      }

      if (trigger.config.type === 'schedule') {
        const scheduleTrigger = new ScheduleTrigger(trigger.config as ScheduleTriggerConfig)
        await scheduleTrigger.cancel()
        
        trigger.status.nextExecution = undefined
      }

      trigger.config.enabled = false
      trigger.status.enabled = false
    } catch (error) {
      console.error('Error disabling trigger:', error)
      throw error
    }
  }

  async listTriggers(): Promise<TriggerConfig[]> {
    return Object.values(this.database).map(t => t.config)
  }

  async getTrigger(id: string): Promise<TriggerConfig | null> {
    const trigger = this.database[id]
    return trigger ? trigger.config : null
  }

  async getTriggerStatus(id: string): Promise<TriggerStatus | null> {
    const trigger = this.database[id]
    return trigger ? trigger.status : null
  }

  async getTriggerHistory(id: string, limit: number = 10): Promise<TriggerHistoryItem[]> {
    const trigger = this.database[id]
    if (!trigger) {
      return []
    }

    return trigger.history.slice(-limit)
  }

  async getEnabledTriggers(): Promise<TriggerConfig[]> {
    return Object.values(this.database)
      .filter(t => t.config.enabled)
      .map(t => t.config)
  }

  async getTriggersByType(type: 'schedule' | 'notification'): Promise<TriggerConfig[]> {
    return Object.values(this.database)
      .filter(t => t.config.type === type)
      .map(t => t.config)
  }

  async getTriggersByPriority(): Promise<TriggerConfig[]> {
    return Object.values(this.database)
      .sort((a, b) => b.config.priority - a.config.priority)
      .map(t => t.config)
  }

  onTriggerExecution(callback: (event: TriggerExecutionEvent) => void): () => void {
    const listenerId = Math.random().toString(36).substring(7)
    this.listeners.set(listenerId, callback)

    return () => {
      this.listeners.delete(listenerId)
    }
  }

  private notifyTriggerExecution(event: TriggerExecutionEvent): void {
    this.listeners.forEach(callback => callback(event))
  }

  recordExecution(id: string, success: boolean, error?: string, result?: any): void {
    const trigger = this.database[id]
    if (!trigger) {
      return
    }

    const historyItem: TriggerHistoryItem = {
      triggerId: id,
      executedAt: Date.now(),
      success,
      error,
      result
    }

    trigger.history.push(historyItem)
    trigger.status.executionCount++
    trigger.status.lastExecuted = Date.now()

    if (error) {
      trigger.status.lastError = error
    }

    // Keep only last 100 history items
    if (trigger.history.length > 100) {
      trigger.history = trigger.history.slice(-100)
    }
  }

  private validateTriggerConfig(config: TriggerConfig): void {
    if (!config.id || config.id.trim().length === 0) {
      throw new Error('Trigger ID is required')
    }

    if (!config.task || config.task.trim().length === 0) {
      throw new Error('Task is required')
    }

    if (!config.agentConfig || !config.agentConfig.apiKey) {
      throw new Error('Agent config with API key is required')
    }

    if (config.priority < 0 || config.priority > 100) {
      throw new Error('Priority must be between 0 and 100')
    }

    if (config.type === 'schedule') {
      ScheduleTrigger.validateSchedule((config as ScheduleTriggerConfig).schedule)
    } else if (config.type === 'notification') {
      NotificationTrigger.validateNotificationConfig(
        (config as NotificationTriggerConfig).notificationConfig
      )
    }
  }

  // Helper method to generate unique trigger ID
  static generateTriggerId(): string {
    return `trigger_${Date.now()}_${Math.random().toString(36).substring(7)}`
  }

  // Clear all triggers (useful for testing)
  async clearAllTriggers(): Promise<void> {
    const triggerIds = Object.keys(this.database)
    for (const id of triggerIds) {
      await this.deleteTrigger(id)
    }
  }
}
