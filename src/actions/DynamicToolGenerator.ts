/**
 * DynamicToolGenerator - Allows AI to generate custom tools at runtime
 * Hybrid approach: predefined tools + AI-generated tools
 */

import { ActionResult, ScreenState } from '../types'
import { AccessibilityModule } from '../native/AccessibilityModule'
import { FileSystem } from '../memory/FileSystem'

/**
 * Dynamic tool definition
 */
export interface DynamicTool {
  /** Unique tool name */
  name: string
  /** Tool description for LLM */
  description: string
  /** Parameter definitions */
  parameters: DynamicToolParameter[]
  /** Implementation as executable code */
  implementation: string
  /** When the tool was created */
  createdAt: number
  /** How many times it's been used */
  usageCount: number
}

export interface DynamicToolParameter {
  name: string
  type: 'string' | 'number' | 'boolean' | 'object'
  description: string
  required: boolean
}

/**
 * Tool generation request from AI
 */
export interface ToolGenerationRequest {
  toolName: string
  description: string
  parameters: DynamicToolParameter[]
  /** High-level description of what the tool should do */
  intent: string
  /** Example usage */
  example?: string
}

/**
 * DynamicToolGenerator - Manages custom tool creation and execution
 */
export class DynamicToolGenerator {
  private tools: Map<string, DynamicTool> = new Map()
  private maxTools: number = 50 // Limit to prevent memory issues

  constructor(
    private nativeModule: AccessibilityModule,
    private fileSystem: FileSystem
  ) {}

