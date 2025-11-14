/**
 * DialogueManager - Manages multi-turn dialogue state for clarification
 */

import { ClarificationAgent } from './ClarificationAgent';
import {
  DialogueState,
  ClarificationQuestion,
  ClarificationResponse,
} from './types';

export class DialogueManager {
  private clarificationAgent: ClarificationAgent;
  private sessions: Map<string, DialogueState>;

  constructor(clarificationAgent: ClarificationAgent) {
    this.clarificationAgent = clarificationAgent;
    this.sessions = new Map();
  }

  /**
   * Start a new dialogue session for an instruction
   */
  async startDialogue(instruction: string): Promise<DialogueState> {
    // Generate unique session ID
    const sessionId = this.generateSessionId();

    // Analyze instruction for ambiguities
    const analysis = await this.clarificationAgent.analyzeInstruction(instruction);

    // If no ambiguity, return completed state immediately
    if (!analysis.hasAmbiguity) {
      const state: DialogueState = {
        sessionId,
        originalInstruction: instruction,
        questions: [],
        responses: [],
        currentQuestionIndex: 0,
        isComplete: true,
        enhancedInstruction: instruction,
      };

      this.sessions.set(sessionId, state);
      return state;
    }

    // Generate clarifying questions
    const questions = await this.clarificationAgent.generateQuestions(analysis);

    // Create initial dialogue state
    const state: DialogueState = {
      sessionId,
      originalInstruction: instruction,
      questions,
      responses: [],
      currentQuestionIndex: 0,
      isComplete: questions.length === 0,
      enhancedInstruction: questions.length === 0 ? instruction : undefined,
    };

    this.sessions.set(sessionId, state);
    return state;
  }

  /**
   * Answer the current question and advance dialogue
   */
  async answerQuestion(
    sessionId: string,
    response: ClarificationResponse
  ): Promise<DialogueState> {
    const state = this.sessions.get(sessionId);

    if (!state) {
      throw new Error(`Dialogue session not found: ${sessionId}`);
    }

    if (state.isComplete) {
      throw new Error('Dialogue is already complete');
    }

    // Validate response matches current question
    const currentQuestion = state.questions[state.currentQuestionIndex];
    if (currentQuestion && response.questionId !== currentQuestion.id) {
      throw new Error(
        `Response question ID ${response.questionId} does not match current question ${currentQuestion.id}`
      );
    }

    // Add response to state
    state.responses.push(response);

    // Advance to next question
    state.currentQuestionIndex++;

    // Check if dialogue is complete
    if (state.currentQuestionIndex >= state.questions.length) {
      state.isComplete = true;
    }

    this.sessions.set(sessionId, state);
    return state;
  }

  /**
   * Get the next question in the dialogue
   */
  async getNextQuestion(sessionId: string): Promise<ClarificationQuestion | null> {
    const state = this.sessions.get(sessionId);

    if (!state) {
      throw new Error(`Dialogue session not found: ${sessionId}`);
    }

    if (state.isComplete) {
      return null;
    }

    if (state.currentQuestionIndex >= state.questions.length) {
      return null;
    }

    return state.questions[state.currentQuestionIndex];
  }

  /**
   * Complete the dialogue and generate enhanced instruction
   */
  async completeDialogue(sessionId: string): Promise<string> {
    const state = this.sessions.get(sessionId);

    if (!state) {
      throw new Error(`Dialogue session not found: ${sessionId}`);
    }

    if (!state.isComplete) {
      throw new Error('Dialogue is not complete - answer all questions first');
    }

    // If already enhanced, return cached result
    if (state.enhancedInstruction) {
      return state.enhancedInstruction;
    }

    // Generate enhanced instruction from responses
    const enhancedInstruction = await this.clarificationAgent.enhanceInstruction(
      state.originalInstruction,
      state.responses
    );

    // Cache the result
    state.enhancedInstruction = enhancedInstruction;
    this.sessions.set(sessionId, state);

    return enhancedInstruction;
  }

  /**
   * Get the current state of a dialogue session
   */
  getDialogueState(sessionId: string): DialogueState | undefined {
    return this.sessions.get(sessionId);
  }

  /**
   * Check if a dialogue session exists
   */
  hasSession(sessionId: string): boolean {
    return this.sessions.has(sessionId);
  }

  /**
   * Delete a dialogue session
   */
  deleteSession(sessionId: string): boolean {
    return this.sessions.delete(sessionId);
  }

  /**
   * Clear all dialogue sessions
   */
  clearAllSessions(): void {
    this.sessions.clear();
  }

  /**
   * Get all active session IDs
   */
  getActiveSessions(): string[] {
    return Array.from(this.sessions.keys());
  }

  /**
   * Generate a unique session ID
   */
  private generateSessionId(): string {
    return `dialogue_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
}
