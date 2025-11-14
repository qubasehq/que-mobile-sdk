/**
 * ActionExecutor - Executes actions by calling native module methods
 * Handles all 19 action types with proper error handling
 */

import { Action } from './types'
import { ActionResult, ScreenState } from '../types'
import { AccessibilityModule } from '../native/AccessibilityModule'
import { FileSystem } from '../memory/FileSystem'
import { QueError } from '../utils/errors'
import { DynamicToolGenerator } from './DynamicToolGenerator'
import { CircuitBreaker } from './ActionRetryHandler'
import { MCPToolAdapter, BuiltInMCPTools } from './MCPToolAdapter'
import { ToolRegistry } from './ToolRegistry'



/**
 * VoiceManager interface (placeholder until VoiceManager is implemented)
 */
interface VoiceManager {
  speak(message: string): Promise<void>
  ask(question: string): Promise<string>
}

/**
 * IntentRegistry interface (placeholder until IntentRegistry is implemented)
 */
interface IntentRegistry {
  launchIntent(intentName: string, parameters: Record<string, string>): Promise<boolean>
}

export class ActionExecutor {
  private dynamicToolGenerator: DynamicToolGenerator
  private circuitBreaker: CircuitBreaker
  private mcpAdapter: MCPToolAdapter
  private actionMetrics: Map<string, { count: number; totalTime: number; failures: number }> = new Map()

  constructor(
    private nativeModule: AccessibilityModule,
    private fileSystem: FileSystem,
    private voiceManager?: VoiceManager,
    private intentRegistry?: IntentRegistry
  ) {
    // Initialize tool registry
    ToolRegistry.initialize()

    // Initialize dynamic tool generator
    this.dynamicToolGenerator = new DynamicToolGenerator(nativeModule, fileSystem)
    this.dynamicToolGenerator.loadTools().catch(console.error)

    // Initialize circuit breaker
    this.circuitBreaker = new CircuitBreaker(5, 60000, 30000)

    // Initialize MCP adapter with built-in tools
    this.mcpAdapter = new MCPToolAdapter()
    for (const tool of BuiltInMCPTools.getAllTools()) {
      this.mcpAdapter.registerTool(tool)
    }
  }

  /**
   * Execute a single action and return the result
   */
  async execute(action: Action, screenState: ScreenState): Promise<ActionResult> {
    const startTime = Date.now()
    let result: ActionResult

    try {
      // Use circuit breaker for critical actions
      result = await this.circuitBreaker.execute(async () => {
        return await this.executeInternal(action, screenState)
      })

      // Record success metric
      const duration = Date.now() - startTime
      this.recordMetric(action.type, duration, false)

      return result
    } catch (error) {
      // Record failure metric
      const duration = Date.now() - startTime
      this.recordMetric(action.type, duration, true)

      // Handle all errors and format for LLM context
      return this.handleError(error, action)
    }
  }

