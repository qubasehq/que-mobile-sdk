package expo.modules.quemobilesdk.triggers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class TriggerEntity(
    val id: String,
    val type: String,
    val enabled: Boolean,
    val priority: Int,
    val task: String,
    val agentConfigJson: String,
    val scheduleJson: String?,
    val notificationConfigJson: String?
)

class TriggerDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "que_triggers.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_TRIGGERS = "triggers"

        // Column names
        private const val COLUMN_ID = "id"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_ENABLED = "enabled"
        private const val COLUMN_PRIORITY = "priority"
        private const val COLUMN_TASK = "task"
        private const val COLUMN_AGENT_CONFIG = "agent_config_json"
        private const val COLUMN_SCHEDULE = "schedule_json"
        private const val COLUMN_NOTIFICATION_CONFIG = "notification_config_json"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_TRIGGERS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_TYPE TEXT NOT NULL,
                $COLUMN_ENABLED INTEGER NOT NULL,
                $COLUMN_PRIORITY INTEGER NOT NULL,
                $COLUMN_TASK TEXT NOT NULL,
                $COLUMN_AGENT_CONFIG TEXT NOT NULL,
                $COLUMN_SCHEDULE TEXT,
                $COLUMN_NOTIFICATION_CONFIG TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRIGGERS")
        onCreate(db)
    }

    fun insertTrigger(trigger: TriggerEntity): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, trigger.id)
            put(COLUMN_TYPE, trigger.type)
            put(COLUMN_ENABLED, if (trigger.enabled) 1 else 0)
            put(COLUMN_PRIORITY, trigger.priority)
            put(COLUMN_TASK, trigger.task)
            put(COLUMN_AGENT_CONFIG, trigger.agentConfigJson)
            put(COLUMN_SCHEDULE, trigger.scheduleJson)
            put(COLUMN_NOTIFICATION_CONFIG, trigger.notificationConfigJson)
        }
        val result = db.insert(TABLE_TRIGGERS, null, values)
        return result != -1L
    }

    fun updateTrigger(trigger: TriggerEntity): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TYPE, trigger.type)
            put(COLUMN_ENABLED, if (trigger.enabled) 1 else 0)
            put(COLUMN_PRIORITY, trigger.priority)
            put(COLUMN_TASK, trigger.task)
            put(COLUMN_AGENT_CONFIG, trigger.agentConfigJson)
            put(COLUMN_SCHEDULE, trigger.scheduleJson)
            put(COLUMN_NOTIFICATION_CONFIG, trigger.notificationConfigJson)
        }
        val result = db.update(TABLE_TRIGGERS, values, "$COLUMN_ID = ?", arrayOf(trigger.id))
        return result > 0
    }

    fun deleteTrigger(id: String): Boolean {
        val db = writableDatabase
        val result = db.delete(TABLE_TRIGGERS, "$COLUMN_ID = ?", arrayOf(id))
        return result > 0
    }

    fun getTrigger(id: String): TriggerEntity? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TRIGGERS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                TriggerEntity(
                    id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID)),
                    type = it.getString(it.getColumnIndexOrThrow(COLUMN_TYPE)),
                    enabled = it.getInt(it.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1,
                    priority = it.getInt(it.getColumnIndexOrThrow(COLUMN_PRIORITY)),
                    task = it.getString(it.getColumnIndexOrThrow(COLUMN_TASK)),
                    agentConfigJson = it.getString(it.getColumnIndexOrThrow(COLUMN_AGENT_CONFIG)),
                    scheduleJson = it.getString(it.getColumnIndexOrThrow(COLUMN_SCHEDULE)),
                    notificationConfigJson = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTIFICATION_CONFIG))
                )
            } else {
                null
            }
        }
    }

    fun getAllTriggers(): List<TriggerEntity> {
        val db = readableDatabase
        val cursor = db.query(TABLE_TRIGGERS, null, null, null, null, null, "$COLUMN_PRIORITY DESC")
        val triggers = mutableListOf<TriggerEntity>()

        cursor.use {
            while (it.moveToNext()) {
                triggers.add(
                    TriggerEntity(
                        id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID)),
                        type = it.getString(it.getColumnIndexOrThrow(COLUMN_TYPE)),
                        enabled = it.getInt(it.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1,
                        priority = it.getInt(it.getColumnIndexOrThrow(COLUMN_PRIORITY)),
                        task = it.getString(it.getColumnIndexOrThrow(COLUMN_TASK)),
                        agentConfigJson = it.getString(it.getColumnIndexOrThrow(COLUMN_AGENT_CONFIG)),
                        scheduleJson = it.getString(it.getColumnIndexOrThrow(COLUMN_SCHEDULE)),
                        notificationConfigJson = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTIFICATION_CONFIG))
                    )
                )
            }
        }

        return triggers
    }

    fun getEnabledTriggers(): List<TriggerEntity> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TRIGGERS,
            null,
            "$COLUMN_ENABLED = ?",
            arrayOf("1"),
            null,
            null,
            "$COLUMN_PRIORITY DESC"
        )
        val triggers = mutableListOf<TriggerEntity>()

        cursor.use {
            while (it.moveToNext()) {
                triggers.add(
                    TriggerEntity(
                        id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID)),
                        type = it.getString(it.getColumnIndexOrThrow(COLUMN_TYPE)),
                        enabled = it.getInt(it.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1,
                        priority = it.getInt(it.getColumnIndexOrThrow(COLUMN_PRIORITY)),
                        task = it.getString(it.getColumnIndexOrThrow(COLUMN_TASK)),
                        agentConfigJson = it.getString(it.getColumnIndexOrThrow(COLUMN_AGENT_CONFIG)),
                        scheduleJson = it.getString(it.getColumnIndexOrThrow(COLUMN_SCHEDULE)),
                        notificationConfigJson = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTIFICATION_CONFIG))
                    )
                )
            }
        }

        return triggers
    }
}
