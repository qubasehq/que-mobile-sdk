/**
 * useFileSystem Hook - React hook for file operations
 * 
 * Wraps FileSystem to provide sandboxed file storage operations
 * with React state management.
 * 
 * Requirements: 1.1
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import { FileSystem } from '../memory/FileSystem'

// ============================================================================
// Hook Return Type
// ============================================================================

export interface UseFileSystemReturn {
  /** Write content to a file */
  writeFile: (fileName: string, content: string) => Promise<boolean>
  /** Append content to a file */
  appendFile: (fileName: string, content: string) => Promise<boolean>
  /** Read file content */
  readFile: (fileName: string) => Promise<{ content: string; lineCount: number }>
  /** List all files in workspace */
  listFiles: () => Promise<string[]>
  /** Archive old files with timestamp */
  archiveOldFiles: () => Promise<void>
  /** Get workspace directory path */
  getWorkspaceDir: () => string
  /** Whether file system is initialized */
  isInitialized: boolean
  /** Error from last operation (null if no error) */
  error: string | null
  /** Files currently in workspace */
  files: string[]
}

// ============================================================================
// useFileSystem Hook
// ============================================================================

/**
 * React hook for file system operations
 * 
 * @param workspaceDir - Optional custom workspace directory path
 * @returns Object with file operation functions and state
 * 
 * @example
 * ```tsx
 * const { writeFile, readFile, listFiles, files } = useFileSystem()
 * 
 * // Write a file
 * await writeFile('notes.md', '# My Notes\n\nHello world')
 * 
 * // Read a file
 * const { content, lineCount } = await readFile('notes.md')
 * console.log(`File has ${lineCount} lines`)
 * 
 * // List all files
 * const allFiles = await listFiles()
 * console.log('Files:', allFiles)
 * ```
 */
export function useFileSystem(workspaceDir?: string): UseFileSystemReturn {
  // State
  const [isInitialized, setIsInitialized] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [files, setFiles] = useState<string[]>([])

  // Refs
  const fileSystemRef = useRef<FileSystem | null>(null)

  // Initialize FileSystem
  useEffect(() => {
    const initFileSystem = async () => {
      try {
        // Create FileSystem instance
        fileSystemRef.current = new FileSystem(workspaceDir)

        // Initialize
        await fileSystemRef.current.initialize()
        setIsInitialized(true)
        setError(null)

        // Load initial file list
        const initialFiles = await fileSystemRef.current.listFiles()
        setFiles(initialFiles)
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : String(err)
        setError(errorMessage)
        console.error('FileSystem initialization error:', err)
      }
    }

    initFileSystem()

    // Cleanup (no cleanup needed for FileSystem)
    return () => {
      fileSystemRef.current = null
    }
  }, [workspaceDir])

  /**
   * Write content to a file
   */
  const writeFile = useCallback(
    async (fileName: string, content: string): Promise<boolean> => {
      if (!fileSystemRef.current) {
        throw new Error('FileSystem not initialized')
      }

      try {
        const result = await fileSystemRef.current.writeFile(fileName, content)
        setError(null)

        // Refresh file list
        const updatedFiles = await fileSystemRef.current.listFiles()
        setFiles(updatedFiles)

        return result
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : String(err)
        setError(errorMessage)
        console.error('Write file error:', err)
        throw err
      }
    },
    []
  )

  /**
   * Append content to a file
   */
  const appendFile = useCallback(
    async (fileName: string, content: string): Promise<boolean> => {
      if (!fileSystemRef.current) {
        throw new Error('FileSystem not initialized')
      }

      try {
        const result = await fileSystemRef.current.appendFile(fileName, content)
        setError(null)

        // Refresh file list
        const updatedFiles = await fileSystemRef.current.listFiles()
        setFiles(updatedFiles)

        return result
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : String(err)
        setError(errorMessage)
        console.error('Append file error:', err)
        throw err
      }
    },
    []
  )

  /**
   * Read file content
   */
  const readFile = useCallback(
    async (fileName: string): Promise<{ content: string; lineCount: number }> => {
      if (!fileSystemRef.current) {
        throw new Error('FileSystem not initialized')
      }

      try {
        const result = await fileSystemRef.current.readFile(fileName)
        setError(null)
        return result
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : String(err)
        setError(errorMessage)
        console.error('Read file error:', err)
        throw err
      }
    },
    []
  )

  /**
   * List all files in workspace
   */
  const listFiles = useCallback(async (): Promise<string[]> => {
    if (!fileSystemRef.current) {
      throw new Error('FileSystem not initialized')
    }

    try {
      const fileList = await fileSystemRef.current.listFiles()
      setFiles(fileList)
      setError(null)
      return fileList
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      console.error('List files error:', err)
      throw err
    }
  }, [])

  /**
   * Archive old files with timestamp
   */
  const archiveOldFiles = useCallback(async (): Promise<void> => {
    if (!fileSystemRef.current) {
      throw new Error('FileSystem not initialized')
    }

    try {
      await fileSystemRef.current.archiveOldFiles()
      setError(null)

      // Refresh file list
      const updatedFiles = await fileSystemRef.current.listFiles()
      setFiles(updatedFiles)
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setError(errorMessage)
      console.error('Archive files error:', err)
      throw err
    }
  }, [])

  /**
   * Get workspace directory path
   */
  const getWorkspaceDir = useCallback((): string => {
    if (!fileSystemRef.current) {
      throw new Error('FileSystem not initialized')
    }

    return fileSystemRef.current.getWorkspaceDir()
  }, [])

  return {
    writeFile,
    appendFile,
    readFile,
    listFiles,
    archiveOldFiles,
    getWorkspaceDir,
    isInitialized,
    error,
    files,
  }
}

