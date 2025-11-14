import { AgentConfig } from '../types'

export interface BaseTriggerConfig {
  id: string
  type: 'schedule' | 'notification'
  enabled: boolean
  priority: number
  task: string
  agentConfig: AgentConfig
}

export interface ScheduleConfig {
  time: string // Format: "HH:mm" (24-hour)
  daysOfWeek: number[] // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
  timezone?: string
}

export interface ScheduleTriggerConfig extends BaseTriggerConfig {
  type: 'schedule'
  schedule: ScheduleConfig
}

export interface NotificationConfig {
  packageName: string
  titlePattern?: string
  textPattern?: string
}

export interface NotificationTriggerConfig extends BaseTriggerConfig {
  type: 'notification'
  notificationConfig: NotificationConfig
}

export type TriggerConfig = ScheduleTriggerConfig | NotificationTriggerConfig

export interface TriggerStatus {
  id: string
  enabled: boolean
  lastExecuted?: number
  nextExecution?: number
  executionCount: number
  lastError?: string
}

export interface TriggerExecutionEvent {
  triggerId: string
  triggerType: 'schedule' | 'notification'
  timestamp: number
  metadata?: {
    notificationPackage?: string
    notificationTitle?: string
    notificationText?: string
  }
}

export interface TriggerHistoryItem {
  triggerId: string
  executedAt: number
  success: boolean
  error?: string
  result?: any
}
