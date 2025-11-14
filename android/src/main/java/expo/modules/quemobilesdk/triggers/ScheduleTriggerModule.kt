package expo.modules.quemobilesdk.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject
import java.util.*

class ScheduleTriggerModule : Module() {
    private lateinit var database: TriggerDatabase

    override fun definition() = ModuleDefinition {
        Name("ScheduleTriggerModule")

        OnCreate {
            database = TriggerDatabase(appContext.reactContext ?: throw IllegalStateException("React context not available"))
        }

        AsyncFunction("scheduleAlarm") { triggerId: String, timeInMillis: Long, promise: Promise ->
            try {
                val alarmManager = appContext.reactContext?.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                if (alarmManager == null) {
                    promise.reject("ALARM_ERROR", "AlarmManager not available", null)
                    return@AsyncFunction
                }

                val intent = Intent(appContext.reactContext, ScheduleTriggerReceiver::class.java).apply {
                    action = "expo.modules.quemobilesdk.TRIGGER_ALARM"
                    putExtra("triggerId", triggerId)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    appContext.reactContext,
                    triggerId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Use setExactAndAllowWhileIdle for precise timing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                }

                Log.d("ScheduleTrigger", "Scheduled alarm for trigger $triggerId at $timeInMillis")
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e("ScheduleTrigger", "Error scheduling alarm", e)
                promise.reject("SCHEDULE_ERROR", e.message, e)
            }
        }

        AsyncFunction("cancelAlarm") { triggerId: String, promise: Promise ->
            try {
                val alarmManager = appContext.reactContext?.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                if (alarmManager == null) {
                    promise.reject("ALARM_ERROR", "AlarmManager not available", null)
                    return@AsyncFunction
                }

                val intent = Intent(appContext.reactContext, ScheduleTriggerReceiver::class.java).apply {
                    action = "expo.modules.quemobilesdk.TRIGGER_ALARM"
                    putExtra("triggerId", triggerId)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    appContext.reactContext,
                    triggerId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()

                Log.d("ScheduleTrigger", "Cancelled alarm for trigger $triggerId")
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e("ScheduleTrigger", "Error cancelling alarm", e)
                promise.reject("CANCEL_ERROR", e.message, e)
            }
        }

        AsyncFunction("getNextAlarmTime") { triggerId: String, scheduleJson: String, promise: Promise ->
            try {
                val schedule = JSONObject(scheduleJson)
                val time = schedule.getString("time") // Format: "HH:mm"
                val daysOfWeek = schedule.getJSONArray("daysOfWeek")
                
                val timeParts = time.split(":")
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()

                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                // If the time has passed today, start from tomorrow
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }

                // Find next matching day of week
                val daysSet = mutableSetOf<Int>()
                for (i in 0 until daysOfWeek.length()) {
                    daysSet.add(daysOfWeek.getInt(i))
                }

                var attempts = 0
                while (attempts < 7) {
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-6
                    if (daysSet.contains(dayOfWeek)) {
                        break
                    }
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    attempts++
                }

                promise.resolve(calendar.timeInMillis)
            } catch (e: Exception) {
                Log.e("ScheduleTrigger", "Error calculating next alarm time", e)
                promise.reject("CALCULATION_ERROR", e.message, e)
            }
        }
    }
}

class ScheduleTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "expo.modules.quemobilesdk.TRIGGER_ALARM") {
            val triggerId = intent.getStringExtra("triggerId") ?: return
            Log.d("ScheduleTrigger", "Alarm fired for trigger $triggerId")

            // Load trigger from database and execute
            val database = TriggerDatabase(context)
            val trigger = database.getTrigger(triggerId)

            if (trigger != null && trigger.enabled) {
                // Send broadcast to execute trigger
                val executeIntent = Intent("expo.modules.quemobilesdk.EXECUTE_TRIGGER").apply {
                    putExtra("triggerId", triggerId)
                    putExtra("triggerType", "schedule")
                }
                context.sendBroadcast(executeIntent)

                // Reschedule for next occurrence
                if (trigger.scheduleJson != null) {
                    rescheduleAlarm(context, trigger)
                }
            }
        }
    }

    private fun rescheduleAlarm(context: Context, trigger: TriggerEntity) {
        try {
            val schedule = JSONObject(trigger.scheduleJson ?: return)
            val time = schedule.getString("time")
            val daysOfWeek = schedule.getJSONArray("daysOfWeek")

            val timeParts = time.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Start from tomorrow
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // Find next matching day of week
            val daysSet = mutableSetOf<Int>()
            for (i in 0 until daysOfWeek.length()) {
                daysSet.add(daysOfWeek.getInt(i))
            }

            var attempts = 0
            while (attempts < 7) {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                if (daysSet.contains(dayOfWeek)) {
                    break
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                attempts++
            }

            // Schedule next alarm
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ScheduleTriggerReceiver::class.java).apply {
                action = "expo.modules.quemobilesdk.TRIGGER_ALARM"
                putExtra("triggerId", trigger.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                trigger.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            Log.d("ScheduleTrigger", "Rescheduled alarm for trigger ${trigger.id} at ${calendar.timeInMillis}")
        } catch (e: Exception) {
            Log.e("ScheduleTrigger", "Error rescheduling alarm", e)
        }
    }
}
