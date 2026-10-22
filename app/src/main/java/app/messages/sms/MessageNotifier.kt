package app.messages.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import app.messages.MainActivity
import app.messages.MessagesApp
import app.messages.R

object MessageNotifier {
    const val CHANNEL = "messages"
    const val KEY_REPLY = "reply"
    const val EXTRA_THREAD = "threadId"
    const val EXTRA_ADDRESS = "address"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.messages_app_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New texts and picture messages"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyIncoming(
        context: Context,
        threadId: Long,
        address: String,
        title: String,
        body: String,
        whenMs: Long,
    ) {
        if (threadId > 0 && ActiveThread.id == threadId) {
            MessagesApp.from(context).messaging.markRead(threadId)
            return
        }
        val preview = MessagesApp.from(context).local.snapshot.notificationPreview
        val shownTitle = if (preview) title.ifBlank { address } else context.getString(R.string.messages_app_name)
        val shownBody = if (preview) body.ifBlank { "Picture" } else "New message"
        val id = notificationId(threadId)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_THREAD, threadId)
                data = Uri.parse("messages://thread/$threadId")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val user = Person.Builder().setName("You").build()
        val sender = Person.Builder().setName(shownTitle).build()
        val reply = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
        val replyIntent = Intent(context, MessageActionReceiver::class.java).apply {
            action = MessageActionReceiver.ACTION_REPLY
            data = Uri.parse("messages://reply/$threadId")
            putExtra(EXTRA_THREAD, threadId)
            putExtra(EXTRA_ADDRESS, address)
        }
        val replyPi = PendingIntent.getBroadcast(
            context,
            id + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val readPi = PendingIntent.getBroadcast(
            context,
            id + 2,
            Intent(context, MessageActionReceiver::class.java).apply {
                action = MessageActionReceiver.ACTION_READ
                data = Uri.parse("messages://read/$threadId")
                putExtra(EXTRA_THREAD, threadId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(shownTitle)
            .setContentText(shownBody)
            .setStyle(
                NotificationCompat.MessagingStyle(user)
                    .setConversationTitle(shownTitle)
                    .addMessage(shownBody, whenMs, sender),
            )
            .setWhen(whenMs)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                NotificationCompat.Action.Builder(0, "Reply", replyPi)
                    .addRemoteInput(reply)
                    .setAllowGeneratedReplies(true)
                    .build(),
            )
            .addAction(0, "Mark read", readPi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun cancel(context: Context, threadId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(threadId))
    }

    fun simple(context: Context, title: String, body: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(3, notification)
    }

    private fun notificationId(threadId: Long): Int {
        val mixed = (threadId xor (threadId ushr 32)).toInt() and 0x7fffffff
        return if (mixed < 100) mixed + 100 else mixed
    }
}
