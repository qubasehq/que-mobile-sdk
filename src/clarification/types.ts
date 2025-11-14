/**
 * Types for the Interactive Dialogue/Clarification System
 */

export interface AmbiguityAnalysis {
  hasAmbiguity: boolean;
  ambiguousElements: AmbiguousElement[];
  confidence: number;
}

export interface AmbiguousElement {
  type: 'parameter' | 'target' | 'condition' | 'sequence';
  description: string;
  suggestedQuestions: string[];
}

export interface ClarificationQuestion {
  id: string;
  question: string;
  type: 'choice' | 'text' | 'number';
  options?: string[];
  required: boolean;
}

export interface ClarificationResponse {
  questionId: string;
  answer: string;
}

export interface ClarificationConfig {
  sensitivity?: 'low' | 'medium' | 'high';
  maxQuestions?: number;
  skipSimpleTasks?: boolean;
}

export interface DialogueState {
  sessionId: string;
  originalInstruction: string;
  questions: ClarificationQuestion[];
  responses: ClarificationResponse[];
  currentQuestionIndex: number;
  isComplete: boolean;
  enhancedInstruction?: string;
}
