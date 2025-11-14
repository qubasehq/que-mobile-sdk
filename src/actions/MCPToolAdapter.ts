/**
 * MCPToolAdapter - Model Context Protocol style tool adapter
 * Allows external tools to be registered and executed through a standard protocol
 */

import { ActionResult, ScreenState } from '../types'
import { ToolSpec, ToolParameter } from './ToolRegistry'

/**
 * MCP Tool definition (external tool that can be called)
 */
export interface MCPTool {
  name: string
  description: string
  inputSchema: {
    type: 'object'
    properties: Record<string, MCPToolProperty>
    required?: string[]
  }
  handler: (params: Record<string, any>, context: MCPToolContext) => Promise<MCPToolResult>
}

export interface MCPToolProperty {
  type: 'string' | 'number' | 'boolean' | 'array' | 'object'
  description: string
  enum?: string[]
  items?: MCPToolProperty
  properties?: Record<string, MCPToolProperty>
}

export interface MCPToolContext {
  screenState: ScreenState
  workspaceDir: string
  metadata?: Record<string, any>
}

export interface MCPToolResult {
  content: Array<{
    type: 'text' | 'image' | 'resource'
    text?: string
    data?: string
    mimeType?: string
  }>
  isError?: boolean
}

/**
 * MCPToolAdapter - Manages external MCP-style tools
 */
export class MCPToolAdapter {
  private tools: Map<string, MCPTool> = new Map()
  private toolCallHistory: Array<{
    toolName: string
    params: Record<string, any>
    result: MCPToolResult
    timestamp: number
    duration: number
  }> = []

  /**
   * Register an MCP tool
   */
  registerTool(tool: MCPTool): void {
    if (this.tools.has(tool.name)) {
      console.warn(`[MCPToolAdapter] Tool '${tool.name}' already registered, overwriting`)
    }

    this.tools.set(tool.name, tool)
    console.log(`[MCPToolAdapter] Registered tool: ${tool.name}`)
  }

  /**
   * Unregister an MCP tool
   */
  unregisterTool(name: string): boolean {
    const deleted = this.tools.delete(name)
    if (deleted) {
      console.log(`[MCPToolAdapter] Unregistered tool: ${name}`)
    }
    return deleted
  }

