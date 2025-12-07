package com.que.core

/**
 * Abstraction for the agent's file system.
 * Allows the agent to read/write files in a sandboxed environment.
 */
interface FileSystem {
    suspend fun readFile(fileName: String): String
    suspend fun writeFile(fileName: String, content: String): Boolean
    suspend fun appendFile(fileName: String, content: String): Boolean
    fun describe(): String
    fun getTodoContents(): String
}
