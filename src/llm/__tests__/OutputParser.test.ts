/**
 * Unit tests for OutputParser
 * Tests JSON parsing and validation of LLM responses
 */

import { parseAgentOutput, extractJsonFromMarkdown } from '../OutputParser'

describe('OutputParser', () => {
  describe('parseAgentOutput()', () => {
    it('should parse valid AgentOutput JSON', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal was achieved',
        memory: 'Remembered something',
        nextGoal: 'Next step to take',
        actions: [
          { type: 'tap_element', elementId: 0 },
        ],
      })

      const result = parseAgentOutput(json)

      expect(result).not.toBeNull()
      expect(result?.evaluationPreviousGoal).toBe('Goal was achieved')
      expect(result?.memory).toBe('Remembered something')
      expect(result?.nextGoal).toBe('Next step to take')
      expect(result?.actions).toHaveLength(1)
    })

    it('should return null for invalid JSON', () => {
      const result = parseAgentOutput('not valid json')

      expect(result).toBeNull()
    })

    it('should return null if missing required fields', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        // missing memory, nextGoal, actions
      })

      const result = parseAgentOutput(json)

      expect(result).toBeNull()
    })

    it('should return null if actions is not an array', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: 'not an array',
      })

      const result = parseAgentOutput(json)

      expect(result).toBeNull()
    })

    it('should validate action types', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'invalid_action', param: 'value' },
        ],
      })

      const result = parseAgentOutput(json)

      expect(result).toBeNull()
    })

    it('should validate tap_element action parameters', () => {
      const validJson = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'tap_element', elementId: 5 },
        ],
      })

      const result = parseAgentOutput(validJson)
      expect(result).not.toBeNull()

      const invalidJson = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'tap_element' }, // missing elementId
        ],
      })

      const invalidResult = parseAgentOutput(invalidJson)
      expect(invalidResult).toBeNull()
    })

    it('should validate type action parameters', () => {
      const validJson = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'type', text: 'Hello' },
        ],
      })

      const result = parseAgentOutput(validJson)
      expect(result).not.toBeNull()

      const invalidJson = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'type' }, // missing text
        ],
      })

      const invalidResult = parseAgentOutput(invalidJson)
      expect(invalidResult).toBeNull()
    })

    it('should validate swipe action parameters', () => {
      const validJson = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'swipe_down', amount: 500 },
        ],
      })

      const result = parseAgentOutput(validJson)
      expect(result).not.toBeNull()
    })

    it('should validate done action parameters', () => {
      const validJson = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'done', success: true, text: 'Completed', filesToDisplay: ['result.md'] },
        ],
      })

      const result = parseAgentOutput(validJson)
      expect(result).not.toBeNull()

      const invalidJson = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'done', success: 'yes', text: 'Completed' }, // success should be boolean
        ],
      })

      const invalidResult = parseAgentOutput(invalidJson)
      expect(invalidResult).toBeNull()
    })

    it('should validate actions without parameters', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'back' },
          { type: 'home' },
          { type: 'wait' },
        ],
      })

      const result = parseAgentOutput(json)
      expect(result).not.toBeNull()
      expect(result?.actions).toHaveLength(3)
    })

    it('should validate file system actions', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'write_file', fileName: 'test.md', content: 'content' },
          { type: 'read_file', fileName: 'test.md' },
        ],
      })

      const result = parseAgentOutput(json)
      expect(result).not.toBeNull()
      expect(result?.actions).toHaveLength(2)
    })

    it('should validate voice actions', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'speak', message: 'Hello' },
          { type: 'ask', question: 'What is your name?' },
        ],
      })

      const result = parseAgentOutput(json)
      expect(result).not.toBeNull()
    })

    it('should validate launch_intent action', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'launch_intent', intentName: 'camera', parameters: { mode: 'photo' } },
        ],
      })

      const result = parseAgentOutput(json)
      expect(result).not.toBeNull()
    })

    it('should handle multiple actions', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [
          { type: 'tap_element', elementId: 0 },
          { type: 'type', text: 'Hello' },
          { type: 'back' },
        ],
      })

      const result = parseAgentOutput(json)
      expect(result).not.toBeNull()
      expect(result?.actions).toHaveLength(3)
    })

    it('should handle empty actions array', () => {
      const json = JSON.stringify({
        evaluationPreviousGoal: 'Goal',
        memory: 'Memory',
        nextGoal: 'Next',
        actions: [],
      })

      const result = parseAgentOutput(json)
      expect(result).not.toBeNull()
      expect(result?.actions).toHaveLength(0)
    })
  })

  describe('extractJsonFromMarkdown()', () => {
    it('should extract JSON from code block', () => {
      const markdown = '```json\n{"key": "value"}\n```'

      const result = extractJsonFromMarkdown(markdown)

      expect(result).toBe('{"key": "value"}')
    })

    it('should extract JSON from code block without language', () => {
      const markdown = '```\n{"key": "value"}\n```'

      const result = extractJsonFromMarkdown(markdown)

      expect(result).toBe('{"key": "value"}')
    })

    it('should return original text if no code block', () => {
      const text = '{"key": "value"}'

      const result = extractJsonFromMarkdown(text)

      expect(result).toBe('{"key": "value"}')
    })

    it('should handle multiline JSON in code block', () => {
      const markdown = '```json\n{\n  "key": "value",\n  "number": 42\n}\n```'

      const result = extractJsonFromMarkdown(markdown)

      expect(result).toContain('"key": "value"')
      expect(result).toContain('"number": 42')
    })

    it('should trim whitespace', () => {
      const markdown = '  \n```json\n{"key": "value"}\n```\n  '

      const result = extractJsonFromMarkdown(markdown)

      expect(result).toBe('{"key": "value"}')
    })
  })
})
