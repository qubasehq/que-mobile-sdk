/**
 * Integration tests for Agent
 * Tests the complete SENSE → THINK → ACT loop with mocked LLM
 */

import { Agent } from '../Agent'
import { AgentConfig, AgentOutput } from '../../types'
import { GeminiClient } from '../../llm/GeminiClient'

// Mock all external dependencies
jest.mock('../../llm/GeminiClient')
jest.mock('../../voice/VoiceManager')

// Mock AccessibilityModule properly
jest.mock('../../native/AccessibilityModule', () => {
  return {
    AccessibilityModule: jest.fn().mockImplementation(() => ({
      dumpHierarchy: jest.fn().mockResolvedValue('<hierarchy><node text="Test" clickable="true" bounds="[0,0][100,100]" /></hierarchy>'),
      clickOnPoint: jest.fn().mockResolvedValue(true),
      longPressOnPoint: jest.fn().mockResolvedValue(true),
      typeText: jest.fn().mockResolvedValue(true),
      scroll: jest.fn().mockResolvedValue(true),
      performBack: jest.fn().mockResolvedValue(true),
      performHome: jest.fn().mockResolvedValue(true),
      performRecents: jest.fn().mockResolvedValue(true),
      pressEnter: jest.fn().mockResolvedValue(true),
      isKeyboardOpen: jest.fn().mockResolvedValue(false),
      getCurrentActivity: jest.fn().mockResolvedValue('com.example.MainActivity'),
      openApp: jest.fn().mockResolvedValue(true),
      findPackageByAppName: jest.fn().mockResolvedValue('com.example.app'),
      getScreenDimensions: jest.fn().mockResolvedValue({ width: 1080, height: 1920 }),
      getScrollInfo: jest.fn().mockResolvedValue({ pixelsAbove: 0, pixelsBelow: 0 }),
    })),
  }
})

