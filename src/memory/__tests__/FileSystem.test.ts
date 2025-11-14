/**
 * Unit tests for FileSystem
 * Tests file operations, validation, and archiving
 */

import { FileSystem } from '../FileSystem'
import RNFS from 'react-native-fs'
import { QueError } from '../../utils/errors'

// Mock react-native-fs
jest.mock('react-native-fs')

describe('FileSystem', () => {
  let fileSystem: FileSystem
  const mockWorkspaceDir = '/mock/workspace'

  beforeEach(() => {
    jest.clearAllMocks()
    fileSystem = new FileSystem(mockWorkspaceDir)

    // Setup default mocks
    ;(RNFS.exists as jest.Mock).mockResolvedValue(false)
    ;(RNFS.mkdir as jest.Mock).mockResolvedValue(undefined)
    ;(RNFS.writeFile as jest.Mock).mockResolvedValue(undefined)
    ;(RNFS.appendFile as jest.Mock).mockResolvedValue(undefined)
    ;(RNFS.readFile as jest.Mock).mockResolvedValue('file content')
    ;(RNFS.readDir as jest.Mock).mockResolvedValue([])
    ;(RNFS.moveFile as jest.Mock).mockResolvedValue(undefined)
  })

  describe('initialize()', () => {
    it('should create workspace directory if not exists', async () => {
      await fileSystem.initialize()

      expect(RNFS.mkdir).toHaveBeenCalledWith(mockWorkspaceDir)
    })

    it('should create archive directory', async () => {
      await fileSystem.initialize()

      expect(RNFS.mkdir).toHaveBeenCalledWith(`${mockWorkspaceDir}/archive`)
    })

    it('should create default files', async () => {
      await fileSystem.initialize()

      expect(RNFS.writeFile).toHaveBeenCalledWith(
        `${mockWorkspaceDir}/todo.md`,
        '# Task List\n\n',
        'utf8'
      )
      expect(RNFS.writeFile).toHaveBeenCalledWith(
        `${mockWorkspaceDir}/results.md`,
        '# Results\n\n',
        'utf8'
      )
    })

    it('should not recreate existing directories', async () => {
      ;(RNFS.exists as jest.Mock).mockResolvedValue(true)

      await fileSystem.initialize()

      expect(RNFS.mkdir).not.toHaveBeenCalledWith(mockWorkspaceDir)
    })

    it('should set initialized flag', async () => {
      await fileSystem.initialize()

      expect(fileSystem.isInitialized()).toBe(true)
    })

    it('should throw QueError on failure', async () => {
      ;(RNFS.mkdir as jest.Mock).mockRejectedValue(new Error('Permission denied'))

      await expect(fileSystem.initialize()).rejects.toThrow(QueError)
    })
  })

  describe('writeFile()', () => {
    beforeEach(async () => {
      await fileSystem.initialize()
    })

    it('should write file with valid extension', async () => {
      const result = await fileSystem.writeFile('test.md', 'content')

      expect(result).toBe(true)
      expect(RNFS.writeFile).toHaveBeenCalledWith(
        `${mockWorkspaceDir}/test.md`,
        'content',
        'utf8'
      )
    })

    it('should accept .txt extension', async () => {
      const result = await fileSystem.writeFile('notes.txt', 'notes')

      expect(result).toBe(true)
      expect(RNFS.writeFile).toHaveBeenCalledWith(
        `${mockWorkspaceDir}/notes.txt`,
        'notes',
        'utf8'
      )
    })

    it('should reject invalid file extensions', async () => {
      await expect(fileSystem.writeFile('script.js', 'code')).rejects.toThrow(QueError)
      await expect(fileSystem.writeFile('data.json', '{}')).rejects.toThrow(QueError)
    })

    it('should reject path traversal attempts', async () => {
      await expect(fileSystem.writeFile('../etc/passwd', 'hack')).rejects.toThrow(QueError)
      await expect(fileSystem.writeFile('../../file.md', 'content')).rejects.toThrow(QueError)
    })

    it('should throw error if not initialized', async () => {
      const uninitializedFS = new FileSystem(mockWorkspaceDir)

      await expect(uninitializedFS.writeFile('test.md', 'content')).rejects.toThrow(QueError)
    })
  })

  describe('appendFile()', () => {
    beforeEach(async () => {
      await fileSystem.initialize()
    })

    it('should append to existing file', async () => {
      ;(RNFS.exists as jest.Mock).mockResolvedValue(true)

      const result = await fileSystem.appendFile('test.md', 'more content')

      expect(result).toBe(true)
      expect(RNFS.appendFile).toHaveBeenCalledWith(
        `${mockWorkspaceDir}/test.md`,
        'more content',
        'utf8'
      )
    })

    it('should create new file if not exists', async () => {
      ;(RNFS.exists as jest.Mock).mockResolvedValue(false)

      const result = await fileSystem.appendFile('new.md', 'content')

      expect(result).toBe(true)
      expect(RNFS.writeFile).toHaveBeenCalledWith(
        `${mockWorkspaceDir}/new.md`,
        'content',
        'utf8'
      )
    })

    it('should reject invalid file extensions', async () => {
      await expect(fileSystem.appendFile('file.pdf', 'content')).rejects.toThrow(QueError)
    })

    it('should reject path traversal', async () => {
      await expect(fileSystem.appendFile('../file.md', 'content')).rejects.toThrow(QueError)
    })
  })

  describe('readFile()', () => {
    beforeEach(async () => {
      await fileSystem.initialize()
    })

    it('should read file and return content with line count', async () => {
      ;(RNFS.exists as jest.Mock).mockResolvedValue(true)
      ;(RNFS.readFile as jest.Mock).mockResolvedValue('line1\nline2\nline3')

      const result = await fileSystem.readFile('test.md')

      expect(result.content).toBe('line1\nline2\nline3')
      expect(result.lineCount).toBe(3)
    })

    it('should throw error if file not found', async () => {
      ;(RNFS.exists as jest.Mock).mockResolvedValue(false)

      await expect(fileSystem.readFile('missing.md')).rejects.toThrow(QueError)
    })

    it('should reject path traversal', async () => {
      await expect(fileSystem.readFile('../file.md')).rejects.toThrow(QueError)
    })

    it('should handle single line files', async () => {
      ;(RNFS.exists as jest.Mock).mockResolvedValue(true)
      ;(RNFS.readFile as jest.Mock).mockResolvedValue('single line')

      const result = await fileSystem.readFile('test.md')

      expect(result.lineCount).toBe(1)
    })
  })

  describe('listFiles()', () => {
    beforeEach(async () => {
      await fileSystem.initialize()
    })

    it('should list all files in workspace', async () => {
      ;(RNFS.readDir as jest.Mock).mockResolvedValue([
        { name: 'file1.md', isFile: () => true },
        { name: 'file2.txt', isFile: () => true },
        { name: 'archive', isFile: () => false },
      ])

      const files = await fileSystem.listFiles()

      expect(files).toEqual(['file1.md', 'file2.txt'])
    })

    it('should filter out directories', async () => {
      ;(RNFS.readDir as jest.Mock).mockResolvedValue([
        { name: 'file.md', isFile: () => true },
        { name: 'folder', isFile: () => false },
      ])

      const files = await fileSystem.listFiles()

      expect(files).toEqual(['file.md'])
    })

    it('should return empty array if no files', async () => {
      ;(RNFS.readDir as jest.Mock).mockResolvedValue([])

      const files = await fileSystem.listFiles()

      expect(files).toEqual([])
    })
  })

  describe('archiveOldFiles()', () => {
    beforeEach(async () => {
      await fileSystem.initialize()
    })

    it('should move files to archive with timestamp', async () => {
      ;(RNFS.readDir as jest.Mock).mockResolvedValue([
        { name: 'todo.md', isFile: () => true },
        { name: 'results.md', isFile: () => true },
      ])

      await fileSystem.archiveOldFiles()

      expect(RNFS.moveFile).toHaveBeenCalledTimes(2)
      expect(RNFS.moveFile).toHaveBeenCalledWith(
        `${mockWorkspaceDir}/todo.md`,
        expect.stringContaining('archive/todo_')
      )
    })

    it('should skip hidden files', async () => {
      ;(RNFS.readDir as jest.Mock).mockResolvedValue([
        { name: '.hidden', isFile: () => true },
        { name: 'visible.md', isFile: () => true },
      ])

      await fileSystem.archiveOldFiles()

      expect(RNFS.moveFile).toHaveBeenCalledTimes(1)
    })

    it('should handle empty workspace', async () => {
      ;(RNFS.readDir as jest.Mock).mockResolvedValue([])

      await expect(fileSystem.archiveOldFiles()).resolves.not.toThrow()
    })
  })

  describe('getWorkspaceDir()', () => {
    it('should return workspace directory path', () => {
      expect(fileSystem.getWorkspaceDir()).toBe(mockWorkspaceDir)
    })
  })

  describe('isInitialized()', () => {
    it('should return false before initialization', () => {
      expect(fileSystem.isInitialized()).toBe(false)
    })

    it('should return true after initialization', async () => {
      await fileSystem.initialize()
      expect(fileSystem.isInitialized()).toBe(true)
    })
  })
})
