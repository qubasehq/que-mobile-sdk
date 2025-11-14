/**
 * ToolRegistry - Central registry for all available tools/actions
 * Similar to Blurr's Spec system but with TypeScript type safety
 */



/**
 * Tool parameter specification
 */
export interface ToolParameter {
  name: string
  type: 'string' | 'number' | 'boolean' | 'array' | 'object'
  description: string
  required: boolean
  default?: any
  enum?: string[] // For string enums
  min?: number // For numbers
  max?: number // For numbers
}

/**
 * Tool specification
 */
export interface ToolSpec {
  name: string
  description: string
  category: 'interaction' | 'navigation' | 'input' | 'file' | 'voice' | 'system' | 'custom'
  parameters: ToolParameter[]
  examples?: string[]
  requiresPermission?: string[]
  deprecated?: boolean
  aliases?: string[]
}

/**
 * ToolRegistry - Manages all available tools
 */
export class ToolRegistry {
  private static specs: Map<string, ToolSpec> = new Map()
  private static initialized = false

  /**
   * Initialize the registry with all predefined tools
   */
  static initialize(): void {
    if (this.initialized) return

    // ========================================================================
    // Element Interaction Tools
    // ========================================================================

    this.register({
      name: 'tap_element',
      description: 'Tap/click on an element by its numeric ID',
      category: 'interaction',
      parameters: [
        {
          name: 'element_id',
          type: 'number',
          description: 'The numeric ID of the element to tap',
          required: true,
        },
      ],
      examples: ['tap_element(element_id: 5)', 'tap_element(element_id: 12)'],
    })

    this.register({
      name: 'long_press_element',
      description: 'Long press on an element by its numeric ID. Useful for context menus.',
      category: 'interaction',
      parameters: [
        {
          name: 'element_id',
          type: 'number',
          description: 'The numeric ID of the element to long press',
          required: true,
        },
      ],
      examples: ['long_press_element(element_id: 8)'],
    })

    this.register({
      name: 'tap_element_input_text_and_enter',
      description: 'Tap an input element, type text, and press enter. Useful for search bars.',
      category: 'input',
      parameters: [
        {
          name: 'index',
          type: 'number',
          description: 'The numeric ID of the input element',
          required: true,
        },
        {
          name: 'text',
          type: 'string',
          description: 'The text to type',
          required: true,
        },
      ],
      examples: ['tap_element_input_text_and_enter(index: 3, text: "hello world")'],
    })

    // ========================================================================
    // Input Tools
    // ========================================================================

    this.register({
      name: 'type',
      description: 'Type text into the currently focused input field',
      category: 'input',
      parameters: [
        {
          name: 'text',
          type: 'string',
          description: 'The text to type',
          required: true,
        },
      ],
      examples: ['type(text: "Hello World")'],
    })

    // ========================================================================
    // Navigation Tools
    // ========================================================================

    this.register({
      name: 'swipe_down',
      description: 'Swipe down to scroll content downward',
      category: 'navigation',
      parameters: [
        {
          name: 'amount',
          type: 'number',
          description: 'Number of pixels to swipe',
          required: true,
          min: 100,
          max: 2000,
        },
      ],
      examples: ['swipe_down(amount: 500)'],
    })

    this.register({
      name: 'swipe_up',
      description: 'Swipe up to scroll content upward',
      category: 'navigation',
      parameters: [
        {
          name: 'amount',
          type: 'number',
          description: 'Number of pixels to swipe',
          required: true,
          min: 100,
          max: 2000,
        },
      ],
      examples: ['swipe_up(amount: 500)'],
    })

    this.register({
      name: 'back',
      description: 'Press the back button',
      category: 'navigation',
      parameters: [],
      examples: ['back()'],
    })

    this.register({
      name: 'home',
      description: 'Go to the home screen',
      category: 'navigation',
      parameters: [],
      examples: ['home()'],
    })

    this.register({
      name: 'switch_app',
      description: 'Open the app switcher/recents screen',
      category: 'navigation',
      parameters: [],
      examples: ['switch_app()'],
    })

    this.register({
      name: 'wait',
      description: 'Wait for 1 second. Use when content is loading.',
      category: 'system',
      parameters: [],
      examples: ['wait()'],
    })

    // ========================================================================
    // App Management Tools
    // ========================================================================

    this.register({
      name: 'open_app',
      description: 'Open an app by its name. The system will find the app automatically.',
      category: 'system',
      parameters: [
        {
          name: 'app_name',
          type: 'string',
          description: 'The name of the app (e.g., "Chrome", "Settings", "Gmail")',
          required: true,
        },
      ],
      examples: ['open_app(app_name: "Chrome")', 'open_app(app_name: "Settings")'],
    })

    this.register({
      name: 'search_google',
      description: 'Search Google with a query. Opens Chrome and performs the search.',
      category: 'system',
      parameters: [
        {
          name: 'query',
          type: 'string',
          description: 'The search query',
          required: true,
        },
      ],
      examples: ['search_google(query: "weather today")'],
    })

    this.register({
      name: 'get_current_app',
      description: 'Get the currently active app/activity name',
      category: 'system',
      parameters: [],
      examples: ['get_current_app()'],
    })

    // ========================================================================
    // File System Tools
    // ========================================================================

    this.register({
      name: 'write_file',
      description: 'Write content to a file, overwriting if it exists. Only .md and .txt files allowed.',
      category: 'file',
      parameters: [
        {
          name: 'file_name',
          type: 'string',
          description: 'The name of the file (must end with .md or .txt)',
          required: true,
        },
        {
          name: 'content',
          type: 'string',
          description: 'The content to write',
          required: true,
        },
      ],
      examples: ['write_file(file_name: "notes.txt", content: "Hello World")'],
    })

    this.register({
      name: 'append_file',
      description: 'Append content to the end of a file. Creates file if it doesn\'t exist.',
      category: 'file',
      parameters: [
        {
          name: 'file_name',
          type: 'string',
          description: 'The name of the file',
          required: true,
        },
        {
          name: 'content',
          type: 'string',
          description: 'The content to append',
          required: true,
        },
      ],
      examples: ['append_file(file_name: "log.txt", content: "New entry\\n")'],
    })

    this.register({
      name: 'read_file',
      description: 'Read the entire content of a file',
      category: 'file',
      parameters: [
        {
          name: 'file_name',
          type: 'string',
          description: 'The name of the file to read',
          required: true,
        },
      ],
      examples: ['read_file(file_name: "notes.txt")'],
    })

    this.register({
      name: 'list_files',
      description: 'List all files in the workspace',
      category: 'file',
      parameters: [],
      examples: ['list_files()'],
    })

    this.register({
      name: 'delete_file',
      description: 'Delete a file from the workspace',
      category: 'file',
      parameters: [
        {
          name: 'file_name',
          type: 'string',
          description: 'The name of the file to delete',
          required: true,
        },
      ],
      examples: ['delete_file(file_name: "old_notes.txt")'],
    })

    this.register({
      name: 'take_screenshot',
      description: 'Save the current screen hierarchy to a file',
      category: 'system',
      parameters: [
        {
          name: 'file_name',
          type: 'string',
          description: 'Optional filename for the screenshot',
          required: false,
        },
      ],
      examples: ['take_screenshot()', 'take_screenshot(file_name: "screen.txt")'],
    })

    // ========================================================================
    // Voice Tools
    // ========================================================================

    this.register({
      name: 'speak',
      description: 'Speak a message to the user using text-to-speech',
      category: 'voice',
      parameters: [
        {
          name: 'message',
          type: 'string',
          description: 'The message to speak',
          required: true,
        },
      ],
      examples: ['speak(message: "Task completed successfully")'],
    })

    this.register({
      name: 'ask',
      description: 'Ask the user a question and wait for voice response',
      category: 'voice',
      parameters: [
        {
          name: 'question',
          type: 'string',
          description: 'The question to ask',
          required: true,
        },
      ],
      examples: ['ask(question: "What is your name?")'],
    })

    // ========================================================================
    // Advanced Tools
    // ========================================================================

    this.register({
      name: 'launch_intent',
      description: 'Launch an Android intent by name with parameters',
      category: 'system',
      parameters: [
        {
          name: 'intent_name',
          type: 'string',
          description: 'The name of the intent',
          required: true,
        },
        {
          name: 'parameters',
          type: 'object',
          description: 'Intent parameters as key-value pairs',
          required: true,
        },
      ],
      examples: ['launch_intent(intent_name: "DIAL", parameters: {phone: "1234567890"})'],
    })

    this.register({
      name: 'done',
      description: 'Mark the task as complete with a success status and message',
      category: 'system',
      parameters: [
        {
          name: 'success',
          type: 'boolean',
          description: 'Whether the task was successful',
          required: true,
        },
        {
          name: 'text',
          type: 'string',
          description: 'Summary message for the user',
          required: true,
        },
        {
          name: 'files_to_display',
          type: 'array',
          description: 'Optional list of files to show the user',
          required: false,
        },
      ],
      examples: ['done(success: true, text: "Task completed successfully")'],
    })

    // ========================================================================
    // Dynamic Tool Generation
    // ========================================================================

    this.register({
      name: 'generate_tool',
      description: 'Generate a new custom tool that can be used in future steps',
      category: 'custom',
      parameters: [
        {
          name: 'tool_name',
          type: 'string',
          description: 'Name for the new tool (lowercase with underscores)',
          required: true,
        },
        {
          name: 'description',
          type: 'string',
          description: 'Description of what the tool does',
          required: true,
        },
        {
          name: 'parameters',
          type: 'array',
          description: 'Array of parameter definitions',
          required: true,
        },
        {
          name: 'intent',
          type: 'string',
          description: 'High-level description of the tool\'s purpose',
          required: true,
        },
      ],
      examples: [
        'generate_tool(tool_name: "double_tap", description: "Tap an element twice quickly", parameters: [{name: "element_id", type: "number", required: true}], intent: "Tap element twice with 200ms delay")',
      ],
    })

    this.register({
      name: 'execute_dynamic_tool',
      description: 'Execute a previously generated custom tool',
      category: 'custom',
      parameters: [
        {
          name: 'tool_name',
          type: 'string',
          description: 'Name of the tool to execute',
          required: true,
        },
        {
          name: 'parameters',
          type: 'object',
          description: 'Parameters for the tool',
          required: true,
        },
      ],
      examples: ['execute_dynamic_tool(tool_name: "double_tap", parameters: {element_id: 5})'],
    })

    // ========================================================================
    // Clipboard Tools
    // ========================================================================

    this.register({
      name: 'get_clipboard',
      description: 'Get the current clipboard content',
      category: 'system',
      parameters: [],
      examples: ['get_clipboard()'],
    })

    this.register({
      name: 'set_clipboard',
      description: 'Set the clipboard content',
      category: 'system',
      parameters: [
        {
          name: 'text',
          type: 'string',
          description: 'Text to copy to clipboard',
          required: true,
        },
      ],
      examples: ['set_clipboard(text: "Hello World")'],
    })

    this.initialized = true
  }

