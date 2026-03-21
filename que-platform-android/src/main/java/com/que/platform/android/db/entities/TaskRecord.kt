package com.que.platform.android.db.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
class TaskRecord(
    @Id var id: Long = 0,
    var taskText: String = "",
    var status: String = "",        // DONE / FAILED / CANCELLED
    var startedAt: Long = 0,
    var completedAt: Long = 0,
    var durationSeconds: Int = 0,
    var summary: String = "",
    var errorReason: String = "",
    var appsTouched: String = "",   // JSON array of app names
    var tokenCount: Int = 0,
    var stepCount: Int = 0,
)
