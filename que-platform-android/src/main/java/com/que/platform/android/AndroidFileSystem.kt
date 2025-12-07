package com.que.platform.android

import android.content.Context
import android.util.Log
import com.que.core.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Android implementation of the FileSystem interface.
 * Ports the logic from Blurr's FileSystem.kt.
 */
class AndroidFileSystem(
    context: Context,
    workspaceName: String = "agent_workspace"
) : FileSystem {

    private val workspaceDir: File
    private val todoFile: File

    companion object {
        private const val TAG = "AndroidFileSystem"
    }

    init {
        val baseDir = context.filesDir
        workspaceDir = File(baseDir, workspaceName)
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }
        
        todoFile = File(workspaceDir, "todo.md")
        if (!todoFile.exists()) {
            try {
                todoFile.createNewFile()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create todo.md", e)
            }
        }
    }

    override suspend fun readFile(fileName: String): String = withContext(Dispatchers.IO) {
        if (!isValidFilename(fileName)) return@withContext "Error: Invalid filename."
        val file = File(workspaceDir, fileName)
        if (!file.exists()) return@withContext "Error: File not found."
        
        try {
            file.readText()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    override suspend fun writeFile(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFilename(fileName)) return@withContext false
        try {
            File(workspaceDir, fileName).writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file", e)
            false
        }
    }

    override suspend fun appendFile(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFilename(fileName)) return@withContext false
        try {
            File(workspaceDir, fileName).appendText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error appending file", e)
            false
        }
    }

    override fun describe(): String {
        return try {
            val files = workspaceDir.listFiles { f -> f.isFile && !f.name.startsWith("todo_ARCHIVED") }
            if (files.isNullOrEmpty()) return "The file system is empty."
            
            files.joinToString("\n") { file ->
                "- ${file.name} — ${file.length()} bytes"
            }
        } catch (e: Exception) {
            "Error describing file system."
        }
    }

    override fun getTodoContents(): String {
        return try {
            if (todoFile.exists()) todoFile.readText() else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun isValidFilename(name: String): Boolean {
        return name.matches(Regex("^[a-zA-Z0-9_-]+\\.(md|txt)$"))
    }
}
