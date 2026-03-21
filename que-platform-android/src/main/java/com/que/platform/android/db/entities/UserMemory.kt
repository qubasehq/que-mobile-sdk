package com.que.platform.android.db.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

@Entity
class UserMemory(
    @Id var id: Long = 0,
    var fieldPath: String = "",     // e.g. "food.usual_order"
    var value: String = "",
    var source: String = "",        // "onboarding" / "learned" / "corrected"
    var confidence: Float = 1.0f,
    var cachedAt: Long = 0,
    var ttlSeconds: Int = 86400,
    @HnswIndex(dimensions = 384)
    var embedding: FloatArray = FloatArray(0),
)
