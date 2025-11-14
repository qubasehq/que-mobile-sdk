/**
 * useClarification - React hook for interactive dialogue/clarification
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { ClarificationAgent } from '../clarification/ClarificationAgent';
import { DialogueManager } from '../clarification/DialogueManager';
import { GeminiClient } from '../llm/GeminiClient';
import {
  DialogueState,
  ClarificationQuestion,
  ClarificationResponse,
  ClarificationConfig,
} from '../clarification/types';

export interface UseClarificationConfig extends ClarificationConfig {
  apiKey: string;
  model?: string;
}

export interface UseClarificationResult {
  // State
  isAnalyzing: boolean;
  isActive: boolean;
  currentQuestion: ClarificationQuestion | null;
  dialogueState: DialogueState | null;
  error: string | null;

  // Actions
  startDialogue: (instruction: string) => Promise<DialogueState>;
  answerQuestion: (answer: string) => Promise<void>;
  skipClarification: () => void;
  completeDialogue: () => Promise<string>;
  reset: () => void;

  // Helpers
  hasMoreQuestions: boolean;
  progress: { current: number; total: number };
}

export function useClarification(config: UseClarificationConfig): UseClarificationResult {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isActive, setIsActive] = useState(false);
  const [currentQuestion, setCurrentQuestion] = useState<ClarificationQuestion | null>(null);
  const [dialogueState, setDialogueState] = useState<DialogueState | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Create refs for managers (persist across renders)
  const llmClientRef = useRef<GeminiClient | null>(null);
  const clarificationAgentRef = useRef<ClarificationAgent | null>(null);
  const dialogueManagerRef = useRef<DialogueManager | null>(null);

  // Initialize managers
  useEffect(() => {
    if (!llmClientRef.current) {
      llmClientRef.current = new GeminiClient({
        apiKey: config.apiKey,
        model: config.model,
      });
    }

    if (!clarificationAgentRef.current) {
      clarificationAgentRef.current = new ClarificationAgent(llmClientRef.current, {
        sensitivity: config.sensitivity,
        maxQuestions: config.maxQuestions,
        skipSimpleTasks: config.skipSimpleTasks,
      });
    }

    if (!dialogueManagerRef.current) {
      dialogueManagerRef.current = new DialogueManager(clarificationAgentRef.current);
    }
  }, [config.apiKey, config.model, config.sensitivity, config.maxQuestions, config.skipSimpleTasks]);

  /**
   * Start a clarification dialogue for an instruction
   */
  const startDialogue = useCallback(
    async (instruction: string): Promise<DialogueState> => {
      if (!dialogueManagerRef.current) {
        throw new Error('Dialogue manager not initialized');
      }

      try {
        setIsAnalyzing(true);
        setError(null);

        const state = await dialogueManagerRef.current.startDialogue(instruction);
        setDialogueState(state);

        if (state.isComplete) {
          // No clarification needed
          setIsActive(false);
          setCurrentQuestion(null);
        } else {
          // Start dialogue
          setIsActive(true);
          const nextQuestion = await dialogueManagerRef.current.getNextQuestion(state.sessionId);
          setCurrentQuestion(nextQuestion);
        }

        return state;
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to start dialogue';
        setError(errorMessage);
        throw err;
      } finally {
        setIsAnalyzing(false);
      }
    },
    []
  );

  /**
   * Answer the current question
   */
  const answerQuestion = useCallback(
    async (answer: string): Promise<void> => {
      if (!dialogueManagerRef.current || !dialogueState || !currentQuestion) {
        throw new Error('No active dialogue or question');
      }

      try {
        setError(null);

        const response: ClarificationResponse = {
          questionId: currentQuestion.id,
          answer,
        };

        const updatedState = await dialogueManagerRef.current.answerQuestion(
          dialogueState.sessionId,
          response
        );

        setDialogueState(updatedState);

        if (updatedState.isComplete) {
          // Dialogue complete
          setIsActive(false);
          setCurrentQuestion(null);
        } else {
          // Get next question
          const nextQuestion = await dialogueManagerRef.current.getNextQuestion(
            updatedState.sessionId
          );
          setCurrentQuestion(nextQuestion);
        }
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to answer question';
        setError(errorMessage);
        throw err;
      }
    },
    [dialogueState, currentQuestion]
  );

  /**
   * Skip clarification and use original instruction
   */
  const skipClarification = useCallback(() => {
    if (dialogueState) {
      // Mark as complete with original instruction
      const updatedState: DialogueState = {
        ...dialogueState,
        isComplete: true,
        enhancedInstruction: dialogueState.originalInstruction,
      };
      setDialogueState(updatedState);
    }

    setIsActive(false);
    setCurrentQuestion(null);
    setError(null);
  }, [dialogueState]);

  /**
   * Complete the dialogue and get enhanced instruction
   */
  const completeDialogue = useCallback(async (): Promise<string> => {
    if (!dialogueManagerRef.current || !dialogueState) {
      throw new Error('No active dialogue');
    }

    if (!dialogueState.isComplete) {
      throw new Error('Dialogue is not complete - answer all questions first');
    }

    try {
      setError(null);
      const enhancedInstruction = await dialogueManagerRef.current.completeDialogue(
        dialogueState.sessionId
      );

      // Update state with enhanced instruction
      setDialogueState({
        ...dialogueState,
        enhancedInstruction,
      });

      return enhancedInstruction;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to complete dialogue';
      setError(errorMessage);
      throw err;
    }
  }, [dialogueState]);

  /**
   * Reset the hook state
   */
  const reset = useCallback(() => {
    setIsAnalyzing(false);
    setIsActive(false);
    setCurrentQuestion(null);
    setDialogueState(null);
    setError(null);

    // Clear dialogue manager sessions
    if (dialogueManagerRef.current) {
      dialogueManagerRef.current.clearAllSessions();
    }
  }, []);

  // Calculate progress
  const hasMoreQuestions = dialogueState
    ? !dialogueState.isComplete && dialogueState.currentQuestionIndex < dialogueState.questions.length
    : false;

  const progress = dialogueState
    ? {
        current: dialogueState.currentQuestionIndex,
        total: dialogueState.questions.length,
      }
    : { current: 0, total: 0 };

  return {
    // State
    isAnalyzing,
    isActive,
    currentQuestion,
    dialogueState,
    error,

    // Actions
    startDialogue,
    answerQuestion,
    skipClarification,
    completeDialogue,
    reset,

    // Helpers
    hasMoreQuestions,
    progress,
  };
}
