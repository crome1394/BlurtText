package app.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.messages.MessagesApp
import app.messages.data.ScheduledMessage

object ScheduleController {
    fun canExact(context: Context): Boolean {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    }

    fun arm(context: Context, item: ScheduledMessage) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pending(context, item.id)
        if (canExact(context)) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.sendAt, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.sendAt, pending)
        }
    }

    fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(pending(context, id))
    }

    suspend fun rearmAll(app: MessagesApp) {
        val now = System.currentTimeMillis()
        for (item in app.local.current().scheduled) {
            if (item.failed) continue
            val whenAt = if (item.sendAt <= now) now + 1_500 else item.sendAt
            arm(app, item.copy(sendAt = whenAt))
        }
    }

    private fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = "app.messages.SEND_SCHEDULED"
            data = Uri.parse("messages://scheduled/$id")
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
