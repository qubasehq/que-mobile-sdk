/**
 * ClarificationAgent - Analyzes instructions for ambiguity and generates clarifying questions
 */

import { GeminiClient } from '../llm/GeminiClient';
import {
  AmbiguityAnalysis,
  AmbiguousElement,
  ClarificationQuestion,
  ClarificationResponse,
  ClarificationConfig,
} from './types';

const AMBIGUITY_DETECTION_PROMPT = `You are an instruction analyzer. Your task is to identify ambiguities in user instructions that could lead to task failure.

Analyze the following instruction for ambiguities:
"{instruction}"

Look for:
1. **Parameter ambiguities**: Missing or unclear values (e.g., "send a message" - what message?)
2. **Target ambiguities**: Unclear recipients or destinations (e.g., "message John" - which John?)
3. **Condition ambiguities**: Unclear conditions or criteria (e.g., "if it's ready" - what defines ready?)
4. **Sequence ambiguities**: Unclear order of operations (e.g., "open app and do X" - which app?)

Respond with JSON:
{
  "hasAmbiguity": boolean,
  "confidence": number (0.0-1.0),
  "ambiguousElements": [
    {
      "type": "parameter" | "target" | "condition" | "sequence",
      "description": "Brief description of the ambiguity",
      "suggestedQuestions": ["Question 1", "Question 2"]
    }
  ]
}

If the instruction is clear and unambiguous, return hasAmbiguity: false.`;

const QUESTION_GENERATION_PROMPT = `Based on the ambiguity analysis, generate specific clarifying questions.

Original instruction: "{instruction}"
Ambiguous elements: {ambiguousElements}

Generate questions that:
1. Are specific and actionable
2. Provide options when possible
3. Are easy to answer
4. Help resolve the ambiguity completely

Respond with JSON:
{
  "questions": [
    {
      "id": "unique_id",
      "question": "Clear question text",
      "type": "choice" | "text" | "number",
      "options": ["Option 1", "Option 2"] (for choice type),
      "required": boolean
    }
  ]
}`;

const INSTRUCTION_ENHANCEMENT_PROMPT = `Enhance the original instruction by incorporating the clarification responses.

Original instruction: "{instruction}"
Clarifications: {clarifications}

Create an enhanced instruction that:
1. Incorporates all clarification details
2. Is clear and unambiguous
3. Maintains the original intent
4. Is actionable by an AI agent

Respond with JSON:
{
  "enhancedInstruction": "The complete, clarified instruction"
}`;

export class ClarificationAgent {
  private llmClient: GeminiClient;
  private config: Required<ClarificationConfig>;

  constructor(llmClient: GeminiClient, config: ClarificationConfig = {}) {
    this.llmClient = llmClient;
    this.config = {
      sensitivity: config.sensitivity || 'medium',
      maxQuestions: config.maxQuestions || 5,
      skipSimpleTasks: config.skipSimpleTasks !== false,
    };
  }

  /**
   * Analyze an instruction for ambiguities
   */
  async analyzeInstruction(instruction: string): Promise<AmbiguityAnalysis> {
    try {
      // Skip analysis for very simple tasks if configured
      if (this.config.skipSimpleTasks && this.isSimpleTask(instruction)) {
        return {
          hasAmbiguity: false,
          ambiguousElements: [],
          confidence: 1.0,
        };
      }

      const prompt = AMBIGUITY_DETECTION_PROMPT.replace('{instruction}', instruction);

      const response = await this.llmClient.generateAgentOutput([
        {
          role: 'user',
          content: prompt,
        },
      ]);

      if (!response) {
        throw new Error('Failed to analyze instruction for ambiguities');
      }

      // Parse the response - it should contain the analysis
      const analysis = this.parseAmbiguityAnalysis(response);

      // Apply sensitivity threshold
      if (analysis.confidence < this.getSensitivityThreshold()) {
        return {
          hasAmbiguity: false,
          ambiguousElements: [],
          confidence: analysis.confidence,
        };
      }

      return analysis;
    } catch (error) {
      console.error('Error analyzing instruction:', error);
      // On error, assume no ambiguity to allow task to proceed
      return {
        hasAmbiguity: false,
        ambiguousElements: [],
        confidence: 0.0,
      };
    }
  }

  /**
   * Generate clarifying questions based on ambiguity analysis
   */
  async generateQuestions(analysis: AmbiguityAnalysis): Promise<ClarificationQuestion[]> {
    if (!analysis.hasAmbiguity || analysis.ambiguousElements.length === 0) {
      return [];
    }

    try {
      const prompt = QUESTION_GENERATION_PROMPT
        .replace('{instruction}', '')
        .replace('{ambiguousElements}', JSON.stringify(analysis.ambiguousElements, null, 2));

      const response = await this.llmClient.generateAgentOutput([
        {
          role: 'user',
          content: prompt,
        },
      ]);

      if (!response) {
        throw new Error('Failed to generate clarifying questions');
      }

      const questions = this.parseQuestions(response);

      // Limit number of questions
      return questions.slice(0, this.config.maxQuestions);
    } catch (error) {
      console.error('Error generating questions:', error);
      // Generate fallback questions from suggested questions
      return this.generateFallbackQuestions(analysis.ambiguousElements);
    }
  }

