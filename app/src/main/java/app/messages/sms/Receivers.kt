package app.messages.sms

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.RemoteInput
import app.lantext.LanTextApp
import app.messages.MessagesApp
import app.messages.data.matchesBlockedKeyword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val deliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        val received = action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        if (!deliver && !received) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val address = parts.first().displayOriginatingAddress ?: parts.first().originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        val timestamp = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val subId = intent.getIntExtra(
            "subscription",
            intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1),
        )
        val app = context.applicationContext as? MessagesApp
        if (deliver && app != null) {
            try {
                val blocked = app.messaging.isBlocked(address)
                val keyword = matchesBlockedKeyword(body, app.local.snapshot.blockedKeywords)
                val threadId = app.messaging.insertInbox(address, body, timestamp, subId, unread = !blocked && !keyword)
                app.messaging.ping()
                if (blocked && threadId > 0) {
                    app.messaging.markRead(threadId)
                    app.messaging.setArchived(listOf(threadId), true)
                } else if (!keyword && threadId > 0) {
                    MessageNotifier.notifyIncoming(
                        context,
                        threadId,
                        address,
                        app.messaging.displayAddress(address),
                        body,
                        timestamp,
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Could not store incoming SMS", t)
            }
        }
        val lan = context.applicationContext as? LanTextApp
        if (IncomingDedup.claim(address, body)) lan?.sms?.emitIncoming(address, body)
        else if (deliver) lan?.sms?.emitRefresh()
    }

    companion object {
        private const val TAG = "Messages"
    }
}

class MessageStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val uri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return
        val key = uri.toString()
        when (intent.action) {
            ACTION_SENT -> {
                val ok = resultCode == Activity.RESULT_OK
                if (!ok) Log.w("Messages", "SMS send failed code=$resultCode")
                if (PartTracker.onPart(key, ok)) {
                    finishSmsStatus(context, uri, PartTracker.failed(key))
                }
            }
            ACTION_DELIVERED -> {
                val values = android.content.ContentValues().apply {
                    put(
                        Telephony.Sms.STATUS,
                        if (resultCode == Activity.RESULT_OK) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_FAILED,
                    )
                }
                context.contentResolver.update(uri, values, null, null)
                (context.applicationContext as? MessagesApp)?.messaging?.ping()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "app.messages.SMS_SENT"
        const val ACTION_DELIVERED = "app.messages.SMS_DELIVERED"
        const val EXTRA_URI = "uri"
        const val EXTRA_INDEX = "index"
        const val EXTRA_COUNT = "count"
    }
}

class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data")
        val header = intent.getByteArrayExtra("header")
        val raw = intent.getByteArrayExtra("pdu")
        val subId = intent.getIntExtra("subscription", intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1))
        val app = context.applicationContext as? MessagesApp ?: return
        val pending = goAsync()
        Thread {
            try {
                val notification = MmsNotificationParser.parse(data)
                    ?: MmsNotificationParser.parse(raw)
                    ?: if (data != null && header != null) MmsNotificationParser.parse(header + data) else null
                    ?: MmsNotificationParser.parse(header)
                if (notification == null) {
                    Log.w("Messages", "Could not read an MMS notification bytes=${data?.size ?: raw?.size ?: 0}")
                    return@Thread
                }
                Log.i("Messages", "MMS notification bytes=${data?.size ?: raw?.size ?: 0}")
                val uri = app.messaging.acceptMmsNotification(notification, subId) ?: return@Thread
                val blocked = app.messaging.isBlocked(notification.from)
                MmsInbound.begin(
                    context,
                    uri,
                    notification.contentLocation,
                    notification.from,
                    subId,
                    quiet = blocked,
                )
                app.messaging.ping()
            } catch (t: Throwable) {
                Log.e("Messages", "MMS download failed to start", t)
                MessageNotifier.simple(context, "Picture message", "Could not start the download.")
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class MmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val fileName = intent.getStringExtra("file").orEmpty()
        if (!fileName.matches(DOWNLOAD_FILE)) return
        val placeholder = intent.getStringExtra("placeholder")?.let(Uri::parse) ?: return
        val address = intent.getStringExtra("address").orEmpty()
        val quiet = intent.getBooleanExtra("quiet", false)
        val code = resultCode
        val http = intent.getIntExtra("android.telephony.extra.MMS_HTTP_STATUS", -1)
        val app = context.applicationContext as? MessagesApp ?: return
        val pending = goAsync()
        Thread {
            val file = java.io.File(context.cacheDir, fileName)
            try {
                Log.i("Messages", "MMS download result=$code http=$http bytes=${file.length()}")
                if (code == Activity.RESULT_OK && file.length() > MAX_RETRIEVE_BYTES) {
                    Log.w("Messages", "MMS download is too large bytes=${file.length()}")
                    if (!quiet) {
                        MessageNotifier.simple(context, "Picture message", "The picture is too large to open.")
                    }
                } else if (code == Activity.RESULT_OK) {
                    val bytes = if (file.isFile) file.readBytes() else ByteArray(0)
                    val threadId = app.messaging.storeRetrievedMms(placeholder, bytes, address)
                    if (threadId >= 0) {
                        val text = MmsRetrieveParser.parse(bytes)?.parts
                            ?.firstOrNull { it.mime.startsWith("text/") }
                            ?.text()
                            .orEmpty()
                        val blocked = app.messaging.isBlocked(address)
                        val keyword = matchesBlockedKeyword(text, app.local.snapshot.blockedKeywords)
                        if (blocked) {
                            app.messaging.markRead(threadId)
                            app.messaging.setArchived(listOf(threadId), true)
                        } else if (!keyword) {
                            MessageNotifier.notifyIncoming(
                                context,
                                threadId,
                                address,
                                address.ifBlank { "Picture message" },
                                text,
                                System.currentTimeMillis(),
                            )
                        }
                    } else if (!quiet) {
                        MessageNotifier.simple(context, "Picture message", "The picture downloaded, but it could not be opened.")
                    } else {
                        Log.w("Messages", "Retrieved MMS was not stored bytes=${bytes.size}")
                    }
                } else if (!quiet) {
                    MessageNotifier.simple(context, "Picture message", "The download did not finish ($code).")
                }
                app.messaging.ping()
            } catch (t: Throwable) {
                Log.e("Messages", "MMS download failed", t)
            } finally {
                file.delete()
                pending.finish()
            }
        }.start()
    }

    companion object {
        private val DOWNLOAD_FILE = Regex("dl-[0-9a-fA-F-]{36}\\.dat")
        private const val MAX_RETRIEVE_BYTES = 4 * 1024 * 1024
    }
}

class MessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val threadId = intent?.getLongExtra(MessageNotifier.EXTRA_THREAD, -1L) ?: return
        if (threadId < 0) return
        val app = context.applicationContext as? MessagesApp ?: return
        when (intent.action) {
            ACTION_READ -> {
                app.messaging.markRead(threadId)
                MessageNotifier.cancel(context, threadId)
            }
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(MessageNotifier.KEY_REPLY)
                    ?.toString()
                    .orEmpty()
                    .trim()
                if (text.isEmpty()) return
                val fallback = intent.getStringExtra(MessageNotifier.EXTRA_ADDRESS).orEmpty()
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val addresses = app.messaging.addressesFor(threadId).ifEmpty {
                            listOf(fallback).filter { it.isNotBlank() }
                        }
                        app.messaging.send(addresses, text, null, null)
                        app.messaging.markRead(threadId)
                    } catch (t: Throwable) {
                        Log.e("Messages", "Notification reply failed", t)
                        MessageNotifier.simple(context, "Reply failed", t.message ?: "Could not send")
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "app.messages.REPLY"
        const val ACTION_READ = "app.messages.MARK_READ"
    }
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart?.substringBefore('?')?.trim().orEmpty()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getStringExtra("sms_body")
            ?: ""
        if (address.isBlank() || body.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val app = applicationContext as MessagesApp
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    app.messaging.send(listOf(address), body, null, null)
                }
            } catch (t: Throwable) {
                Log.e("Messages", "Headless send failed", t)
            } finally {
                stopSelf(startId)
            }
        }.start()
        return START_NOT_STICKY
    }
}

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("id") ?: return
        val app = context.applicationContext as? MessagesApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = app.local.current().scheduled.find { it.id == id } ?: return@launch
                app.messaging.send(item.addresses, item.body, null, item.subscriptionId)
                app.local.update { state -> state.copy(scheduled = state.scheduled.filter { it.id != id }) }
                val shown = if (app.local.snapshot.notificationPreview) item.body.ifBlank { "Sent" } else "Sent"
                MessageNotifier.simple(context, "Scheduled message sent", shown, MessageNotifier.statusId(item.id))
            } catch (t: Throwable) {
                Log.e("Messages", "Scheduled send failed", t)
                app.local.update { state ->
                    state.copy(scheduled = state.scheduled.map { if (it.id == id) it.copy(failed = true) else it })
                }
                MessageNotifier.simple(context, "Scheduled message failed", t.message ?: "Could not send", MessageNotifier.statusId(item.id))
            } finally {
                pending.finish()
            }
        }
    }
}

class ScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val app = context.applicationContext as? MessagesApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleController.rearmAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}

internal fun smsManager(context: Context, subId: Int): SmsManager {
    val base = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
    if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || subId < 0) return base
    return if (Build.VERSION.SDK_INT >= 31) {
        base.createForSubscriptionId(subId)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getSmsManagerForSubscriptionId(subId)
    }
}

