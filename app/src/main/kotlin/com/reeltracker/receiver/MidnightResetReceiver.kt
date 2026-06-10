package com.reeltracker.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.reeltracker.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MIDNIGHT_RESET) return

        // Reschedule the alarm for the next midnight
        schedule(context)

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                AppContainer.initialize(context)
                val repository = AppContainer.repository
                val prefsRepo = AppContainer.prefsRepository
                val prefs = prefsRepo.userPreferencesFlow.first()

                // Expire old blocks
                repository.expireOldBlocks()

                // Ensure today's record exists (new day)
                repository.ensureTodayExists(prefs.dailyLimit)

                // Calculate and update streak
                val streak = repository.calculateStreak()
                prefsRepo.updateStreak(streak)

            } finally {
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT_RESET = "com.reeltracker.ACTION_MIDNIGHT_RESET"

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, MidnightResetReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_RESET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If the time is in the past (e.g. we are scheduling during the day), set it for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val triggerTime = calendar.timeInMillis

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }
}
