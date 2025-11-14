/**
 * Unit tests for GeminiClient
 * Tests LLM communication and response parsing
 */

import { GeminiClient } from '../GeminiClient'
import { LLMMessage } from '../../types'

// Mock @google/generative-ai
jest.mock('@google/generative-ai', () => ({
  GoogleGenerativeAI: jest.fn().mockImplementation(() => ({
    getGenerativeModel: jest.fn().mockReturnValue({
      generateContent: jest.fn(),
    }),
  })),
}))

describe('GeminiClient', () => {
  let client: GeminiClient
  const mockApiKey = 'test-api-key'

  beforeEach(() => {
    jest.clearAllMocks()
    client = new GeminiClient({ apiKey: mockApiKey })
  })

  describe('constructor', () => {
    it('should initialize with API key', () => {
      expect(() => new GeminiClient({ apiKey: mockApiKey })).not.toThrow()
    })

    it('should accept custom model', () => {
      expect(() => new GeminiClient({ apiKey: mockApiKey, model: 'gemini-pro' })).not.toThrow()
    })
  })

  describe('generateAgentOutput()', () => {
    it('should parse valid JSON response', async () => {
      const mockResponse = {
        response: {
          text: () => JSON.stringify({
            evaluationPreviousGoal: 'Goal achieved',
            memory: 'Remembered',
            nextGoal: 'Next step',
            actions: [{ type: 'tap_element', elementId: 0 }],
          }),
        },
      }

      const mockModel = {
        generateContent: jest.fn().mockResolvedValue(mockResponse),
      }

      // Access the private model through any
      ;(client as any).model = mockModel

      const messages: LLMMessage[] = [
        { role: 'system', content: 'System prompt' },
        { role: 'user', content: 'User message' },
      ]

      const result = await client.generateAgentOutput(messages)

      expect(result).not.toBeNull()
      expect(result?.evaluationPreviousGoal).toBe('Goal achieved')
      expect(result?.actions).toHaveLength(1)
    })

    it('should throw error for invalid JSON after retries', async () => {
      const mockResponse = {
        response: {
          text: () => 'not valid json',
        },
      }

      const mockModel = {
        generateContent: jest.fn().mockResolvedValue(mockResponse),
      }

      ;(client as any).model = mockModel

      const messages: LLMMessage[] = [
        { role: 'user', content: 'Test' },
      ]

      await expect(client.generateAgentOutput(messages)).rejects.toThrow('Failed to generate valid output')
    })

    it('should extract JSON from markdown code blocks', async () => {
      const mockResponse = {
        response: {
          text: () => '```json\n' + JSON.stringify({
            evaluationPreviousGoal: 'Goal',
            memory: 'Memory',
            nextGoal: 'Next',
            actions: [],
          }) + '\n```',
        },
      }

      const mockModel = {
        generateContent: jest.fn().mockResolvedValue(mockResponse),
      }

      ;(client as any).model = mockModel

      const messages: LLMMessage[] = [
        { role: 'user', content: 'Test' },
      ]

      const result = await client.generateAgentOutput(messages)

      expect(result).not.toBeNull()
      expect(result?.evaluationPreviousGoal).toBe('Goal')
    })

    it('should throw error for API errors', async () => {
      const mockModel = {
        generateContent: jest.fn().mockRejectedValue(new Error('API Error')),
      }

      ;(client as any).model = mockModel

      const messages: LLMMessage[] = [
        { role: 'user', content: 'Test' },
      ]

      await expect(client.generateAgentOutput(messages)).rejects.toThrow('Gemini API error')
    })

    it('should convert messages to correct format', async () => {
      const mockResponse = {
        response: {
          text: () => JSON.stringify({
            evaluationPreviousGoal: 'Goal',
            memory: 'Memory',
            nextGoal: 'Next',
            actions: [],
          }),
        },
      }

      const mockModel = {
        generateContent: jest.fn().mockResolvedValue(mockResponse),
      }

      ;(client as any).model = mockModel

      const messages: LLMMessage[] = [
        { role: 'system', content: 'System prompt' },
        { role: 'user', content: 'User message' },
      ]

      await client.generateAgentOutput(messages)

      expect(mockModel.generateContent).toHaveBeenCalled()
    })
  })
})
