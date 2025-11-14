/**
 * Tests for ClarificationAgent
 */

import { ClarificationAgent } from '../ClarificationAgent';
import { GeminiClient } from '../../llm/GeminiClient';

// Mock GeminiClient
jest.mock('../../llm/GeminiClient');

describe('ClarificationAgent', () => {
  let mockLLMClient: jest.Mocked<GeminiClient>;
  let clarificationAgent: ClarificationAgent;

  beforeEach(() => {
    mockLLMClient = {
      generateAgentOutput: jest.fn(),
    } as any;

    clarificationAgent = new ClarificationAgent(mockLLMClient, {
      sensitivity: 'medium',
      maxQuestions: 5,
      skipSimpleTasks: true,
    });
  });

  describe('analyzeInstruction', () => {
    it('should skip analysis for simple tasks', async () => {
      const result = await clarificationAgent.analyzeInstruction('open settings');

      expect(result.hasAmbiguity).toBe(false);
      expect(result.confidence).toBe(1.0);
      expect(mockLLMClient.generateAgentOutput).not.toHaveBeenCalled();
    });

    it('should analyze complex instructions', async () => {
      mockLLMClient.generateAgentOutput.mockResolvedValue({
        evaluationPreviousGoal: '',
        memory: '',
        nextGoal: '',
        actions: [],
      });

      await clarificationAgent.analyzeInstruction('send a message to John');

      expect(mockLLMClient.generateAgentOutput).toHaveBeenCalled();
    });

    it('should handle LLM errors gracefully', async () => {
      mockLLMClient.generateAgentOutput.mockRejectedValue(new Error('API error'));

      const result = await clarificationAgent.analyzeInstruction('complex task');

      expect(result.hasAmbiguity).toBe(false);
      expect(result.confidence).toBe(0.0);
    });
  });

  describe('generateQuestions', () => {
    it('should return empty array for no ambiguity', async () => {
      const analysis = {
        hasAmbiguity: false,
        ambiguousElements: [],
        confidence: 1.0,
      };

      const questions = await clarificationAgent.generateQuestions(analysis);

      expect(questions).toEqual([]);
    });

    it('should generate fallback questions on LLM failure', async () => {
      mockLLMClient.generateAgentOutput.mockRejectedValue(new Error('API error'));

      const analysis = {
        hasAmbiguity: true,
        ambiguousElements: [
          {
            type: 'parameter' as const,
            description: 'Missing message content',
            suggestedQuestions: ['What message?', 'What text?'],
          },
        ],
        confidence: 0.8,
      };

      const questions = await clarificationAgent.generateQuestions(analysis);

      expect(questions.length).toBeGreaterThan(0);
      expect(questions[0].type).toBe('text');
    });
  });

  describe('enhanceInstruction', () => {
    it('should return original instruction with no responses', async () => {
      const result = await clarificationAgent.enhanceInstruction('original task', []);

      expect(result).toBe('original task');
    });

    it('should create fallback enhancement on LLM failure', async () => {
      mockLLMClient.generateAgentOutput.mockRejectedValue(new Error('API error'));

      const responses = [
        { questionId: 'q1', answer: 'John Smith' },
        { questionId: 'q2', answer: 'Hello' },
      ];

      const result = await clarificationAgent.enhanceInstruction('send message', responses);

      expect(result).toContain('send message');
      expect(result).toContain('John Smith');
      expect(result).toContain('Hello');
    });
  });
});