  /**
   * Generate a new tool based on AI request
   */
  async generateTool(request: ToolGenerationRequest): Promise<{ success: boolean; error?: string }> {
    try {
      // Validate tool name
      if (!/^[a-z_][a-z0-9_]*$/.test(request.toolName)) {
        return {
          success: false,
          error: 'Tool name must be lowercase with underscores only (e.g., my_custom_tool)',
        }
      }

      // Check if tool already exists
      if (this.tools.has(request.toolName)) {
        return {
          success: false,
          error: `Tool '${request.toolName}' already exists`,
        }
      }

      // Check tool limit
      if (this.tools.size >= this.maxTools) {
        return {
          success: false,
          error: `Maximum number of dynamic tools (${this.maxTools}) reached`,
        }
      }

      // Generate implementation based on intent
      const implementation = this.generateImplementation(request)

      // Create tool definition
      const tool: DynamicTool = {
        name: request.toolName,
        description: request.description,
        parameters: request.parameters,
        implementation,
        createdAt: Date.now(),
        usageCount: 0,
      }

      // Store tool
      this.tools.set(request.toolName, tool)

      // Persist to file system
      await this.persistTools()

      return { success: true }
    } catch (error) {
      return {
        success: false,
        error: `Failed to generate tool: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  /**
   * Execute a dynamic tool
   */
  async executeTool(
    toolName: string,
    parameters: Record<string, any>,
    screenState: ScreenState
  ): Promise<ActionResult> {
    const tool = this.tools.get(toolName)

    if (!tool) {
      return {
        error: `Dynamic tool '${toolName}' not found`,
      }
    }

    try {
      // Validate parameters
      const validationError = this.validateParameters(tool, parameters)
      if (validationError) {
        return { error: validationError }
      }

      // Execute tool implementation
      const result = await this.executeImplementation(tool, parameters, screenState)

      // Update usage count
      tool.usageCount++

      return result
    } catch (error) {
      return {
        error: `Error executing tool '${toolName}': ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  /**
   * Get all available dynamic tools
   */
  getAvailableTools(): DynamicTool[] {
    return Array.from(this.tools.values())
  }

  /**
   * Get tool by name
   */
  getTool(name: string): DynamicTool | undefined {
    return this.tools.get(name)
  }

  /**
   * Delete a dynamic tool
   */
  async deleteTool(name: string): Promise<boolean> {
    const deleted = this.tools.delete(name)
    if (deleted) {
      await this.persistTools()
    }
    return deleted
  }

  /**
   * Load tools from file system
   */
  async loadTools(): Promise<void> {
    try {
      const { content } = await this.fileSystem.readFile('dynamic_tools.txt')
      const toolsData = JSON.parse(content)
      
      for (const toolData of toolsData) {
        this.tools.set(toolData.name, toolData)
      }
    } catch (error) {
      // File doesn't exist or is invalid - start fresh
      this.tools.clear()
    }
  }

  // ==========================================================================
  // Private Methods
  // ==========================================================================

  private generateImplementation(request: ToolGenerationRequest): string {
    // Generate a safe, sandboxed implementation based on intent
    // This is a simplified version - in production, you'd want more sophisticated code generation
    
    const template = `
// Auto-generated tool: ${request.toolName}
// Intent: ${request.intent}
async function execute(params, screenState, nativeModule, fileSystem) {
  try {
    // Tool implementation based on intent
    ${this.generateIntentBasedCode(request)}
    
    return {
      longTermMemory: "Executed ${request.toolName} successfully"
    };
  } catch (error) {
    return {
      error: "Tool execution failed: " + error.message
    };
  }
}
`
    return template
  }

  private generateIntentBasedCode(request: ToolGenerationRequest): string {
    const intent = request.intent.toLowerCase()

    // Pattern matching for common intents
    if (intent.includes('tap') || intent.includes('click')) {
      return `
    // Find and tap element
    const elementId = params.elementId || params.element_id;
    const element = screenState.elementMap.get(elementId);
    if (!element) {
      throw new Error('Element not found');
    }
    await nativeModule.clickOnPoint(element.center.x, element.center.y);
      `
    }

    if (intent.includes('scroll') || intent.includes('swipe')) {
      return `
    // Scroll action
    const direction = params.direction || 'down';
    const amount = params.amount || 500;
    await nativeModule.scroll(direction, amount);
      `
    }

    if (intent.includes('type') || intent.includes('input')) {
      return `
    // Type text
    const text = params.text || '';
    await nativeModule.typeText(text);
      `
    }

    if (intent.includes('file') || intent.includes('write') || intent.includes('save')) {
      return `
    // File operation
    const fileName = params.fileName || params.file_name || 'output.txt';
    const content = params.content || '';
    await fileSystem.writeFile(fileName, content);
      `
    }

    if (intent.includes('wait') || intent.includes('delay')) {
      return `
    // Wait/delay
    const duration = params.duration || 1000;
    await new Promise(resolve => setTimeout(resolve, duration));
      `
    }

    // Default: composite action
    return `
    // Composite action - combine multiple steps
    // This is a placeholder - the AI should provide more specific implementation
    console.log('Executing custom tool with params:', params);
      `
  }

  private async executeImplementation(
    tool: DynamicTool,
    parameters: Record<string, any>,
    screenState: ScreenState
  ): Promise<ActionResult> {
    try {
      // Create a sandboxed execution context
      const context = {
        params: parameters,
        screenState,
        nativeModule: this.nativeModule,
        fileSystem: this.fileSystem,
      }

      // Execute the tool implementation
      // Note: In production, you'd want to use a proper sandboxing solution
      // like isolated-vm or a WebWorker for security
      const executeFunc = new Function(
        'params',
        'screenState',
        'nativeModule',
        'fileSystem',
        tool.implementation + '\nreturn execute(params, screenState, nativeModule, fileSystem);'
      )

      const result = await executeFunc(
        context.params,
        context.screenState,
        context.nativeModule,
        context.fileSystem
      )

      return result
    } catch (error) {
      return {
        error: `Tool execution error: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private validateParameters(tool: DynamicTool, parameters: Record<string, any>): string | null {
    for (const param of tool.parameters) {
      if (param.required && !(param.name in parameters)) {
        return `Missing required parameter: ${param.name}`
      }

      if (param.name in parameters) {
        const value = parameters[param.name]
        const actualType = typeof value

        // Type checking
        if (param.type === 'object' && actualType !== 'object') {
          return `Parameter '${param.name}' must be an object`
        } else if (param.type !== 'object' && actualType !== param.type) {
          return `Parameter '${param.name}' must be of type ${param.type}`
        }
      }
    }

    return null
  }

  private async persistTools(): Promise<void> {
    try {
      const toolsArray = Array.from(this.tools.values())
      const content = JSON.stringify(toolsArray, null, 2)
      await this.fileSystem.writeFile('dynamic_tools.txt', content)
    } catch (error) {
      console.error('Failed to persist dynamic tools:', error)
    }
  }
}