  /**
   * Enhance the original instruction with clarification responses
   */
  async enhanceInstruction(
    originalInstruction: string,
    responses: ClarificationResponse[]
  ): Promise<string> {
    if (responses.length === 0) {
      return originalInstruction;
    }

    try {
      const clarifications = responses
        .map((r) => `Q: ${r.questionId}\nA: ${r.answer}`)
        .join('\n\n');

      const prompt = INSTRUCTION_ENHANCEMENT_PROMPT
        .replace('{instruction}', originalInstruction)
        .replace('{clarifications}', clarifications);

      const response = await this.llmClient.generateAgentOutput([
        {
          role: 'user',
          content: prompt,
        },
      ]);

      if (!response) {
        throw new Error('Failed to enhance instruction');
      }

      return this.parseEnhancedInstruction(response, originalInstruction);
    } catch (error) {
      console.error('Error enhancing instruction:', error);
      // Fallback: append responses to original instruction
      return this.createFallbackEnhancement(originalInstruction, responses);
    }
  }

  /**
   * Check if a task is simple enough to skip clarification
   */
  private isSimpleTask(instruction: string): boolean {
    const simplePatterns = [
      /^open\s+\w+$/i, // "open settings"
      /^go\s+back$/i, // "go back"
      /^go\s+home$/i, // "go home"
      /^press\s+\w+$/i, // "press back"
      /^scroll\s+(up|down)$/i, // "scroll down"
      /^wait$/i, // "wait"
    ];

    return simplePatterns.some((pattern) => pattern.test(instruction.trim()));
  }

  /**
   * Get sensitivity threshold based on configuration
   */
  private getSensitivityThreshold(): number {
    switch (this.config.sensitivity) {
      case 'low':
        return 0.7; // Only flag high-confidence ambiguities
      case 'medium':
        return 0.5; // Balanced
      case 'high':
        return 0.3; // Flag even low-confidence ambiguities
      default:
        return 0.5;
    }
  }

  /**
   * Parse ambiguity analysis from LLM response
   */
  private parseAmbiguityAnalysis(response: any): AmbiguityAnalysis {
    // Try to extract JSON from the response
    const text = JSON.stringify(response);
    const jsonMatch = text.match(/\{[\s\S]*"hasAmbiguity"[\s\S]*\}/);

    if (jsonMatch) {
      try {
        const parsed = JSON.parse(jsonMatch[0]);
        return {
          hasAmbiguity: parsed.hasAmbiguity || false,
          confidence: parsed.confidence || 0.0,
          ambiguousElements: parsed.ambiguousElements || [],
        };
      } catch (e) {
        // Fall through to default
      }
    }

    // Default: no ambiguity
    return {
      hasAmbiguity: false,
      ambiguousElements: [],
      confidence: 0.0,
    };
  }

  /**
   * Parse questions from LLM response
   */
  private parseQuestions(response: any): ClarificationQuestion[] {
    const text = JSON.stringify(response);
    const jsonMatch = text.match(/\{[\s\S]*"questions"[\s\S]*\}/);

    if (jsonMatch) {
      try {
        const parsed = JSON.parse(jsonMatch[0]);
        return parsed.questions || [];
      } catch (e) {
        // Fall through to empty array
      }
    }

    return [];
  }

  /**
   * Parse enhanced instruction from LLM response
   */
  private parseEnhancedInstruction(response: any, fallback: string): string {
    const text = JSON.stringify(response);
    const jsonMatch = text.match(/\{[\s\S]*"enhancedInstruction"[\s\S]*\}/);

    if (jsonMatch) {
      try {
        const parsed = JSON.parse(jsonMatch[0]);
        return parsed.enhancedInstruction || fallback;
      } catch (e) {
        // Fall through to fallback
      }
    }

    return fallback;
  }

  /**
   * Generate fallback questions from ambiguous elements
   */
  private generateFallbackQuestions(elements: AmbiguousElement[]): ClarificationQuestion[] {
    return elements.flatMap((element, index) =>
      element.suggestedQuestions.slice(0, 2).map((question, qIndex) => ({
        id: `q${index}_${qIndex}`,
        question,
        type: 'text' as const,
        required: true,
      }))
    );
  }

  /**
   * Create fallback enhanced instruction by appending responses
   */
  private createFallbackEnhancement(
    originalInstruction: string,
    responses: ClarificationResponse[]
  ): string {
    const clarifications = responses.map((r) => r.answer).join(', ');
    return `${originalInstruction} (${clarifications})`;
  }
}
