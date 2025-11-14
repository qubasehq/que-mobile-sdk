/**
 * Tests for DialogueManager
 */

import { DialogueManager } from '../DialogueManager';
import { ClarificationAgent } from '../ClarificationAgent';

// Mock ClarificationAgent
jest.mock('../ClarificationAgent');

describe('DialogueManager', () => {
  let mockClarificationAgent: jest.Mocked<ClarificationAgent>;
  let dialogueManager: DialogueManager;

  beforeEach(() => {
    mockClarificationAgent = {
      analyzeInstruction: jest.fn(),
      generateQuestions: jest.fn(),
      enhanceInstruction: jest.fn(),
    } as any;

    dialogueManager = new DialogueManager(mockClarificationAgent);
  });

  describe('startDialogue', () => {
    it('should create dialogue with no ambiguity', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: false,
        ambiguousElements: [],
        confidence: 1.0,
      });

      const state = await dialogueManager.startDialogue('open settings');

      expect(state.isComplete).toBe(true);
      expect(state.questions).toEqual([]);
      expect(state.enhancedInstruction).toBe('open settings');
    });

    it('should create dialogue with questions', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: true,
        ambiguousElements: [
          {
            type: 'parameter',
            description: 'Missing info',
            suggestedQuestions: ['What?'],
          },
        ],
        confidence: 0.8,
      });

      mockClarificationAgent.generateQuestions.mockResolvedValue([
        {
          id: 'q1',
          question: 'What message?',
          type: 'text',
          required: true,
        },
      ]);

      const state = await dialogueManager.startDialogue('send message');

      expect(state.isComplete).toBe(false);
      expect(state.questions.length).toBe(1);
      expect(state.currentQuestionIndex).toBe(0);
    });
  });

  describe('answerQuestion', () => {
    it('should advance to next question', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: true,
        ambiguousElements: [],
        confidence: 0.8,
      });

      mockClarificationAgent.generateQuestions.mockResolvedValue([
        { id: 'q1', question: 'Q1?', type: 'text', required: true },
        { id: 'q2', question: 'Q2?', type: 'text', required: true },
      ]);

      const state = await dialogueManager.startDialogue('task');
      const updatedState = await dialogueManager.answerQuestion(state.sessionId, {
        questionId: 'q1',
        answer: 'Answer 1',
      });

      expect(updatedState.currentQuestionIndex).toBe(1);
      expect(updatedState.responses.length).toBe(1);
      expect(updatedState.isComplete).toBe(false);
    });

    it('should mark as complete after last question', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: true,
        ambiguousElements: [],
        confidence: 0.8,
      });

      mockClarificationAgent.generateQuestions.mockResolvedValue([
        { id: 'q1', question: 'Q1?', type: 'text', required: true },
      ]);

      const state = await dialogueManager.startDialogue('task');
      const updatedState = await dialogueManager.answerQuestion(state.sessionId, {
        questionId: 'q1',
        answer: 'Answer 1',
      });

      expect(updatedState.isComplete).toBe(true);
    });

    it('should throw error for invalid session', async () => {
      await expect(
        dialogueManager.answerQuestion('invalid', { questionId: 'q1', answer: 'A' })
      ).rejects.toThrow('Dialogue session not found');
    });
  });

  describe('getNextQuestion', () => {
    it('should return current question', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: true,
        ambiguousElements: [],
        confidence: 0.8,
      });

      mockClarificationAgent.generateQuestions.mockResolvedValue([
        { id: 'q1', question: 'Q1?', type: 'text', required: true },
      ]);

      const state = await dialogueManager.startDialogue('task');
      const question = await dialogueManager.getNextQuestion(state.sessionId);

      expect(question).not.toBeNull();
      expect(question?.id).toBe('q1');
    });

    it('should return null when complete', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: false,
        ambiguousElements: [],
        confidence: 1.0,
      });

      const state = await dialogueManager.startDialogue('task');
      const question = await dialogueManager.getNextQuestion(state.sessionId);

      expect(question).toBeNull();
    });
  });

  describe('completeDialogue', () => {
    it('should generate enhanced instruction', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: true,
        ambiguousElements: [],
        confidence: 0.8,
      });

      mockClarificationAgent.generateQuestions.mockResolvedValue([
        { id: 'q1', question: 'Q1?', type: 'text', required: true },
      ]);

      mockClarificationAgent.enhanceInstruction.mockResolvedValue('enhanced task');

      const state = await dialogueManager.startDialogue('task');
      await dialogueManager.answerQuestion(state.sessionId, {
        questionId: 'q1',
        answer: 'Answer',
      });

      const enhanced = await dialogueManager.completeDialogue(state.sessionId);

      expect(enhanced).toBe('enhanced task');
      expect(mockClarificationAgent.enhanceInstruction).toHaveBeenCalled();
    });

    it('should throw error if not complete', async () => {
      mockClarificationAgent.analyzeInstruction.mockResolvedValue({
        hasAmbiguity: true,
        ambiguousElements: [],
        confidence: 0.8,
      });

      mockClarificationAgent.generateQuestions.mockResolvedValue([
        { id: 'q1', question: 'Q1?', type: 'text', required: true },
      ]);

      const state = await dialogueManager.startDialogue('task');

      await expect(dialogueManager.completeDialogue(state.sessionId)).rejects.toThrow(
        'not complete'
      );
    });
  });
});