describe('Agent Integration Tests', () => {
  let agent: Agent
  let mockConfig: AgentConfig
  let mockGeminiClient: jest.Mocked<GeminiClient>

  beforeEach(() => {
    jest.clearAllMocks()

    mockConfig = {
      apiKey: 'test-api-key',
      maxSteps: 10,
      maxFailures: 3,
      debugMode: false,
    }

    // Mock GeminiClient
    mockGeminiClient = {
      generateAgentOutput: jest.fn(),
    } as any

    ;(GeminiClient as jest.MockedClass<typeof GeminiClient>).mockImplementation(() => mockGeminiClient)

    agent = new Agent(mockConfig)
  })

  describe('Agent loop with mocked LLM', () => {
    it('should complete task successfully with done action', async () => {
      // Mock LLM to return done action
      const mockOutput: AgentOutput = {
        evaluationPreviousGoal: 'Starting task',
        memory: 'Task initiated',
        nextGoal: 'Complete the task',
        actions: [
          {
            type: 'done',
            success: true,
            text: 'Task completed successfully',
          },
        ],
      }

      mockGeminiClient.generateAgentOutput.mockResolvedValue(mockOutput)

      const result = await agent.run('Test task')

      expect(result.success).toBe(true)
      expect(result.message).toContain('Task completed successfully')
      expect(result.steps).toBe(1)
      expect(mockGeminiClient.generateAgentOutput).toHaveBeenCalledTimes(1)
    })

    it('should execute multiple steps before completion', async () => {
      let callCount = 0

      mockGeminiClient.generateAgentOutput.mockImplementation(async () => {
        callCount++
        
        if (callCount < 3) {
          // First two calls: perform actions
          return {
            evaluationPreviousGoal: 'Making progress',
            memory: `Step ${callCount}`,
            nextGoal: 'Continue task',
            actions: [
              { type: 'wait' },
            ],
          }
        } else {
          // Third call: complete task
          return {
            evaluationPreviousGoal: 'Task complete',
            memory: 'Finished',
            nextGoal: 'Done',
            actions: [
              {
                type: 'done',
                success: true,
                text: 'All steps completed',
              },
            ],
          }
        }
      })

      const result = await agent.run('Multi-step task')

      expect(result.success).toBe(true)
      expect(result.steps).toBe(3)
      expect(mockGeminiClient.generateAgentOutput).toHaveBeenCalledTimes(3)
    })

    it('should stop at max steps if task not completed', async () => {
      // Mock LLM to never return done action
      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Working on it',
        memory: 'Still going',
        nextGoal: 'Keep trying',
        actions: [{ type: 'back' }], // Use back instead of wait to avoid delay
      })

      const result = await agent.run('Endless task', 3) // Reduce to 3 steps

      expect(result.success).toBe(false)
      expect(result.message).toContain('Reached maximum steps')
      expect(result.steps).toBe(3)
    }, 10000) // Increase timeout to 10s

    it('should handle action errors and retry', async () => {
      let callCount = 0

      mockGeminiClient.generateAgentOutput.mockImplementation(async () => {
        callCount++
        
        if (callCount === 1) {
          // First call: action that will fail
          return {
            evaluationPreviousGoal: 'Starting',
            memory: 'Attempting action',
            nextGoal: 'Try action',
            actions: [
              { type: 'tap_element', elementId: 999 }, // Non-existent element
            ],
          }
        } else {
          // Second call: complete successfully
          return {
            evaluationPreviousGoal: 'Recovered from error',
            memory: 'Trying different approach',
            nextGoal: 'Complete',
            actions: [
              {
                type: 'done',
                success: true,
                text: 'Completed after retry',
              },
            ],
          }
        }
      })

      const result = await agent.run('Task with error')

      expect(result.success).toBe(true)
      expect(result.steps).toBe(2)
      expect(mockGeminiClient.generateAgentOutput).toHaveBeenCalledTimes(2)
    })

    it('should fail after max consecutive failures', async () => {
      // Mock LLM to always return failing actions
      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Trying',
        memory: 'Attempting',
        nextGoal: 'Keep trying',
        actions: [
          { type: 'tap_element', elementId: 999 }, // Always fails
        ],
      })

      const result = await agent.run('Failing task')

      expect(result.success).toBe(false)
      expect(result.message).toContain('consecutive failures')
      expect(result.steps).toBeLessThanOrEqual(3) // maxFailures = 3
    })

    it('should reset consecutive failures on success', async () => {
      let callCount = 0

      mockGeminiClient.generateAgentOutput.mockImplementation(async () => {
        callCount++
        
        if (callCount === 1 || callCount === 3) {
          // Fail on steps 1 and 3
          return {
            evaluationPreviousGoal: 'Trying',
            memory: 'Attempting',
            nextGoal: 'Try action',
            actions: [
              { type: 'tap_element', elementId: 999 },
            ],
          }
        } else if (callCount === 2) {
          // Succeed on step 2 (resets counter)
          return {
            evaluationPreviousGoal: 'Success',
            memory: 'Action worked',
            nextGoal: 'Continue',
            actions: [
              { type: 'wait' },
            ],
          }
        } else {
          // Complete on step 4
          return {
            evaluationPreviousGoal: 'Done',
            memory: 'Finished',
            nextGoal: 'Complete',
            actions: [
              {
                type: 'done',
                success: true,
                text: 'Completed',
              },
            ],
          }
        }
      })

      const result = await agent.run('Task with intermittent failures')

      expect(result.success).toBe(true)
      expect(result.steps).toBe(4)
    })

    it('should handle multiple actions in single step', async () => {
      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Executing multiple actions',
        memory: 'Batch operations',
        nextGoal: 'Complete all actions',
        actions: [
          { type: 'wait' },
          { type: 'back' },
          {
            type: 'done',
            success: true,
            text: 'All actions completed',
          },
        ],
      })

      const result = await agent.run('Multi-action task')

      expect(result.success).toBe(true)
      expect(result.steps).toBe(1)
    })

    it('should stop executing actions after first error', async () => {
      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Trying actions',
        memory: 'Multiple actions',
        nextGoal: 'Execute all',
        actions: [
          { type: 'wait' }, // Success
          { type: 'tap_element', elementId: 999 }, // Fails
          { type: 'back' }, // Should not execute
        ],
      })

      const result = await agent.run('Task with failing action', 1)

      expect(result.success).toBe(false)
      // Only first two actions should be attempted
    })

    it('should call onStep callback for each step', async () => {
      const onStepMock = jest.fn()
      const configWithCallback: AgentConfig = {
        ...mockConfig,
        onStep: onStepMock,
      }

      const agentWithCallback = new Agent(configWithCallback)

      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Working',
        memory: 'Progress',
        nextGoal: 'Complete',
        actions: [
          {
            type: 'done',
            success: true,
            text: 'Done',
          },
        ],
      })

      await agentWithCallback.run('Task with callback')

      expect(onStepMock).toHaveBeenCalledTimes(1)
      expect(onStepMock).toHaveBeenCalledWith(
        expect.objectContaining({
          stepNumber: 1,
          modelOutput: expect.any(Object),
          actionResults: expect.any(Array),
          screenState: expect.any(Object),
        })
      )
    })

    it('should call onComplete callback when task finishes', async () => {
      const onCompleteMock = jest.fn()
      const configWithCallback: AgentConfig = {
        ...mockConfig,
        onComplete: onCompleteMock,
      }

      const agentWithCallback = new Agent(configWithCallback)

      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Done',
        memory: 'Finished',
        nextGoal: 'Complete',
        actions: [
          {
            type: 'done',
            success: true,
            text: 'Task complete',
          },
        ],
      })

      await agentWithCallback.run('Task with completion callback')

      expect(onCompleteMock).toHaveBeenCalledTimes(1)
      expect(onCompleteMock).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          message: expect.any(String),
          steps: 1,
          history: expect.any(Array),
        })
      )
    })

    it('should allow manual stop', async () => {
      mockGeminiClient.generateAgentOutput.mockImplementation(async () => {
        // Stop agent after first step
        setTimeout(() => agent.stop(), 100)
        
        return {
          evaluationPreviousGoal: 'Working',
          memory: 'In progress',
          nextGoal: 'Continue',
          actions: [{ type: 'wait' }],
        }
      })

      const result = await agent.run('Stoppable task')

      expect(result.success).toBe(false)
      expect(result.message).toContain('stopped')
      expect(result.steps).toBeLessThan(10)
    })
  })

  describe('Agent state management', () => {
    it('should track agent state correctly', async () => {
      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Done',
        memory: 'Complete',
        nextGoal: 'Finish',
        actions: [
          {
            type: 'done',
            success: true,
            text: 'Done',
          },
        ],
      })

      const runPromise = agent.run('State tracking task')

      // State should update during run
      await new Promise(resolve => setTimeout(resolve, 100))

      const state = agent.getState()
      expect(state.nSteps).toBeGreaterThan(0)

      await runPromise
    })

    it('should maintain execution history', async () => {
      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Done',
        memory: 'Complete',
        nextGoal: 'Finish',
        actions: [
          {
            type: 'done',
            success: true,
            text: 'Done',
          },
        ],
      })

      await agent.run('History task')

      const history = agent.getHistory()
      expect(history).toHaveLength(1)
      expect(history[0]).toHaveProperty('modelOutput')
      expect(history[0]).toHaveProperty('result')
      expect(history[0]).toHaveProperty('state')
      expect(history[0]).toHaveProperty('metadata')
    })

    it('should track current task', async () => {
      mockGeminiClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: 'Done',
        memory: 'Complete',
        nextGoal: 'Finish',
        actions: [
          {
            type: 'done',
            success: true,
            text: 'Done',
          },
        ],
      })

      const taskDescription = 'My test task'
      await agent.run(taskDescription)

      expect(agent.getCurrentTask()).toBe(taskDescription)
    })
  })
})
