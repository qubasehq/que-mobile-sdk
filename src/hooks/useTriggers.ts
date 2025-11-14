import { useState, useEffect, useCallback, useRef } from 'react'
import {
  TriggerManager,
  TriggerExecutor,
  TriggerConfig,
  TriggerStatus,
  TriggerHistoryItem
} from '../triggers'
import { AgentResult } from '../types'

export interface UseTriggersConfig {
  autoInitialize?: boolean
  onExecutionStart?: (triggerId: string) => void
  onExecutionComplete?: (triggerId: string, result: AgentResult) => void
  onExecutionError?: (triggerId: string, error: Error) => void
}

export interface UseTriggers {
  // Trigger CRUD operations
  createTrigger: (config: TriggerConfig) => Promise<string>
  updateTrigger: (id: string, updates: Partial<TriggerConfig>) => Promise<void>
  deleteTrigger: (id: string) => Promise<void>
  
  // Trigger control
  enableTrigger: (id: string) => Promise<void>
  disableTrigger: (id: string) => Promise<void>
  
  // Trigger queries
  getTrigger: (id: string) => Promise<TriggerConfig | null>
  listTriggers: () => Promise<TriggerConfig[]>
  getEnabledTriggers: () => Promise<TriggerConfig[]>
  getTriggersByType: (type: 'schedule' | 'notification') => Promise<TriggerConfig[]>
  
  // Status and history
  getTriggerStatus: (id: string) => Promise<TriggerStatus | null>
  getTriggerHistory: (id: string, limit?: number) => Promise<TriggerHistoryItem[]>
  
  // Manual execution
  executeTrigger: (id: string) => Promise<AgentResult | null>
  
  // State
  triggers: TriggerConfig[]
  isLoading: boolean
  error: string | null
  
  // Refresh
  refresh: () => Promise<void>
}

export function useTriggers(config: UseTriggersConfig = {}): UseTriggers {
  const [triggers, setTriggers] = useState<TriggerConfig[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const triggerManagerRef = useRef<TriggerManager | null>(null)
  const triggerExecutorRef = useRef<TriggerExecutor | null>(null)

  // Initialize trigger manager and executor
  useEffect(() => {
    if (config.autoInitialize !== false) {
      const manager = new TriggerManager()
      const executor = new TriggerExecutor({
        triggerManager: manager,
        onExecutionStart: config.onExecutionStart,
        onExecutionComplete: config.onExecutionComplete,
        onExecutionError: config.onExecutionError
      })

      triggerManagerRef.current = manager
      triggerExecutorRef.current = executor

      // Load initial triggers
      loadTriggers()

      return () => {
        manager.destroy()
      }
    }
    return undefined
  }, [config.autoInitialize])

  const loadTriggers = useCallback(async () => {
    if (!triggerManagerRef.current) return

    try {
      setIsLoading(true)
      setError(null)
      const allTriggers = await triggerManagerRef.current.listTriggers()
      setTriggers(allTriggers)
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      console.error('Error loading triggers:', err)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const createTrigger = useCallback(async (triggerConfig: TriggerConfig): Promise<string> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    try {
      setError(null)
      const id = await triggerManagerRef.current.createTrigger(triggerConfig)
      await loadTriggers()
      return id
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      throw err
    }
  }, [loadTriggers])

  const updateTrigger = useCallback(async (id: string, updates: Partial<TriggerConfig>): Promise<void> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    try {
      setError(null)
      await triggerManagerRef.current.updateTrigger(id, updates)
      await loadTriggers()
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      throw err
    }
  }, [loadTriggers])

  const deleteTrigger = useCallback(async (id: string): Promise<void> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    try {
      setError(null)
      await triggerManagerRef.current.deleteTrigger(id)
      await loadTriggers()
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      throw err
    }
  }, [loadTriggers])

  const enableTrigger = useCallback(async (id: string): Promise<void> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    try {
      setError(null)
      await triggerManagerRef.current.enableTrigger(id)
      await loadTriggers()
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      throw err
    }
  }, [loadTriggers])

  const disableTrigger = useCallback(async (id: string): Promise<void> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    try {
      setError(null)
      await triggerManagerRef.current.disableTrigger(id)
      await loadTriggers()
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      throw err
    }
  }, [loadTriggers])

  const getTrigger = useCallback(async (id: string): Promise<TriggerConfig | null> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    return await triggerManagerRef.current.getTrigger(id)
  }, [])

  const listTriggers = useCallback(async (): Promise<TriggerConfig[]> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    return await triggerManagerRef.current.listTriggers()
  }, [])

  const getEnabledTriggers = useCallback(async (): Promise<TriggerConfig[]> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    return await triggerManagerRef.current.getEnabledTriggers()
  }, [])

  const getTriggersByType = useCallback(async (type: 'schedule' | 'notification'): Promise<TriggerConfig[]> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    return await triggerManagerRef.current.getTriggersByType(type)
  }, [])

  const getTriggerStatus = useCallback(async (id: string): Promise<TriggerStatus | null> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    return await triggerManagerRef.current.getTriggerStatus(id)
  }, [])

  const getTriggerHistory = useCallback(async (id: string, limit: number = 10): Promise<TriggerHistoryItem[]> => {
    if (!triggerManagerRef.current) {
      throw new Error('Trigger manager not initialized')
    }

    return await triggerManagerRef.current.getTriggerHistory(id, limit)
  }, [])

  const executeTrigger = useCallback(async (id: string): Promise<AgentResult | null> => {
    if (!triggerExecutorRef.current) {
      throw new Error('Trigger executor not initialized')
    }

    try {
      setError(null)
      return await triggerExecutorRef.current.executeTrigger(id)
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      throw err
    }
  }, [])

  const refresh = useCallback(async (): Promise<void> => {
    await loadTriggers()
  }, [loadTriggers])

  return {
    createTrigger,
    updateTrigger,
    deleteTrigger,
    enableTrigger,
    disableTrigger,
    getTrigger,
    listTriggers,
    getEnabledTriggers,
    getTriggersByType,
    getTriggerStatus,
    getTriggerHistory,
    executeTrigger,
    triggers,
    isLoading,
    error,
    refresh
  }
}
