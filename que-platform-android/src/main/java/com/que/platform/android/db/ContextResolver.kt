package com.que.platform.android.db

import com.que.platform.android.db.entities.UserMemory
import com.que.platform.android.db.entities.UserMemory_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder

class ContextResolver(private val boxStore: BoxStore) {
    private val memoryBox = boxStore.boxFor(UserMemory::class.java)

    fun resolve(vararg fieldPaths: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val currentTime = System.currentTimeMillis()
        
        for (path in fieldPaths) {
            val memory = memoryBox.query()
                .equal(UserMemory_.fieldPath, path, StringOrder.CASE_SENSITIVE)
                .build()
                .findFirst()
                
            if (memory != null) {
                // Check if still valid
                if (currentTime - memory.cachedAt < memory.ttlSeconds * 1000L) {
                    result[path] = memory.value
                } else {
                    result[path] = "" // Expired
                }
            } else {
                result[path] = "" // Missing
            }
        }
        
        android.util.Log.d("ContextResolver", "Resolved fields: \${result.keys}")
        return result
    }

    fun resolveBySemantics(queryEmbedding: FloatArray, topK: Int = 3): List<UserMemory> {
        return memoryBox.query()
            .nearestNeighbors(UserMemory_.embedding, queryEmbedding, topK)
            .build()
            .find()
    }
}
