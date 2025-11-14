/**
 * FileSystem class for managing sandboxed file storage
 * Handles task tracking files (todo.md, results.md) with validation and archiving
 */

import RNFS from 'react-native-fs'
import { QueError, ErrorCategory } from '../utils/errors'

export class FileSystem {
  private workspaceDir: string
  private initialized: boolean = false

  constructor(workspaceDir?: string) {
    // Default to app's document directory + que-workspace
    this.workspaceDir = workspaceDir || `${RNFS.DocumentDirectoryPath}/que-workspace`
  }

  /**
   * Initialize the file system by creating workspace directory
   * Creates todo.md and results.md if they don't exist
   */
  async initialize(): Promise<void> {
    try {
      // Check if workspace directory exists
      const exists = await RNFS.exists(this.workspaceDir)
      
      if (!exists) {
        // Create workspace directory
        await RNFS.mkdir(this.workspaceDir)
      }

      // Create archive directory if it doesn't exist
      const archiveDir = `${this.workspaceDir}/archive`
      const archiveExists = await RNFS.exists(archiveDir)
      if (!archiveExists) {
        await RNFS.mkdir(archiveDir)
      }

      // Initialize default files if they don't exist
      const todoPath = `${this.workspaceDir}/todo.md`
      const resultsPath = `${this.workspaceDir}/results.md`

      if (!(await RNFS.exists(todoPath))) {
        await RNFS.writeFile(todoPath, '# Task List\n\n', 'utf8')
      }

      if (!(await RNFS.exists(resultsPath))) {
        await RNFS.writeFile(resultsPath, '# Results\n\n', 'utf8')
      }

      this.initialized = true
    } catch (error) {
      throw new QueError(
        `Failed to initialize file system: ${error instanceof Error ? error.message : String(error)}`,
        ErrorCategory.SYSTEM,
        false,
        { workspaceDir: this.workspaceDir, error }
      )
    }
  }

  /**
   * Write content to a file with validation
   * Only allows .md and .txt extensions
   */
  async writeFile(fileName: string, content: string): Promise<boolean> {
    this.ensureInitialized()

    try {
      // Validate file extension
      if (!this.isValidExtension(fileName)) {
        throw new QueError(
          `Invalid file extension. Only .md and .txt files are allowed. Got: ${fileName}`,
          ErrorCategory.SYSTEM,
          true,
          { fileName }
        )
      }

      // Validate file name doesn't contain path traversal
      if (this.containsPathTraversal(fileName)) {
        throw new QueError(
          `Invalid file name. Path traversal not allowed: ${fileName}`,
          ErrorCategory.SYSTEM,
          true,
          { fileName }
        )
      }

      const filePath = `${this.workspaceDir}/${fileName}`
      await RNFS.writeFile(filePath, content, 'utf8')
      
      return true
    } catch (error) {
      if (error instanceof QueError) {
        throw error
      }
      throw new QueError(
        `Failed to write file: ${error instanceof Error ? error.message : String(error)}`,
        ErrorCategory.SYSTEM,
        true,
        { fileName, error }
      )
    }
  }

  /**
   * Append content to an existing file
   * Creates the file if it doesn't exist
   */
  async appendFile(fileName: string, content: string): Promise<boolean> {
    this.ensureInitialized()

    try {
      // Validate file extension
      if (!this.isValidExtension(fileName)) {
        throw new QueError(
          `Invalid file extension. Only .md and .txt files are allowed. Got: ${fileName}`,
          ErrorCategory.SYSTEM,
          true,
          { fileName }
        )
      }

      // Validate file name doesn't contain path traversal
      if (this.containsPathTraversal(fileName)) {
        throw new QueError(
          `Invalid file name. Path traversal not allowed: ${fileName}`,
          ErrorCategory.SYSTEM,
          true,
          { fileName }
        )
      }

      const filePath = `${this.workspaceDir}/${fileName}`
      
      // Check if file exists
      const exists = await RNFS.exists(filePath)
      
      if (exists) {
        // Append to existing file
        await RNFS.appendFile(filePath, content, 'utf8')
      } else {
        // Create new file with content
        await RNFS.writeFile(filePath, content, 'utf8')
      }
      
      return true
    } catch (error) {
      if (error instanceof QueError) {
        throw error
      }
      throw new QueError(
        `Failed to append to file: ${error instanceof Error ? error.message : String(error)}`,
        ErrorCategory.SYSTEM,
        true,
        { fileName, error }
      )
    }
  }

