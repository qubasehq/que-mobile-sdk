/**
 * Agent tests
 * 
 * Tests the core SENSE → THINK → ACT loop with mocked subsystems
 */

import { Agent } from '../Agent'
import { AgentConfig } from '../../types'

// Mock all subsystems
jest.mock('../../native/AccessibilityModule')
jest.mock('../../memory/FileSystem')
jest.mock('../../memory/MemoryManager')
jest.mock('../../perception/Perception')
jest.mock('../../llm/GeminiClient')
jest.mock('../../actions/ActionExecutor')
jest.mock('../../voice/VoiceManager')

describe('Agent', () => {
  let agent: Agent
  let mockConfig: AgentConfig

  beforeEach(() => {
    jest.clearAllMocks()
    
    mockConfig = {
      apiKey: 'test-api-key',
      maxSteps: 10,
      maxFailures: 3,
      debugMode: false,
    }
  })

  describe('initialization', () => {
    it('should create Agent instance with config', () => {
      agent = new Agent(mockConfig)
      expect(agent).toBeInstanceOf(Agent)
    })

    it('should initialize with default settings', () => {
      agent = new Agent({ apiKey: 'test-key' })
      expect(agent).toBeInstanceOf(Agent)
    })
  })

  describe('getState', () => {
    it('should return current agent state', () => {
      agent = new Agent(mockConfig)
      const state = agent.getState()
      
      expect(state).toHaveProperty('nSteps')
      expect(state).toHaveProperty('stopped')
      expect(state).toHaveProperty('consecutiveFailures')
      expect(state).toHaveProperty('lastModelOutput')
      expect(state).toHaveProperty('lastResult')
      
      expect(state.nSteps).toBe(0)
      expect(state.stopped).toBe(false)
      expect(state.consecutiveFailures).toBe(0)
    })
  })

  describe('getHistory', () => {
    it('should return empty history initially', () => {
      agent = new Agent(mockConfig)
      const history = agent.getHistory()
      
      expect(Array.isArray(history)).toBe(true)
      expect(history.length).toBe(0)
    })
  })

  describe('stop', () => {
    it('should set stopped flag', () => {
      agent = new Agent(mockConfig)
      
      expect(agent.getState().stopped).toBe(false)
      
      agent.stop()
      
      expect(agent.getState().stopped).toBe(true)
    })
  })

  describe('getCurrentTask', () => {
    it('should return empty string initially', () => {
      agent = new Agent(mockConfig)
      expect(agent.getCurrentTask()).toBe('')
    })
  })

  describe('getFileSystem', () => {
    it('should return FileSystem instance', () => {
      agent = new Agent(mockConfig)
      const fileSystem = agent.getFileSystem()
      expect(fileSystem).toBeDefined()
    })
  })

  describe('getMemoryManager', () => {
    it('should return MemoryManager instance', () => {
      agent = new Agent(mockConfig)
      const memoryManager = agent.getMemoryManager()
      expect(memoryManager).toBeDefined()
    })
  })

  // Note: Full integration tests for run() would require extensive mocking
  // of all subsystems. These tests verify the Agent class structure and
  // basic functionality. Integration tests should be done in a separate
  // test suite with proper mocks for each subsystem.
})

