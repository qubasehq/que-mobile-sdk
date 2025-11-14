/**
 * Unit tests for ActionExecutor
 * Tests action mapping and execution logic
 */

import { ActionExecutor } from '../ActionExecutor'
import { AccessibilityModule } from '../../native/AccessibilityModule'
import { FileSystem } from '../../memory/FileSystem'
import { ScreenState, Element } from '../../types'

// Mock dependencies
jest.mock('../../native/AccessibilityModule')
jest.mock('../../memory/FileSystem')

describe('ActionExecutor', () => {
  let executor: ActionExecutor
  let mockNativeModule: jest.Mocked<AccessibilityModule>
  let mockFileSystem: jest.Mocked<FileSystem>
  let mockScreenState: ScreenState

  beforeEach(() => {
    mockNativeModule = {
      clickOnPoint: jest.fn().mockResolvedValue(true),
      longPressOnPoint: jest.fn().mockResolvedValue(true),
      typeText: jest.fn().mockResolvedValue(true),
      scroll: jest.fn().mockResolvedValue(true),
      performBack: jest.fn().mockResolvedValue(true),
      performHome: jest.fn().mockResolvedValue(true),
      performRecents: jest.fn().mockResolvedValue(true),
      pressEnter: jest.fn().mockResolvedValue(true),
      openApp: jest.fn().mockResolvedValue(true),
      findPackageByAppName: jest.fn().mockResolvedValue('com.example.app'),
      getCurrentActivity: jest.fn().mockResolvedValue('com.example.MainActivity'),
      dumpHierarchy: jest.fn().mockResolvedValue('<hierarchy></hierarchy>'),
    } as any

    mockFileSystem = {
      writeFile: jest.fn().mockResolvedValue(true),
      appendFile: jest.fn().mockResolvedValue(true),
      readFile: jest.fn().mockResolvedValue({ content: 'file content', lineCount: 10 }),
      getWorkspaceDir: jest.fn().mockReturnValue('/workspace'),
    } as any

    executor = new ActionExecutor(mockNativeModule, mockFileSystem)

    // Create mock screen state
    const element1: Element = {
      id: 0,
      description: 'Button 1',
      bounds: '[0,0][100,100]',
      center: { x: 50, y: 50 },
      isClickable: true,
    }

    const element2: Element = {
      id: 1,
      description: 'Button 2',
      bounds: '[100,0][200,100]',
      center: { x: 150, y: 50 },
      isClickable: true,
    }

    mockScreenState = {
      uiRepresentation: 'Mock UI',
      isKeyboardOpen: false,
      activityName: 'MainActivity',
      elementMap: new Map([
        [0, element1],
        [1, element2],
      ]),
      scrollUp: 0,
      scrollDown: 0,
    }
  })

  describe('tap_element action', () => {
    it('should tap element at correct coordinates', async () => {
      const action = { type: 'tap_element' as const, elementId: 0 }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.clickOnPoint).toHaveBeenCalledWith(50, 50)
      expect(result.error).toBeUndefined()
      expect(result.longTermMemory).toContain('Tapped element 0')
    })

    it('should return error if element not found', async () => {
      const action = { type: 'tap_element' as const, elementId: 999 }

      const result = await executor.execute(action, mockScreenState)

      expect(result.error).toContain('Element with ID 999 not found')
    })

    it('should return error if tap fails', async () => {
      mockNativeModule.clickOnPoint.mockResolvedValue(false)
      const action = { type: 'tap_element' as const, elementId: 0 }

      const result = await executor.execute(action, mockScreenState)

      expect(result.error).toContain('Failed to tap element')
    })
  })

  describe('long_press_element action', () => {
    it('should long press element at correct coordinates', async () => {
      const action = { type: 'long_press_element' as const, elementId: 1 }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.longPressOnPoint).toHaveBeenCalledWith(150, 50)
      expect(result.error).toBeUndefined()
      expect(result.longTermMemory).toContain('Long pressed element 1')
    })
  })

  describe('type action', () => {
    it('should type text', async () => {
      const action = { type: 'type' as const, text: 'Hello World' }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.typeText).toHaveBeenCalledWith('Hello World')
      expect(result.error).toBeUndefined()
      expect(result.longTermMemory).toContain('Typed text: Hello World')
    })
  })

  describe('swipe actions', () => {
    it('should swipe down', async () => {
      const action = { type: 'swipe_down' as const, amount: 500 }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.scroll).toHaveBeenCalledWith('down', 500)
      expect(result.error).toBeUndefined()
    })

    it('should swipe up', async () => {
      const action = { type: 'swipe_up' as const, amount: 300 }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.scroll).toHaveBeenCalledWith('up', 300)
      expect(result.error).toBeUndefined()
    })
  })

  describe('system actions', () => {
    it('should perform back action', async () => {
      const action = { type: 'back' as const }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.performBack).toHaveBeenCalled()
      expect(result.error).toBeUndefined()
    })

    it('should perform home action', async () => {
      const action = { type: 'home' as const }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.performHome).toHaveBeenCalled()
      expect(result.error).toBeUndefined()
    })

    it('should perform switch app action', async () => {
      const action = { type: 'switch_app' as const }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.performRecents).toHaveBeenCalled()
      expect(result.error).toBeUndefined()
    })
  })

  describe('wait action', () => {
    it('should wait for specified time', async () => {
      const action = { type: 'wait' as const }
      const startTime = Date.now()

      const result = await executor.execute(action, mockScreenState)

      const elapsed = Date.now() - startTime
      expect(elapsed).toBeGreaterThanOrEqual(900) // Allow some margin
      expect(result.error).toBeUndefined()
    })
  })

  describe('open_app action', () => {
    it('should open app by name', async () => {
      const action = { type: 'open_app' as const, appName: 'Chrome' }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.findPackageByAppName).toHaveBeenCalledWith('Chrome')
      expect(mockNativeModule.openApp).toHaveBeenCalledWith('com.example.app')
      expect(result.error).toBeUndefined()
    })

    it('should open app by package name', async () => {
      const action = { type: 'open_app' as const, appName: 'com.android.chrome' }

      const result = await executor.execute(action, mockScreenState)

      expect(mockNativeModule.openApp).toHaveBeenCalledWith('com.android.chrome')
      expect(result.error).toBeUndefined()
    })

    it('should return error if app not found', async () => {
      mockNativeModule.findPackageByAppName.mockResolvedValue(null)
      const action = { type: 'open_app' as const, appName: 'NonExistent' }

      const result = await executor.execute(action, mockScreenState)

      expect(result.error).toContain('App \'NonExistent\' not found')
    })
  })

  describe('file system actions', () => {
    it('should write file', async () => {
      const action = {
        type: 'write_file' as const,
        fileName: 'test.md',
        content: 'Test content',
      }

      const result = await executor.execute(action, mockScreenState)

      expect(mockFileSystem.writeFile).toHaveBeenCalledWith('test.md', 'Test content')
      expect(result.error).toBeUndefined()
      expect(result.longTermMemory).toContain('Wrote file: test.md')
    })

    it('should append to file', async () => {
      const action = {
        type: 'append_file' as const,
        fileName: 'test.md',
        content: 'More content',
      }

      const result = await executor.execute(action, mockScreenState)

      expect(mockFileSystem.appendFile).toHaveBeenCalledWith('test.md', 'More content')
      expect(result.error).toBeUndefined()
    })

    it('should read file', async () => {
      const action = { type: 'read_file' as const, fileName: 'test.md' }

      const result = await executor.execute(action, mockScreenState)

      expect(mockFileSystem.readFile).toHaveBeenCalledWith('test.md')
      expect(result.extractedContent).toContain('file content')
      expect(result.includeExtractedContentOnlyOnce).toBe(true)
    })
  })

  describe('done action', () => {
    it('should mark task as done with success', async () => {
      const action = {
        type: 'done' as const,
        success: true,
        text: 'Task completed',
        filesToDisplay: ['result.md'],
      }

      const result = await executor.execute(action, mockScreenState)

      expect(result.isDone).toBe(true)
      expect(result.success).toBe(true)
      expect(result.longTermMemory).toBe('Task completed')
      expect(result.attachments).toEqual(['result.md'])
    })

    it('should mark task as done with failure', async () => {
      const action = {
        type: 'done' as const,
        success: false,
        text: 'Task failed',
      }

      const result = await executor.execute(action, mockScreenState)

      expect(result.isDone).toBe(true)
      expect(result.success).toBe(false)
    })
  })

  describe('error handling', () => {
    it('should handle native module errors', async () => {
      mockNativeModule.clickOnPoint.mockRejectedValue(new Error('Native error'))
      const action = { type: 'tap_element' as const, elementId: 0 }

      const result = await executor.execute(action, mockScreenState)

      expect(result.error).toContain('Native error')
    })

    it('should handle file system errors', async () => {
      mockFileSystem.writeFile.mockRejectedValue(new Error('Write failed'))
      const action = {
        type: 'write_file' as const,
        fileName: 'test.md',
        content: 'content',
      }

      const result = await executor.execute(action, mockScreenState)

      expect(result.error).toContain('Write failed')
    })
  })
})