  /**
   * Read file content with line count tracking
   * Returns content and line count
   */
  async readFile(fileName: string): Promise<{ content: string; lineCount: number }> {
    this.ensureInitialized()

    try {
      // Validate file name doesn't contain path traversal
      if (this.containsPathTraversal(fileName)) {
        throw new QueError(
          `Invalid file name. Path traversal not allowed: ${fileName}`,
          ErrorCategory.SYSTEM,
          true,
          { fileName }
        )
      }

      const filePath = `${this.workspaceDir}/${fileName}`
      
      // Check if file exists
      const exists = await RNFS.exists(filePath)
      if (!exists) {
        throw new QueError(
          `File not found: ${fileName}`,
          ErrorCategory.SYSTEM,
          true,
          { fileName, filePath }
        )
      }

      const content = await RNFS.readFile(filePath, 'utf8')
      const lineCount = content.split('\n').length
      
      return { content, lineCount }
    } catch (error) {
      if (error instanceof QueError) {
        throw error
      }
      throw new QueError(
        `Failed to read file: ${error instanceof Error ? error.message : String(error)}`,
        ErrorCategory.SYSTEM,
        true,
        { fileName, error }
      )
    }
  }

  /**
   * List all files in the workspace directory
   * Returns array of file names (not full paths)
   */
  async listFiles(): Promise<string[]> {
    this.ensureInitialized()

    try {
      const items = await RNFS.readDir(this.workspaceDir)
      
      // Filter to only include files (not directories) and exclude archive folder
      const files = items
        .filter(item => item.isFile())
        .map(item => item.name)
      
      return files
    } catch (error) {
      throw new QueError(
        `Failed to list files: ${error instanceof Error ? error.message : String(error)}`,
        ErrorCategory.SYSTEM,
        true,
        { workspaceDir: this.workspaceDir, error }
      )
    }
  }

  /**
   * Archive old files with timestamp suffixes
   * Moves files to archive/ subdirectory with timestamp
   */
  async archiveOldFiles(): Promise<void> {
    this.ensureInitialized()

    try {
      const files = await this.listFiles()
      const timestamp = this.getTimestamp()
      const archiveDir = `${this.workspaceDir}/archive`

      for (const fileName of files) {
        // Skip if already in archive or is a system file we want to keep fresh
        if (fileName.startsWith('.')) {
          continue
        }

        const sourcePath = `${this.workspaceDir}/${fileName}`
        const fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'))
        const extension = fileName.substring(fileName.lastIndexOf('.'))
        const archivedFileName = `${fileNameWithoutExt}_${timestamp}${extension}`
        const destPath = `${archiveDir}/${archivedFileName}`

        // Move file to archive
        await RNFS.moveFile(sourcePath, destPath)
      }
    } catch (error) {
      throw new QueError(
        `Failed to archive files: ${error instanceof Error ? error.message : String(error)}`,
        ErrorCategory.SYSTEM,
        false,
        { workspaceDir: this.workspaceDir, error }
      )
    }
  }

  /**
   * Get the workspace directory path
   */
  getWorkspaceDir(): string {
    return this.workspaceDir
  }

  /**
   * Check if file system is initialized
   */
  isInitialized(): boolean {
    return this.initialized
  }

  // ============================================================================
  // Private Helper Methods
  // ============================================================================

  private ensureInitialized(): void {
    if (!this.initialized) {
      throw new QueError(
        'FileSystem not initialized. Call initialize() first.',
        ErrorCategory.SYSTEM,
        true
      )
    }
  }

  private isValidExtension(fileName: string): boolean {
    const lowerFileName = fileName.toLowerCase()
    return lowerFileName.endsWith('.md') || lowerFileName.endsWith('.txt')
  }

  private containsPathTraversal(fileName: string): boolean {
    // Check for path traversal attempts
    return fileName.includes('..') || fileName.includes('/') || fileName.includes('\\')
  }

  private getTimestamp(): string {
    const now = new Date()
    const year = now.getFullYear()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    const hours = String(now.getHours()).padStart(2, '0')
    const minutes = String(now.getMinutes()).padStart(2, '0')
    const seconds = String(now.getSeconds()).padStart(2, '0')
    
    return `${year}${month}${day}_${hours}${minutes}${seconds}`
  }
}
