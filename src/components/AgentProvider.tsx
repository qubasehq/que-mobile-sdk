/**
 * AgentProvider - React Context Provider for Agent Configuration
 * 
 * Provides agent configuration to child components and shares agent instance
 * across the component tree. This allows multiple components to access the
 * same agent configuration without prop drilling.
 * 
 * Requirements: 1.1
 */

import React, { createContext, useContext, ReactNode } from 'react'
import { AgentConfig } from '../types'

// ============================================================================
// Context Type
// ============================================================================

export interface AgentContextValue {
  /** Agent configuration */
  config: AgentConfig
}

// ============================================================================
// Context Creation
// ============================================================================

const AgentContext = createContext<AgentContextValue | null>(null)

// ============================================================================
// Provider Props
// ============================================================================

export interface AgentProviderProps {
  /** Agent configuration to provide to children */
  config: AgentConfig
  /** Child components */
  children: ReactNode
}

// ============================================================================
// AgentProvider Component
// ============================================================================

/**
 * Provider component that makes agent configuration available to all child components
 * 
 * @example
 * ```tsx
 * <AgentProvider config={{ apiKey: 'YOUR_KEY', maxSteps: 50 }}>
 *   <App />
 * </AgentProvider>
 * ```
 */
export function AgentProvider({ config, children }: AgentProviderProps): React.ReactElement {
  const contextValue: AgentContextValue = {
    config,
  }

  return (
    <AgentContext.Provider value={contextValue}>
      {children}
    </AgentContext.Provider>
  )
}

// ============================================================================
// useAgentContext Hook
// ============================================================================

/**
 * Hook to access agent configuration from context
 * Must be used within an AgentProvider
 * 
 * @throws Error if used outside of AgentProvider
 * @returns Agent context value with configuration
 * 
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { config } = useAgentContext()
 *   return <Text>Max Steps: {config.maxSteps}</Text>
 * }
 * ```
 */
export function useAgentContext(): AgentContextValue {
  const context = useContext(AgentContext)
  
  if (!context) {
    throw new Error('useAgentContext must be used within an AgentProvider')
  }
  
  return context
}
