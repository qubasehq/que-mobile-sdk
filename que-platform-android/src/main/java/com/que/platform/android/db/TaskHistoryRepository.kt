package com.que.platform.android.db

import com.que.platform.android.db.entities.ActionItem
import com.que.platform.android.db.entities.TaskRecord
import com.que.platform.android.db.entities.TaskRecord_
import com.que.platform.android.db.entities.ActionItem_
import com.que.core.engine.AgentHistoryTracker
import io.objectbox.BoxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskHistoryRepository(
    private val boxStore: BoxStore, 
    private val profileLearner: ProfileLearner? = null
) : AgentHistoryTracker {
    private val taskBox = boxStore.boxFor(TaskRecord::class.java)
    private val actionBox = boxStore.boxFor(ActionItem::class.java)

    override fun startTask(taskText: String): Long {
        val task = TaskRecord(
            taskText = taskText,
            status = "RUNNING",
            startedAt = System.currentTimeMillis()
        )
        val id = taskBox.put(task)
        android.util.Log.d("TaskHistoryRepo", "Started task: $taskText, generated ID: $id")
        return id
    }

    override fun recordAction(taskId: Long, timestamp: Long, description: String, actionType: String, appName: String, success: Boolean, errorDetail: String) {
        val action = ActionItem(
            taskId = taskId,
            timestamp = timestamp,
            description = description,
            actionType = actionType,
            appName = appName,
            success = success,
            errorDetail = errorDetail
        )
        actionBox.put(action)
    }

    override fun completeTask(taskId: Long, summary: String, tokenCount: Int) {
        if (taskId <= 0) return
        val task = taskBox.get(taskId) ?: return
        task.status = "DONE"
        task.completedAt = System.currentTimeMillis()
        task.durationSeconds = ((task.completedAt - task.startedAt) / 1000).toInt()
        task.summary = summary
        task.tokenCount = tokenCount
        taskBox.put(task)
    }

    override fun failTask(taskId: Long, reason: String) {
        if (taskId <= 0) return
        val task = taskBox.get(taskId) ?: return
        task.status = "FAILED"
        task.completedAt = System.currentTimeMillis()
        task.durationSeconds = ((task.completedAt - task.startedAt) / 1000).toInt()
        task.errorReason = reason
        taskBox.put(task)
    }

    override fun cancelTask(taskId: Long) {
        if (taskId <= 0) return
        val task = taskBox.get(taskId) ?: return
        task.status = "CANCELLED"
        task.completedAt = System.currentTimeMillis()
        task.durationSeconds = ((task.completedAt - task.startedAt) / 1000).toInt()
        taskBox.put(task)
    }
    
    override fun extractAndLearn(taskId: Long) {
        if (taskId <= 0) return
        val task = taskBox.get(taskId) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            profileLearner?.extractAndLearn(task)
        }
    }

    fun getHistory(limit: Int = 50): List<TaskRecord> {
        return taskBox.query()
            .orderDesc(TaskRecord_.startedAt)
            .build()
            .find(0, limit.toLong())
    }

    fun getTaskActions(taskId: Long): List<ActionItem> {
        return actionBox.query()
            .equal(ActionItem_.taskId, taskId)
            .order(ActionItem_.timestamp)
            .build()
            .find()
    }

    fun getTaskById(taskId: Long): TaskRecord? {
        return taskBox.get(taskId)
    }

    fun clearHistory() {
        taskBox.removeAll()
        actionBox.removeAll()
    }
}
