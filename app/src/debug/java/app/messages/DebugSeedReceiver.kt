package app.messages

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import java.io.File

/** Debug-only hook so a test thread can be created and removed without touching real conversations. */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val app = context.applicationContext as? MessagesApp ?: return
        val address = intent?.getStringExtra("address") ?: return
        when (intent.action) {
            ACTION_INSERT -> {
                val body = intent.getStringExtra("body") ?: "Swipe test"
                val unread = intent.getBooleanExtra("unread", false)
                val whenMs = intent.getLongExtra("when", 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
                val thread = if (intent.getStringExtra("box") == "out" && intent.getStringExtra("file").isNullOrBlank()) {
                    insertSentSms(context, address, body, whenMs)
                } else {
                    app.messaging.insertInbox(address, body, whenMs, -1, unread = unread)
                }
                val media = intent.getStringExtra("file")
                val mime = intent.getStringExtra("mime")
                if (!media.isNullOrBlank() && !mime.isNullOrBlank()) {
                    val outgoing = intent.getStringExtra("box") == "out"
                    val saved = runCatching { insertLocalMedia(context, address, thread, mime, media, outgoing) }
                    Log.i(TAG, if (saved.isSuccess) "debug media mime=$mime bytes=${saved.getOrNull()}" else "debug media failed")
                }
                Log.i(TAG, "debug insert thread=$thread")
                app.messaging.ping()
            }
            ACTION_DELETE -> {
                val removed = app.messaging.deleteAddress(address)
                Log.i(TAG, "debug delete threads=$removed")
            }
        }
    }

    companion object {
        const val ACTION_INSERT = "app.messages.DEBUG_INSERT"
        const val ACTION_DELETE = "app.messages.DEBUG_DELETE"
        private const val TAG = "Messages"

        private fun insertSentSms(context: Context, address: String, body: String, whenMs: Long): Long {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, whenMs)
                put(Telephony.Sms.DATE_SENT, whenMs)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) ?: return -1L
            return Telephony.Threads.getOrCreateThreadId(context, setOf(address)).takeIf { it > 0 }
                ?: uri.lastPathSegment?.toLongOrNull()
                ?: -1L
        }

        private fun insertLocalMedia(context: Context, address: String, threadId: Long, mime: String, path: String, outgoing: Boolean): Int {
            if (mime != "image/gif" && mime != "video/mp4" && mime != "video/3gpp") error("mime")
            val file = File(path).canonicalFile
            val roots = listOf(context.cacheDir, context.filesDir).map { it.canonicalFile.path + File.separator }
            val allowed = roots.any { file.path.startsWith(it) }
            if (!allowed || !file.isFile || file.length() !in 1..300_000) error("file")
            val bytes = file.readBytes()
            val thread = if (threadId > 0) threadId else Telephony.Threads.getOrCreateThreadId(context, setOf(address))
            val box = if (outgoing) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_INBOX
            val values = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, thread)
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.MESSAGE_BOX, box)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
                put(Telephony.Mms.MESSAGE_TYPE, if (outgoing) 128 else 132)
                put(Telephony.Mms.MMS_VERSION, 18)
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
                put(Telephony.Mms.TEXT_ONLY, 0)
            }
            val table = if (outgoing) Telephony.Mms.Sent.CONTENT_URI else Telephony.Mms.Inbox.CONTENT_URI
            val uri = context.contentResolver.insert(table, values) ?: error("mms")
            val id = uri.lastPathSegment ?: error("mms")
            val name = if (mime == "image/gif") "g.gif" else "v.mp4"
            val part = ContentValues().apply {
                put(Telephony.Mms.Part.CONTENT_TYPE, mime)
                put(Telephony.Mms.Part.NAME, name)
                put(Telephony.Mms.Part.CONTENT_LOCATION, name)
            }
            val partUri = context.contentResolver.insert(Uri.parse("content://mms/$id/part"), part) ?: error("part")
            context.contentResolver.openOutputStream(partUri)?.use { it.write(bytes) } ?: error("part")
            val addr = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, if (outgoing) 151 else 137)
                put(Telephony.Mms.Addr.CHARSET, 106)
            }
            context.contentResolver.insert(Uri.parse("content://mms/$id/addr"), addr)
            return bytes.size
        }
    }
}
