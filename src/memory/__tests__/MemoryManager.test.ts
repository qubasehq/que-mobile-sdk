/**
 * Unit tests for MemoryManager
 * Tests history management, prompt building, and truncation
 */

import { MemoryManager } from '../MemoryManager'
import { FileSystem } from '../FileSystem'
import { AgentOutput, ActionResult, AgentSettings } from '../../types'

// Mock FileSystem
jest.mock('../FileSystem')

describe('MemoryManager', () => {
  let memoryManager: MemoryManager
  let mockFileSystem: jest.Mocked<FileSystem>
  let mockSettings: AgentSettings

  beforeEach(() => {
    mockFileSystem = new FileSystem() as jest.Mocked<FileSystem>
    mockSettings = {
      maxHistoryItems: 20,
      stepDelay: 1000,
      maxLLMRetries: 3,
      actionTimeout: 30000,
    }
    memoryManager = new MemoryManager(mockFileSystem, mockSettings)
  })

  describe('addNewTask()', () => {
    it('should initialize new task and reset history', () => {
      memoryManager.addNewTask('Test task')

      expect(memoryManager.getCurrentTask()).toBe('Test task')
      expect(memoryManager.getAgentHistory()).toHaveLength(0)
    })

    it('should create system message on new task', () => {
      memoryManager.addNewTask('Test task')

      const messages = memoryManager.getMessages()
      expect(messages.length).toBeGreaterThan(0)
      expect(messages[0].role).toBe('system')
    })
  })

  describe('updateHistory()', () => {
    beforeEach(() => {
      memoryManager.addNewTask('Test task')
    })

    it('should add history item with model output', () => {
      const modelOutput: AgentOutput = {
        evaluationPreviousGoal: 'Goal achieved',
        memory: 'Remembered something',
        nextGoal: 'Next step',
        actions: [],
      }

      memoryManager.updateHistory(1, modelOutput, null)

      const history = memoryManager.getAgentHistory()
      expect(history).toHaveLength(1)
      expect(history[0].stepNumber).toBe(1)
      expect(history[0].evaluation).toBe('Goal achieved')
      expect(history[0].memory).toBe('Remembered something')
      expect(history[0].nextGoal).toBe('Next step')
    })

    it('should add history item with action results', () => {
      const results: ActionResult[] = [
        { longTermMemory: 'Action completed' },
      ]

      memoryManager.updateHistory(1, null, results)

      const history = memoryManager.getAgentHistory()
      expect(history).toHaveLength(1)
      expect(history[0].actionResults).toContain('Action 1: Success')
    })

    it('should add history item with error', () => {
      memoryManager.updateHistory(1, null, null, 'Something went wrong')

      const history = memoryManager.getAgentHistory()
      expect(history).toHaveLength(1)
      expect(history[0].error).toBe('Something went wrong')
    })

    it('should handle extracted content with one-time flag', () => {
      const results: ActionResult[] = [
        {
          extractedContent: 'File content here',
          includeExtractedContentOnlyOnce: true,
        },
      ]

      memoryManager.updateHistory(1, null, results)

      // Content should be added to read state (not directly testable, but history should be updated)
      const history = memoryManager.getAgentHistory()
      expect(history).toHaveLength(1)
    })

    it('should truncate history when exceeding max items', () => {
      // Set low max for testing
      const smallSettings: AgentSettings = {
        ...mockSettings,
        maxHistoryItems: 3,
      }
      const smallMemoryManager = new MemoryManager(mockFileSystem, smallSettings)
      smallMemoryManager.addNewTask('Test')

      // Add more items than max
      for (let i = 0; i < 5; i++) {
        smallMemoryManager.updateHistory(i, null, null)
      }

      const history = smallMemoryManager.getAgentHistory()
      expect(history).toHaveLength(3)
      expect(history[0].stepNumber).toBe(2) // Should keep most recent
    })
  })

  describe('addContextMessage()', () => {
    beforeEach(() => {
      memoryManager.addNewTask('Test task')
    })

    it('should add context message to messages', () => {
      memoryManager.addContextMessage('Error occurred')

      const messages = memoryManager.getMessages()
      const contextMessages = messages.filter(m => m.role === 'user')
      expect(contextMessages.length).toBeGreaterThan(0)
    })
  })

  describe('clearContextMessages()', () => {
    beforeEach(() => {
      memoryManager.addNewTask('Test task')
    })

    it('should clear all context messages', () => {
      memoryManager.addContextMessage('Message 1')
      memoryManager.addContextMessage('Message 2')

      memoryManager.clearContextMessages()

      const messages = memoryManager.getMessages()
      // Should only have system message, no context messages
      expect(messages.filter(m => m.role === 'user')).toHaveLength(0)
    })
  })

  describe('getMessages()', () => {
    beforeEach(() => {
      memoryManager.addNewTask('Test task')
    })

    it('should return messages in correct order', () => {
      const messages = memoryManager.getMessages()

      // Should have at least system message
      expect(messages.length).toBeGreaterThan(0)
      expect(messages[0].role).toBe('system')
    })

    it('should include context messages', () => {
      memoryManager.addContextMessage('Context 1')
      memoryManager.addContextMessage('Context 2')

      const messages = memoryManager.getMessages()
      const contextMessages = messages.filter(m => m.role === 'user')
      expect(contextMessages.length).toBeGreaterThan(0)
    })
  })

  describe('addSystemMessageToHistory()', () => {
    beforeEach(() => {
      memoryManager.addNewTask('Test task')
    })

    it('should add system message to history', () => {
      memoryManager.addSystemMessageToHistory(1, 'System notification')

      const history = memoryManager.getAgentHistory()
      expect(history).toHaveLength(1)
      expect(history[0].systemMessage).toBe('System notification')
    })
  })

  describe('addToReadState()', () => {
    beforeEach(() => {
      memoryManager.addNewTask('Test task')
    })

    it('should add content to read state', () => {
      memoryManager.addToReadState('First content')
      memoryManager.addToReadState('Second content')

      // Read state is internal, but we can verify it doesn't throw
      expect(() => memoryManager.getMemoryState()).not.toThrow()
    })
  })

  describe('getFileSystem()', () => {
    it('should return file system instance', () => {
      expect(memoryManager.getFileSystem()).toBe(mockFileSystem)
    })
  })

  describe('getCurrentTask()', () => {
    it('should return current task', () => {
      memoryManager.addNewTask('My task')
      expect(memoryManager.getCurrentTask()).toBe('My task')
    })

    it('should return empty string before task is set', () => {
      expect(memoryManager.getCurrentTask()).toBe('')
    })
  })
})
