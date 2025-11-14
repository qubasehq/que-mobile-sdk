package expo.modules.quemobilesdk.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.util.*

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "QueBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed, restoring triggers")
            restoreScheduledTriggers(context)
        }
    }

    private fun restoreScheduledTriggers(context: Context) {
        try {
            val database = TriggerDatabase(context)
            val triggers = database.getEnabledTriggers()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager not available")
                return
            }

            for (trigger in triggers) {
                if (trigger.type == "schedule" && trigger.scheduleJson != null) {
                    scheduleNextAlarm(context, alarmManager, trigger)
                }
            }

            Log.d(TAG, "Restored ${triggers.size} scheduled triggers")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring triggers", e)
        }
    }

    private fun scheduleNextAlarm(context: Context, alarmManager: AlarmManager, trigger: TriggerEntity) {
        try {
            val schedule = JSONObject(trigger.scheduleJson ?: return)
            val time = schedule.getString("time")
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
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                if (daysSet.contains(dayOfWeek)) {
                    break
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                attempts++
            }

            // Schedule alarm
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

            Log.d(TAG, "Scheduled alarm for trigger ${trigger.id} at ${calendar.timeInMillis}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm for trigger ${trigger.id}", e)
        }
    }
}
