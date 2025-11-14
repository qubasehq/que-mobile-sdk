/**
 * GeminiClient - Interface to Google Gemini API for agent decision-making
 */

import { GoogleGenerativeAI, GenerativeModel } from '@google/generative-ai'
import { AgentOutput, LLMMessage } from '../types'
import { parseAgentOutput, extractJsonFromMarkdown } from './OutputParser'
import { QueError, ErrorCategory } from '../utils/errors'

/**
 * Configuration for GeminiClient
 */
export interface GeminiClientConfig {
  /** Gemini API key */
  apiKey: string
  /** Model name (default: gemini-1.5-flash) */
  model?: string
  /** Maximum retries on failure (default: 3) */
  maxRetries?: number
  /** Temperature for generation (default: 0.7) */
  temperature?: number
}

/**
 * GeminiClient handles communication with Google Gemini API
 */
export class GeminiClient {
  private genAI: GoogleGenerativeAI
  private model: GenerativeModel
  private maxRetries: number
  private temperature: number

  constructor(config: GeminiClientConfig) {
    if (!config.apiKey) {
      throw new QueError(
        'Gemini API key is required',
        ErrorCategory.LLM,
        false,
        { hint: 'Provide apiKey in AgentConfig' }
      )
    }

    this.genAI = new GoogleGenerativeAI(config.apiKey)
    this.maxRetries = config.maxRetries ?? 3
    this.temperature = config.temperature ?? 0.7

    const modelName = config.model ?? 'gemini-1.5-flash'
    this.model = this.genAI.getGenerativeModel({
      model: modelName,
      generationConfig: {
        temperature: this.temperature,
        topK: 40,
        topP: 0.95,
        maxOutputTokens: 8192,
      },
    })
  }

  /**
   * Generate agent output from conversation messages
   * @param messages - Array of conversation messages
   * @returns AgentOutput or null if all retries fail
   */
  async generateAgentOutput(messages: LLMMessage[]): Promise<AgentOutput | null> {
    let lastError: Error | null = null

    for (let attempt = 0; attempt < this.maxRetries; attempt++) {
      try {
        const output = await this.attemptGeneration(messages, attempt)
        if (output) {
          return output
        }

        // If parsing failed, add corrective message for next attempt
        if (attempt < this.maxRetries - 1) {
          messages.push({
            role: 'user',
            content: this.getCorrectiveMessage(attempt),
          })
        }
      } catch (error) {
        lastError = error as Error
        console.error(`Attempt ${attempt + 1}/${this.maxRetries} failed:`, error)

        // If it's a rate limit or network error, wait before retry
        if (this.isRetryableError(error)) {
          await this.delay(Math.pow(2, attempt) * 1000) // Exponential backoff
        } else {
          // Non-retryable error, throw immediately
          throw new QueError(
            `Gemini API error: ${(error as Error).message}`,
            ErrorCategory.LLM,
            false,
            { originalError: error }
          )
        }
      }
    }

    // All retries exhausted
    throw new QueError(
      `Failed to generate valid output after ${this.maxRetries} attempts`,
      ErrorCategory.LLM,
      true,
      { lastError }
    )
  }

  /**
   * Attempt to generate and parse output
   */
  private async attemptGeneration(
    messages: LLMMessage[],
    attemptNumber: number
  ): Promise<AgentOutput | null> {
    // Convert messages to Gemini format
    const geminiMessages = this.convertToGeminiFormat(messages)

    // Generate response
    const result = await this.model.generateContent({
      contents: geminiMessages,
    })

    const response = result.response
    const text = response.text()

    if (!text) {
      console.error('Empty response from Gemini API')
      return null
    }

    // Extract JSON from potential markdown wrapper
    const jsonText = extractJsonFromMarkdown(text)

    // Parse and validate
    const output = parseAgentOutput(jsonText)

    if (!output) {
      console.error(`Failed to parse output on attempt ${attemptNumber + 1}`)
      console.error('Raw response:', text)
      return null
    }

    return output
  }

  /**
   * Convert LLMMessage array to Gemini API format
   */
  private convertToGeminiFormat(messages: LLMMessage[]): any[] {
    const geminiMessages: any[] = []

    for (const message of messages) {
      // Gemini uses 'user' and 'model' roles
      const role = message.role === 'assistant' ? 'model' : message.role === 'system' ? 'user' : message.role

      geminiMessages.push({
        role: role,
        parts: [{ text: message.content }],
      })
    }

    return geminiMessages
  }

  /**
   * Get corrective message for retry attempts
   */
  private getCorrectiveMessage(attemptNumber: number): string {
    const messages = [
      'Your previous response was not valid JSON. Please respond with ONLY a valid JSON object matching the AgentOutput schema. Do not include any markdown formatting or explanations.',
      'The JSON structure was incorrect. Ensure you include all required fields: evaluationPreviousGoal, memory, nextGoal, and actions array. Each action must have a valid type and required parameters.',
      'Still receiving invalid JSON. Please carefully review the action types and their required parameters. Return ONLY the JSON object, nothing else.',
    ]

    return messages[Math.min(attemptNumber, messages.length - 1)]
  }

  /**
   * Check if error is retryable
   */
  private isRetryableError(error: any): boolean {
    const message = error?.message?.toLowerCase() || ''
    return (
      message.includes('rate limit') ||
      message.includes('timeout') ||
      message.includes('network') ||
      message.includes('503') ||
      message.includes('429')
    )
  }

  /**
   * Delay helper for exponential backoff
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }
}