  /**
   * Execute an MCP tool
   */
  async executeTool(
    toolName: string,
    params: Record<string, any>,
    context: MCPToolContext
  ): Promise<ActionResult> {
    const tool = this.tools.get(toolName)

    if (!tool) {
      return {
        error: `MCP tool '${toolName}' not found. Available tools: ${Array.from(this.tools.keys()).join(', ')}`,
      }
    }

    try {
      // Validate parameters against schema
      const validationError = this.validateParams(params, tool.inputSchema)
      if (validationError) {
        return {
          error: `Invalid parameters for tool '${toolName}': ${validationError}`,
        }
      }

      const startTime = Date.now()

      // Execute tool handler
      const result = await tool.handler(params, context)

      const duration = Date.now() - startTime

      // Record in history
      this.toolCallHistory.push({
        toolName,
        params,
        result,
        timestamp: startTime,
        duration,
      })

      // Keep only last 100 calls
      if (this.toolCallHistory.length > 100) {
        this.toolCallHistory.shift()
      }

      // Convert MCP result to ActionResult
      return this.convertMCPResult(result, toolName)
    } catch (error) {
      return {
        error: `Error executing MCP tool '${toolName}': ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  /**
   * Get all registered MCP tools
   */
  getTools(): MCPTool[] {
    return Array.from(this.tools.values())
  }

  /**
   * Get tool by name
   */
  getTool(name: string): MCPTool | undefined {
    return this.tools.get(name)
  }

  /**
   * Convert MCP tools to ToolSpec format
   */
  toToolSpecs(): ToolSpec[] {
    return Array.from(this.tools.values()).map(tool => this.mcpToToolSpec(tool))
  }

  /**
   * Get tool call history
   */
  getHistory(limit: number = 10): typeof this.toolCallHistory {
    return this.toolCallHistory.slice(-limit)
  }

  /**
   * Clear tool call history
   */
  clearHistory(): void {
    this.toolCallHistory = []
  }

  // ==========================================================================
  // Private Methods
  // ==========================================================================

  private validateParams(params: Record<string, any>, schema: MCPTool['inputSchema']): string | null {
    // Check required parameters
    if (schema.required) {
      for (const requiredParam of schema.required) {
        if (!(requiredParam in params)) {
          return `Missing required parameter: ${requiredParam}`
        }
      }
    }

    // Validate parameter types
    for (const [key, value] of Object.entries(params)) {
      const propSchema = schema.properties[key]
      if (!propSchema) {
        continue // Allow extra parameters
      }

      const actualType = Array.isArray(value) ? 'array' : typeof value
      if (actualType !== propSchema.type) {
        return `Parameter '${key}' must be of type ${propSchema.type}, got ${actualType}`
      }

      // Validate enum values
      if (propSchema.enum && !propSchema.enum.includes(value)) {
        return `Parameter '${key}' must be one of: ${propSchema.enum.join(', ')}`
      }
    }

    return null
  }

  private convertMCPResult(mcpResult: MCPToolResult, toolName: string): ActionResult {
    if (mcpResult.isError) {
      const errorText = mcpResult.content.find(c => c.type === 'text')?.text || 'Unknown error'
      return {
        error: errorText,
      }
    }

    // Extract text content
    const textContent = mcpResult.content
      .filter(c => c.type === 'text')
      .map(c => c.text)
      .join('\n')

    // Check for image/resource content
    const hasMedia = mcpResult.content.some(c => c.type === 'image' || c.type === 'resource')

    return {
      longTermMemory: `Executed MCP tool '${toolName}'${textContent ? `: ${textContent.substring(0, 100)}` : ''}`,
      extractedContent: textContent || undefined,
      includeExtractedContentOnlyOnce: hasMedia,
    }
  }

  private mcpToToolSpec(mcpTool: MCPTool): ToolSpec {
    const parameters: ToolParameter[] = Object.entries(mcpTool.inputSchema.properties).map(
      ([name, prop]) => ({
        name,
        type: prop.type,
        description: prop.description,
        required: mcpTool.inputSchema.required?.includes(name) || false,
        enum: prop.enum,
      })
    )

    return {
      name: mcpTool.name,
      description: mcpTool.description,
      category: 'custom',
      parameters,
    }
  }
}

/**
 * Built-in MCP tools
 */
export class BuiltInMCPTools {
  /**
   * Create a web search tool
   */
  static createWebSearchTool(): MCPTool {
    return {
      name: 'web_search',
      description: 'Search the web for information',
      inputSchema: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'The search query',
          },
          num_results: {
            type: 'number',
            description: 'Number of results to return (default: 5)',
          },
        },
        required: ['query'],
      },
      handler: async (params, _context) => {
        // This would integrate with a real search API
        return {
          content: [
            {
              type: 'text',
              text: `Web search for "${params.query}" would return results here. This is a placeholder.`,
            },
          ],
        }
      },
    }
  }

  /**
   * Create a calculator tool
   */
  static createCalculatorTool(): MCPTool {
    return {
      name: 'calculator',
      description: 'Perform mathematical calculations',
      inputSchema: {
        type: 'object',
        properties: {
          expression: {
            type: 'string',
            description: 'Mathematical expression to evaluate (e.g., "2 + 2 * 3")',
          },
        },
        required: ['expression'],
      },
      handler: async (params, _context) => {
        try {
          // Safe eval using Function constructor (limited scope)
          const result = Function(`'use strict'; return (${params.expression})`)()

          return {
            content: [
              {
                type: 'text',
                text: `${params.expression} = ${result}`,
              },
            ],
          }
        } catch (error) {
          return {
            content: [
              {
                type: 'text',
                text: `Error evaluating expression: ${error instanceof Error ? error.message : String(error)}`,
              },
            ],
            isError: true,
          }
        }
      },
    }
  }

  /**
   * Create a date/time tool
   */
  static createDateTimeTool(): MCPTool {
    return {
      name: 'get_datetime',
      description: 'Get current date and time information',
      inputSchema: {
        type: 'object',
        properties: {
          format: {
            type: 'string',
            description: 'Format type: "iso", "unix", "readable"',
            enum: ['iso', 'unix', 'readable'],
          },
          timezone: {
            type: 'string',
            description: 'Timezone (e.g., "America/New_York")',
          },
        },
      },
      handler: async (params, _context) => {
        const now = new Date()
        const format = params.format || 'readable'

        let result: string

        switch (format) {
          case 'iso':
            result = now.toISOString()
            break
          case 'unix':
            result = Math.floor(now.getTime() / 1000).toString()
            break
          case 'readable':
          default:
            result = now.toLocaleString()
            break
        }

        return {
          content: [
            {
              type: 'text',
              text: `Current date/time: ${result}`,
            },
          ],
        }
      },
    }
  }

  /**
   * Get all built-in MCP tools
   */
  static getAllTools(): MCPTool[] {
    return [this.createWebSearchTool(), this.createCalculatorTool(), this.createDateTimeTool()]
  }
}