  /**
   * Internal execution method (wrapped by circuit breaker)
   */
  private async executeInternal(action: Action, screenState: ScreenState): Promise<ActionResult> {
    try {
      switch (action.type) {
        // ====================================================================
        // Element Interaction Actions (7.1)
        // ====================================================================
        
        case 'tap_element':
          return await this.executeTapElement(action.elementId, screenState)
        
        case 'long_press_element':
          return await this.executeLongPressElement(action.elementId, screenState)
        
        case 'tap_element_input_text_and_enter':
          return await this.executeTapElementInputTextAndEnter(
            action.index,
            action.text,
            screenState
          )
        
        case 'type':
          return await this.executeType(action.text)
        
        case 'swipe_down':
          return await this.executeSwipeDown(action.amount)
        
        case 'swipe_up':
          return await this.executeSwipeUp(action.amount)
        
        case 'back':
          return await this.executeBack()
        
        case 'home':
          return await this.executeHome()
        
        case 'switch_app':
          return await this.executeSwitchApp()
        
        case 'wait':
          return await this.executeWait()
        
        // ====================================================================
        // App and Search Actions (7.2)
        // ====================================================================
        
        case 'open_app':
          return await this.executeOpenApp(action.appName)
        
        case 'search_google':
          return await this.executeSearchGoogle(action.query)
        
        // ====================================================================
        // File System Actions (7.3)
        // ====================================================================
        
        case 'write_file':
          return await this.executeWriteFile(action.fileName, action.content)
        
        case 'append_file':
          return await this.executeAppendFile(action.fileName, action.content)
        
        case 'read_file':
          return await this.executeReadFile(action.fileName)
        
        // ====================================================================
        // Voice Actions (7.4)
        // ====================================================================
        
        case 'speak':
          return await this.executeSpeak(action.message)
        
        case 'ask':
          return await this.executeAsk(action.question)
        
        // ====================================================================
        // Special Actions (7.5)
        // ====================================================================
        
        case 'launch_intent':
          return await this.executeLaunchIntent(action.intentName, action.parameters)
        
        case 'done':
          return await this.executeDone(action.success, action.text, action.filesToDisplay)
        
        // ====================================================================
        // Extended Actions (Additional Tools)
        // ====================================================================
        
        case 'take_screenshot':
          return await this.executeTakeScreenshot(action.fileName)
        
        case 'get_clipboard':
          return await this.executeGetClipboard()
        
        case 'set_clipboard':
          return await this.executeSetClipboard(action.text)
        
        case 'get_installed_apps':
          return await this.executeGetInstalledApps()
        
        case 'get_current_app':
          return await this.executeGetCurrentApp()
        
        case 'send_notification':
          return await this.executeSendNotification(action.title, action.message)
        
        case 'list_files':
          return await this.executeListFiles()
        
        case 'delete_file':
          return await this.executeDeleteFile(action.fileName)
        
        // ====================================================================
        // Dynamic Tool Actions
        // ====================================================================
        
        case 'generate_tool':
          return await this.executeGenerateTool(
            action.toolName,
            action.description,
            action.parameters,
            action.intent
          )
        
        case 'execute_dynamic_tool':
          return await this.dynamicToolGenerator.executeTool(
            action.toolName,
            action.parameters,
            screenState
          )
        
        default:
          // TypeScript should prevent this, but handle unknown actions
          const unknownAction = action as any
          
          // Check if it's an MCP tool
          const mcpTool = this.mcpAdapter.getTool(unknownAction.type)
          if (mcpTool) {
            return await this.mcpAdapter.executeTool(unknownAction.type, unknownAction, {
              screenState,
              workspaceDir: this.fileSystem.getWorkspaceDir(),
            })
          }

          return {
            error: `Unknown action type: ${unknownAction.type}`,
          }
      }
    } catch (error) {
      throw error // Let outer execute() handle it
    }
  }

  // ==========================================================================
  // Element Interaction Actions (7.1)
  // ==========================================================================

  private async executeTapElement(elementId: number, screenState: ScreenState): Promise<ActionResult> {
    const element = screenState.elementMap.get(elementId)
    
    if (!element) {
      return {
        error: `Element with ID ${elementId} not found in current screen state. Available elements: ${Array.from(screenState.elementMap.keys()).join(', ')}`,
      }
    }

    const success = await this.nativeModule.clickOnPoint(element.center.x, element.center.y)
    
    if (!success) {
      return {
        error: `Failed to tap element ${elementId} at coordinates (${element.center.x}, ${element.center.y})`,
      }
    }

    return {
      longTermMemory: `Tapped element ${elementId}: ${element.description}`,
    }
  }

  private async executeLongPressElement(
    elementId: number,
    screenState: ScreenState
  ): Promise<ActionResult> {
    const element = screenState.elementMap.get(elementId)
    
    if (!element) {
      return {
        error: `Element with ID ${elementId} not found in current screen state. Available elements: ${Array.from(screenState.elementMap.keys()).join(', ')}`,
      }
    }

    const success = await this.nativeModule.longPressOnPoint(element.center.x, element.center.y)
    
    if (!success) {
      return {
        error: `Failed to long press element ${elementId} at coordinates (${element.center.x}, ${element.center.y})`,
      }
    }

    return {
      longTermMemory: `Long pressed element ${elementId}: ${element.description}`,
    }
  }

