/**
 * InstructionEnhancer - Parses clarification responses and merges them into instructions
 */

import { ClarificationResponse, ClarificationQuestion } from './types';

export interface EnhancementResult {
  enhancedInstruction: string;
  isComplete: boolean;
  missingRequiredFields: string[];
}

export class InstructionEnhancer {
  /**
   * Parse clarification responses and extract key information
   */
  parseResponses(
    responses: ClarificationResponse[],
    questions: ClarificationQuestion[]
  ): Map<string, string> {
    const parsedData = new Map<string, string>();

    for (const response of responses) {
      const question = questions.find((q) => q.id === response.questionId);

      if (question) {
        // Store the response with the question ID as key
        parsedData.set(response.questionId, response.answer);

        // Also store with a semantic key if we can infer it
        const semanticKey = this.inferSemanticKey(question.question);
        if (semanticKey) {
          parsedData.set(semanticKey, response.answer);
        }
      }
    }

    return parsedData;
  }

  /**
   * Merge clarification responses into the original instruction
   */
  mergeIntoInstruction(
    originalInstruction: string,
    responses: ClarificationResponse[],
    questions: ClarificationQuestion[]
  ): string {
    const parsedData = this.parseResponses(responses, questions);

    // Build enhancement clauses from responses
    const enhancements: string[] = [];

    for (const [questionId, answer] of parsedData.entries()) {
      // Skip semantic keys (they're duplicates)
      if (!questionId.startsWith('q')) {
        continue;
      }

      const question = questions.find((q) => q.id === questionId);
      if (question) {
        const enhancement = this.createEnhancementClause(question, answer);
        if (enhancement) {
          enhancements.push(enhancement);
        }
      }
    }

    // Merge enhancements into instruction
    if (enhancements.length === 0) {
      return originalInstruction;
    }

    // Try to intelligently insert enhancements
    return this.intelligentMerge(originalInstruction, enhancements);
  }

  /**
   * Validate that the enhanced instruction is complete
   */
  validateCompleteness(
    enhancedInstruction: string,
    questions: ClarificationQuestion[],
    responses: ClarificationResponse[]
  ): EnhancementResult {
    const missingRequired: string[] = [];

    // Check that all required questions have responses
    for (const question of questions) {
      if (question.required) {
        const hasResponse = responses.some((r) => r.questionId === question.id);
        if (!hasResponse) {
          missingRequired.push(question.id);
        }
      }
    }

    return {
      enhancedInstruction,
      isComplete: missingRequired.length === 0,
      missingRequiredFields: missingRequired,
    };
  }

  /**
   * Create a simple enhanced instruction by appending clarifications
   */
  createSimpleEnhancement(
    originalInstruction: string,
    responses: ClarificationResponse[],
    questions: ClarificationQuestion[]
  ): string {
    if (responses.length === 0) {
      return originalInstruction;
    }

    const clarifications = responses
      .map((response) => {
        const question = questions.find((q) => q.id === response.questionId);
        if (question) {
          return `${this.extractKeyword(question.question)}: ${response.answer}`;
        }
        return response.answer;
      })
      .filter(Boolean);

    if (clarifications.length === 0) {
      return originalInstruction;
    }

    return `${originalInstruction} (${clarifications.join(', ')})`;
  }

  /**
   * Infer a semantic key from a question
   */
  private inferSemanticKey(question: string): string | null {
    const lowerQuestion = question.toLowerCase();

    // Common patterns
    if (lowerQuestion.includes('which') || lowerQuestion.includes('who')) {
      if (lowerQuestion.includes('contact') || lowerQuestion.includes('person')) {
        return 'target_person';
      }
      if (lowerQuestion.includes('app')) {
        return 'target_app';
      }
    }

    if (lowerQuestion.includes('what message') || lowerQuestion.includes('what text')) {
      return 'message_content';
    }

    if (lowerQuestion.includes('when') || lowerQuestion.includes('time')) {
      return 'time';
    }

    if (lowerQuestion.includes('where') || lowerQuestion.includes('location')) {
      return 'location';
    }

    return null;
  }

  /**
   * Create an enhancement clause from a question and answer
   */
  private createEnhancementClause(question: ClarificationQuestion, answer: string): string | null {
    const lowerQuestion = question.question.toLowerCase();

    // Try to create natural language clauses
    if (lowerQuestion.includes('which') || lowerQuestion.includes('who')) {
      return answer;
    }

    if (lowerQuestion.includes('what message') || lowerQuestion.includes('what text')) {
      return `with message "${answer}"`;
    }

    if (lowerQuestion.includes('when') || lowerQuestion.includes('time')) {
      return `at ${answer}`;
    }

    if (lowerQuestion.includes('where')) {
      return `at ${answer}`;
    }

    // Default: just return the answer
    return answer;
  }

  /**
   * Intelligently merge enhancements into the instruction
   */
  private intelligentMerge(originalInstruction: string, enhancements: string[]): string {
    // Simple strategy: append enhancements with proper grammar
    let result = originalInstruction.trim();

    // Remove trailing period if present
    if (result.endsWith('.')) {
      result = result.slice(0, -1);
    }

    // Add enhancements
    for (const enhancement of enhancements) {
      // Check if enhancement should be inserted or appended
      if (this.shouldInsert(result, enhancement)) {
        result = this.insertEnhancement(result, enhancement);
      } else {
        result = `${result} ${enhancement}`;
      }
    }

    return result.trim();
  }

  /**
   * Check if an enhancement should be inserted rather than appended
   */
  private shouldInsert(instruction: string, enhancement: string): boolean {
    // If enhancement looks like a target (name, app), try to insert
    const lowerInstruction = instruction.toLowerCase();
    const lowerEnhancement = enhancement.toLowerCase();

    // Check for patterns like "send message to X" where X should be inserted
    if (
      lowerInstruction.includes('send') &&
      lowerInstruction.includes('to') &&
      !lowerEnhancement.startsWith('with')
    ) {
      return true;
    }

    return false;
  }

  /**
   * Insert an enhancement into the instruction at the appropriate position
   */
  private insertEnhancement(instruction: string, enhancement: string): string {
    const lowerInstruction = instruction.toLowerCase();

    // Try to find insertion point
    const toIndex = lowerInstruction.indexOf(' to ');
    if (toIndex !== -1) {
      // Insert after "to"
      const before = instruction.slice(0, toIndex + 4);
      const after = instruction.slice(toIndex + 4);
      return `${before}${enhancement} ${after}`;
    }

    // Default: append
    return `${instruction} ${enhancement}`;
  }

  /**
   * Extract a keyword from a question for labeling
   */
  private extractKeyword(question: string): string {
    const lowerQuestion = question.toLowerCase();

    if (lowerQuestion.includes('contact') || lowerQuestion.includes('person')) {
      return 'Contact';
    }
    if (lowerQuestion.includes('app')) {
      return 'App';
    }
    if (lowerQuestion.includes('message') || lowerQuestion.includes('text')) {
      return 'Message';
    }
    if (lowerQuestion.includes('time')) {
      return 'Time';
    }
    if (lowerQuestion.includes('location')) {
      return 'Location';
    }

    return 'Detail';
  }
}
