package com.que.platform.android.db.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
class ActionItem(
    @Id var id: Long = 0,
    var taskId: Long = 0,           // foreign key to TaskRecord
    var timestamp: Long = 0,
    var description: String = "",
    var actionType: String = "",    // from AndroidActionExecutor action types
    var appName: String = "",
    var success: Boolean = true,
    var errorDetail: String = "",
)
