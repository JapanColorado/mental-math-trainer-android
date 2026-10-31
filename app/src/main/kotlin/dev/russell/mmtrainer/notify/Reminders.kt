package dev.russell.mmtrainer.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dev.russell.mmtrainer.MainActivity
import dev.russell.mmtrainer.MmtApp
import dev.russell.mmtrainer.R
import dev.russell.mmtrainer.Settings
import dev.russell.mmtrainer.Store
import dev.russell.mmtrainer.ui.StoreThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The optional daily reminder. One inexact alarm at a time: each firing schedules the next, so it
 * follows the clock across time zones and daylight saving, and needs no exact-alarm permission.
 */
object Reminders {
    private const val CHANNEL = "daily_reminder"
    private const val NOTIFICATION_ID = 1
    private const val ACTION_FIRE = "dev.russell.mmtrainer.REMINDER"

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The next time [minute] (after midnight) comes round, in epoch milliseconds. */
    fun nextTrigger(minute: Int, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val time = LocalTime.of(minute / 60, minute % 60)
        val today = LocalDate.from(now).atTime(time)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    fun schedule(context: Context, enabled: Boolean, minute: Int) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        if (enabled) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(minute), pending)
    }

    fun isFiring(intent: Intent) = intent.action == ACTION_FIRE

    /** Posts the notification, unless today's session is already done or notifications are off. */
    fun maybeNotify(context: Context, store: Store) {
        val manager = NotificationManagerCompat.from(context)
        if (store.sessionsToday() > 0 || !manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val minutes = store.settingInt(Settings.SESSION_MINUTES)
        val streak = store.streak()
        val body = if (streak > 0) "$minutes minutes keeps your $streak-day streak going."
        else "$minutes minutes is all it takes."
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time for a quick session")
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

/** Fires the reminder, and puts the alarm back after a reboot, app update or clock change. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val known = Reminders.isFiring(intent) || action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED || action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        if (!known) return
        val app = context.applicationContext as MmtApp
        val done = goAsync()
        CoroutineScope(StoreThread).launch {
            try {
                val store = app.store
                if (store.settingBool(Settings.REMINDER_ENABLED)) {
                    if (Reminders.isFiring(intent)) Reminders.maybeNotify(app, store)
                    Reminders.schedule(app, true, store.settingInt(Settings.REMINDER_MINUTE))
                }
            } finally {
                done.finish()
            }
        }
    }
}
