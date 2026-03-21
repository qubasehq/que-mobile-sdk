package com.que.platform.android.db

import com.que.platform.android.db.entities.TaskRecord
import com.que.platform.android.db.entities.UserMemory
import com.que.platform.android.db.entities.UserMemory_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileLearner(private val boxStore: BoxStore) {
    private val memoryBox = boxStore.boxFor(UserMemory::class.java)

    suspend fun extractAndLearn(task: TaskRecord) {
        withContext(Dispatchers.IO) {
            // Placeholder: Extract fields from task.summary
            // Expected extracted fields:
            // - App used for task
            // - Delivery address
            // - Payment method
            // - Contact messaged
            // - Food preferences
            
            android.util.Log.d("ProfileLearner", "Learning from task \${task.id}: \${task.summary}")
        }
    }

    suspend fun addOrUpdateMemory(fieldPath: String, value: String, source: String = "learned", embedding: FloatArray? = null) {
        withContext(Dispatchers.IO) {
            val existing = memoryBox.query()
                .equal(UserMemory_.fieldPath, fieldPath, StringOrder.CASE_SENSITIVE)
                .build()
                .findFirst()

            if (existing != null) {
                existing.value = value
                existing.confidence += 0.1f // Increment confidence
                existing.ttlSeconds = 86400 // Reset TTL
                existing.cachedAt = System.currentTimeMillis()
                if (embedding != null) existing.embedding = embedding
                memoryBox.put(existing)
            } else {
                val newMemory = UserMemory(
                    fieldPath = fieldPath,
                    value = value,
                    source = source,
                    confidence = 1.0f,
                    cachedAt = System.currentTimeMillis(),
                    ttlSeconds = 86400,
                    embedding = embedding ?: FloatArray(0)
                )
                memoryBox.put(newMemory)
            }
        }
    }
}