  /**
   * Register a new tool spec
   */
  static register(spec: ToolSpec): void {
    this.specs.set(spec.name, spec)

    // Register aliases
    if (spec.aliases) {
      for (const alias of spec.aliases) {
        this.specs.set(alias, spec)
      }
    }
  }

  /**
   * Get a tool spec by name
   */
  static get(name: string): ToolSpec | undefined {
    if (!this.initialized) this.initialize()
    return this.specs.get(name)
  }

  /**
   * Get all tool specs
   */
  static getAll(): ToolSpec[] {
    if (!this.initialized) this.initialize()
    return Array.from(this.specs.values())
  }

  /**
   * Get tools by category
   */
  static getByCategory(category: ToolSpec['category']): ToolSpec[] {
    if (!this.initialized) this.initialize()
    return Array.from(this.specs.values()).filter(spec => spec.category === category)
  }

  /**
   * Get tool names for a specific category
   */
  static getToolNames(category?: ToolSpec['category']): string[] {
    if (!this.initialized) this.initialize()
    const specs = category ? this.getByCategory(category) : this.getAll()
    return specs.map(spec => spec.name)
  }

  /**
   * Generate JSON schema for all tools (for LLM function calling)
   */
  static toJSONSchema(): any[] {
    if (!this.initialized) this.initialize()
    
    return Array.from(this.specs.values()).map(spec => ({
      name: spec.name,
      description: spec.description,
      parameters: {
        type: 'object',
        properties: spec.parameters.reduce((acc, param) => {
          acc[param.name] = {
            type: param.type,
            description: param.description,
            ...(param.enum && { enum: param.enum }),
            ...(param.min !== undefined && { minimum: param.min }),
            ...(param.max !== undefined && { maximum: param.max }),
          }
          return acc
        }, {} as any),
        required: spec.parameters.filter(p => p.required).map(p => p.name),
      },
    }))
  }

  /**
   * Generate markdown documentation for all tools
   */
  static toMarkdown(): string {
    if (!this.initialized) this.initialize()
    
    const categories = ['interaction', 'navigation', 'input', 'file', 'voice', 'system', 'custom'] as const
    let markdown = '# Available Tools\n\n'

    for (const category of categories) {
      const tools = this.getByCategory(category)
      if (tools.length === 0) continue

      markdown += `## ${category.charAt(0).toUpperCase() + category.slice(1)} Tools\n\n`

      for (const tool of tools) {
        markdown += `### ${tool.name}\n\n`
        markdown += `${tool.description}\n\n`

        if (tool.parameters.length > 0) {
          markdown += '**Parameters:**\n\n'
          for (const param of tool.parameters) {
            const required = param.required ? '(required)' : '(optional)'
            markdown += `- \`${param.name}\` (${param.type}) ${required}: ${param.description}\n`
          }
          markdown += '\n'
        }

        if (tool.examples && tool.examples.length > 0) {
          markdown += '**Examples:**\n\n'
          for (const example of tool.examples) {
            markdown += `- \`${example}\`\n`
          }
          markdown += '\n'
        }
      }
    }

    return markdown
  }
}

// Initialize on module load
ToolRegistry.initialize()