  private async executeTapElementInputTextAndEnter(
    index: number,
    text: string,
    screenState: ScreenState
  ): Promise<ActionResult> {
    // First tap the element
    const tapResult = await this.executeTapElement(index, screenState)
    if (tapResult.error) {
      return tapResult
    }

    // Wait for keyboard to appear
    await this.delay(500)

    // Type the text
    const typeSuccess = await this.nativeModule.typeText(text)
    if (!typeSuccess) {
      return {
        error: `Failed to type text: ${text}`,
      }
    }

    // Press enter
    const enterSuccess = await this.nativeModule.pressEnter()
    if (!enterSuccess) {
      return {
        error: `Failed to press enter after typing text`,
      }
    }

    return {
      longTermMemory: `Tapped element ${index}, typed "${text}", and pressed enter`,
    }
  }

  private async executeType(text: string): Promise<ActionResult> {
    const success = await this.nativeModule.typeText(text)
    
    if (!success) {
      return {
        error: `Failed to type text: ${text}`,
      }
    }

    return {
      longTermMemory: `Typed text: ${text}`,
    }
  }

  private async executeSwipeDown(amount: number): Promise<ActionResult> {
    const success = await this.nativeModule.scroll('down', amount)
    
    if (!success) {
      return {
        error: `Failed to swipe down by ${amount} pixels`,
      }
    }

    return {
      longTermMemory: `Swiped down by ${amount} pixels`,
    }
  }

  private async executeSwipeUp(amount: number): Promise<ActionResult> {
    const success = await this.nativeModule.scroll('up', amount)
    
    if (!success) {
      return {
        error: `Failed to swipe up by ${amount} pixels`,
      }
    }

    return {
      longTermMemory: `Swiped up by ${amount} pixels`,
    }
  }

  private async executeBack(): Promise<ActionResult> {
    const success = await this.nativeModule.performBack()
    
    if (!success) {
      return {
        error: `Failed to perform back action`,
      }
    }

    return {
      longTermMemory: `Pressed back button`,
    }
  }

  private async executeHome(): Promise<ActionResult> {
    const success = await this.nativeModule.performHome()
    
    if (!success) {
      return {
        error: `Failed to perform home action`,
      }
    }

    return {
      longTermMemory: `Pressed home button`,
    }
  }

  private async executeSwitchApp(): Promise<ActionResult> {
    const success = await this.nativeModule.performRecents()
    
    if (!success) {
      return {
        error: `Failed to open app switcher`,
      }
    }

    return {
      longTermMemory: `Opened app switcher (recents)`,
    }
  }

  private async executeWait(): Promise<ActionResult> {
    await this.delay(1000)
    
    return {
      longTermMemory: `Waited for 1 second`,
    }
  }

  // ==========================================================================
  // App and Search Actions (7.2)
  // ==========================================================================

  private async executeOpenApp(appName: string): Promise<ActionResult> {
    // First check if it's already a package name (contains dots)
    let packageName: string | null = null
    
    if (appName.includes('.')) {
      packageName = appName
    } else {
      // Query PackageManager to find app by label
      packageName = await this.nativeModule.findPackageByAppName(appName)
    }
    
    if (!packageName) {
      return {
        error: `App '${appName}' not found. Maybe try using different name or use app drawer by scrolling up.`,
      }
    }

    const success = await this.nativeModule.openApp(packageName)
    
    if (!success) {
      return {
        error: `Failed to open app '${appName}' (package: ${packageName}). Maybe try using different name or use app drawer by scrolling up.`,
      }
    }

    return {
      longTermMemory: `Opened app: ${appName}`,
    }
  }

  private async executeSearchGoogle(query: string): Promise<ActionResult> {
    // Open Chrome
    const chromePackage = 'com.android.chrome'
    const chromeSuccess = await this.nativeModule.openApp(chromePackage)
    
    if (!chromeSuccess) {
      return {
        error: `Failed to open Chrome for Google search`,
      }
    }

    // Wait for Chrome to open
    await this.delay(1500)

    // Type the search query (Chrome should focus on address bar)
    const typeSuccess = await this.nativeModule.typeText(query)
    
    if (!typeSuccess) {
      return {
        error: `Failed to type search query: ${query}`,
      }
    }

    // Press enter to search
    await this.delay(300)
    const enterSuccess = await this.nativeModule.pressEnter()
    
    if (!enterSuccess) {
      return {
        error: `Failed to press enter after typing search query`,
      }
    }

    return {
      longTermMemory: `Searched Google for: ${query}`,
    }
  }

  // ==========================================================================
  // File System Actions (7.3)
  // ==========================================================================

  private async executeWriteFile(fileName: string, content: string): Promise<ActionResult> {
    try {
      const success = await this.fileSystem.writeFile(fileName, content)
      
      if (!success) {
        return {
          error: `Failed to write file: ${fileName}`,
        }
      }

      return {
        longTermMemory: `Wrote file: ${fileName} (${content.length} characters)`,
      }
    } catch (error) {
      return {
        error: `Error writing file ${fileName}: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeAppendFile(fileName: string, content: string): Promise<ActionResult> {
    try {
      const success = await this.fileSystem.appendFile(fileName, content)
      
      if (!success) {
        return {
          error: `Failed to append to file: ${fileName}`,
        }
      }

      return {
        longTermMemory: `Appended to file: ${fileName} (${content.length} characters)`,
      }
    } catch (error) {
      return {
        error: `Error appending to file ${fileName}: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeReadFile(fileName: string): Promise<ActionResult> {
    try {
      const { content, lineCount } = await this.fileSystem.readFile(fileName)
      
      return {
        extractedContent: `File: ${fileName} (${lineCount} lines)\n\n${content}`,
        includeExtractedContentOnlyOnce: true,
        longTermMemory: `Read file: ${fileName} (${lineCount} lines)`,
      }
    } catch (error) {
      return {
        error: `Error reading file ${fileName}: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  // ==========================================================================
  // Voice Actions (7.4)
  // ==========================================================================

  private async executeSpeak(message: string): Promise<ActionResult> {
    if (!this.voiceManager) {
      return {
        error: `Voice manager not available. Cannot speak message.`,
      }
    }

    try {
      await this.voiceManager.speak(message)
      
      return {
        longTermMemory: `Spoke: "${message}"`,
      }
    } catch (error) {
      return {
        error: `Error speaking message: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeAsk(question: string): Promise<ActionResult> {
    if (!this.voiceManager) {
      return {
        error: `Voice manager not available. Cannot ask question.`,
      }
    }

    try {
      const answer = await this.voiceManager.ask(question)
      
      return {
        extractedContent: `User answered: ${answer}`,
        includeExtractedContentOnlyOnce: true,
        longTermMemory: `Asked: "${question}", User answered: "${answer}"`,
      }
    } catch (error) {
      return {
        error: `Error asking question: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  // ==========================================================================
  // Special Actions (7.5)
  // ==========================================================================

  private async executeLaunchIntent(
    intentName: string,
    parameters: Record<string, string>
  ): Promise<ActionResult> {
    if (!this.intentRegistry) {
      return {
        error: `Intent registry not available. Cannot launch intent: ${intentName}`,
      }
    }

    try {
      const success = await this.intentRegistry.launchIntent(intentName, parameters)
      
      if (!success) {
        return {
          error: `Failed to launch intent: ${intentName}`,
        }
      }

      return {
        longTermMemory: `Launched intent: ${intentName} with parameters: ${JSON.stringify(parameters)}`,
      }
    } catch (error) {
      return {
        error: `Error launching intent ${intentName}: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeDone(
    success: boolean,
    text: string,
    filesToDisplay?: string[]
  ): Promise<ActionResult> {
    return {
      isDone: true,
      success,
      longTermMemory: text,
      attachments: filesToDisplay,
    }
  }

  // ==========================================================================
  // Error Handling (7.6)
  // ==========================================================================

  private handleError(error: unknown, action: Action): ActionResult {
    let errorMessage: string

    if (error instanceof QueError) {
      // Handle QueError with category information
      errorMessage = `[${error.category}] ${error.message}`
      
      if (error.details) {
        errorMessage += ` | Details: ${JSON.stringify(error.details)}`
      }
    } else if (error instanceof Error) {
      errorMessage = error.message
    } else {
      errorMessage = String(error)
    }

    // Format error message for LLM context
    const formattedError = `Action "${action.type}" failed: ${errorMessage}`

    return {
      error: formattedError,
    }
  }

  // ==========================================================================
  // Extended Actions
  // ==========================================================================

  private async executeTakeScreenshot(fileName?: string): Promise<ActionResult> {
    try {
      const name = fileName || `screenshot_${Date.now()}.txt`
      
      // Get current screen state as text representation
      const hierarchy = await this.nativeModule.dumpHierarchy()
      await this.fileSystem.writeFile(name, hierarchy)

      return {
        longTermMemory: `Saved screenshot to: ${name}`,
      }
    } catch (error) {
      return {
        error: `Failed to take screenshot: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeGetClipboard(): Promise<ActionResult> {
    // Note: React Native doesn't have direct clipboard access in native modules
    // This would need to be implemented via a separate clipboard module
    return {
      error: 'Clipboard access not yet implemented. Requires additional native module.',
    }
  }

  private async executeSetClipboard(_text: string): Promise<ActionResult> {
    // Note: React Native doesn't have direct clipboard access in native modules
    // This would need to be implemented via a separate clipboard module
    return {
      error: 'Clipboard access not yet implemented. Requires additional native module.',
    }
  }

  private async executeGetInstalledApps(): Promise<ActionResult> {
    try {
      // This would require a native method to list all installed apps
      // For now, return a placeholder
      return {
        error: 'Get installed apps not yet implemented. Requires additional native method.',
      }
    } catch (error) {
      return {
        error: `Failed to get installed apps: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeGetCurrentApp(): Promise<ActionResult> {
    try {
      const activity = await this.nativeModule.getCurrentActivity()
      
      return {
        extractedContent: `Current app activity: ${activity}`,
        includeExtractedContentOnlyOnce: true,
        longTermMemory: `Retrieved current app: ${activity}`,
      }
    } catch (error) {
      return {
        error: `Failed to get current app: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeSendNotification(_title: string, _message: string): Promise<ActionResult> {
    // Note: Sending notifications would require additional native implementation
    return {
      error: 'Send notification not yet implemented. Requires additional native module.',
    }
  }

  private async executeListFiles(): Promise<ActionResult> {
    try {
      const files = await this.fileSystem.listFiles()
      const fileList = files.join('\n')

      return {
        extractedContent: `Files in workspace:\n${fileList}`,
        includeExtractedContentOnlyOnce: true,
        longTermMemory: `Listed ${files.length} files in workspace`,
      }
    } catch (error) {
      return {
        error: `Failed to list files: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  private async executeDeleteFile(_fileName: string): Promise<ActionResult> {
    try {
      // FileSystem doesn't have delete method yet - would need to add it
      return {
        error: 'Delete file not yet implemented. Requires FileSystem.deleteFile() method.',
      }
    } catch (error) {
      return {
        error: `Failed to delete file: ${error instanceof Error ? error.message : String(error)}`,
      }
    }
  }

  // ==========================================================================
  // Dynamic Tool Actions
  // ==========================================================================

  private async executeGenerateTool(
    toolName: string,
    description: string,
    parameters: Array<{
      name: string
      type: 'string' | 'number' | 'boolean' | 'object'
      description: string
      required: boolean
    }>,
    intent: string
  ): Promise<ActionResult> {
    const result = await this.dynamicToolGenerator.generateTool({
      toolName,
      description,
      parameters,
      intent,
    })

    if (result.success) {
      return {
        longTermMemory: `Generated new tool: ${toolName}. You can now use it with execute_dynamic_tool action.`,
      }
    } else {
      return {
        error: result.error || 'Failed to generate tool',
      }
    }
  }

  /**
   * Get all available dynamic tools (for prompt building)
   */
  getDynamicTools() {
    return this.dynamicToolGenerator.getAvailableTools()
  }

  /**
   * Get all available tools (predefined + dynamic + MCP)
   */
  getAllAvailableTools() {
    return {
      predefined: ToolRegistry.getAll(),
      dynamic: this.dynamicToolGenerator.getAvailableTools(),
      mcp: this.mcpAdapter.toToolSpecs(),
    }
  }

  /**
   * Get tool registry for documentation generation
   */
  getToolRegistry() {
    return ToolRegistry
  }

  /**
   * Register an MCP tool
   */
  registerMCPTool(tool: Parameters<typeof this.mcpAdapter.registerTool>[0]) {
    this.mcpAdapter.registerTool(tool)
  }

  /**
   * Execute an MCP tool
   */
  async executeMCPTool(
    toolName: string,
    params: Record<string, any>,
    screenState: ScreenState
  ): Promise<ActionResult> {
    return this.mcpAdapter.executeTool(toolName, params, {
      screenState,
      workspaceDir: this.fileSystem.getWorkspaceDir(),
    })
  }

  /**
   * Get action execution metrics
   */
  getMetrics() {
    const metrics: Record<string, any> = {}
    
    for (const [action, data] of this.actionMetrics.entries()) {
      metrics[action] = {
        count: data.count,
        avgTime: data.totalTime / data.count,
        failures: data.failures,
        successRate: ((data.count - data.failures) / data.count) * 100,
      }
    }

    return metrics
  }

  /**
   * Get circuit breaker state
   */
  getCircuitBreakerState() {
    return this.circuitBreaker.getState()
  }

  /**
   * Reset circuit breaker
   */
  resetCircuitBreaker() {
    this.circuitBreaker.reset()
  }

  /**
   * Get MCP tool call history
   */
  getMCPHistory(limit?: number) {
    return this.mcpAdapter.getHistory(limit)
  }

  /**
   * Generate tool documentation
   */
  generateToolDocumentation(): string {
    let docs = ToolRegistry.toMarkdown()

    // Add dynamic tools
    const dynamicTools = this.dynamicToolGenerator.getAvailableTools()
    if (dynamicTools.length > 0) {
      docs += '\n## Dynamic Tools\n\n'
      for (const tool of dynamicTools) {
        docs += `### ${tool.name}\n\n`
        docs += `${tool.description}\n\n`
        docs += `**Usage count:** ${tool.usageCount}\n\n`
      }
    }

    // Add MCP tools
    const mcpTools = this.mcpAdapter.getTools()
    if (mcpTools.length > 0) {
      docs += '\n## MCP Tools\n\n'
      for (const tool of mcpTools) {
        docs += `### ${tool.name}\n\n`
        docs += `${tool.description}\n\n`
      }
    }

    return docs
  }

  // ==========================================================================
  // Helper Methods
  // ==========================================================================

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
  }

  private recordMetric(actionType: string, duration: number, failed: boolean = false) {
    const existing = this.actionMetrics.get(actionType) || { count: 0, totalTime: 0, failures: 0 }
    
    this.actionMetrics.set(actionType, {
      count: existing.count + 1,
      totalTime: existing.totalTime + duration,
      failures: existing.failures + (failed ? 1 : 0),
    })
  }
}
