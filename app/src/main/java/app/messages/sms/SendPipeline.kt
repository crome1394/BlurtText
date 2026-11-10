package app.messages.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import app.lantext.sms.MmsSender
import app.lantext.sms.MmsSentReceiver
import app.lantext.sms.mmsWireAddress
import app.messages.MessagesApp
import app.messages.data.mimeForPath
import com.google.android.mms.ContentType
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.SendReq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object SendPipeline {
    private const val TAG = "Messages"

    suspend fun send(
        context: Context,
        addresses: List<String>,
        body: String,
        attachment: ByteArray?,
        mime: String?,
        subId: Int?,
        deliveryReports: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        if (!SmsRole.isDefault(context)) {
            throw IllegalStateException("Set BlurtText as the default SMS app to send.")
        }
        val dest = addresses.map { it.trim() }.filter { it.isNotEmpty() }
        if (dest.isEmpty()) throw IllegalStateException("Add a recipient.")
        val text = body.trim()
        if (text.isEmpty() && attachment == null) throw IllegalStateException("Message is empty.")
        try {
            if (attachment != null || dest.size > 1) {
                sendMms(context, dest, text, attachment, mime, subId, deliveryReports)
            } else {
                sendSms(context, dest.first(), text, subId, deliveryReports)
            }
        } catch (t: IllegalStateException) {
            throw IllegalStateException(t.message?.replace("Fossify Messages", "BlurtText")?.replace("Fossify", "BlurtText") ?: "Could not send", t)
        } catch (t: Throwable) {
            Log.e(TAG, "send failed", t)
            throw IllegalStateException(
                (t.message ?: "Could not send").replace("Fossify Messages", "BlurtText").replace("Fossify", "BlurtText"),
                t,
            )
        }
    }

    private fun sendSms(
        context: Context,
        address: String,
        body: String,
        subId: Int?,
        deliveryReports: Boolean,
    ): Long {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
            if (deliveryReports) put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            val id = subId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) put(Telephony.Sms.SUBSCRIPTION_ID, id)
        }
        val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            ?: throw IllegalStateException("Set BlurtText as the default SMS app to send.")
        val manager = manager(context, subId)
        val parts = manager.divideMessage(body)
        PartTracker.begin(uri.toString(), parts.size)
        val sent = ArrayList<PendingIntent>(parts.size)
        val delivered = ArrayList<PendingIntent>(parts.size)
        parts.forEachIndexed { index, _ ->
            sent += pending(context, MessageStatusReceiver.ACTION_SENT, uri, index, parts.size)
            if (deliveryReports) {
                delivered += pending(context, MessageStatusReceiver.ACTION_DELIVERED, uri, index, parts.size)
            }
        }
        if (parts.size <= 1) {
            manager.sendTextMessage(address, null, body, sent.firstOrNull(), delivered.firstOrNull())
        } else {
            manager.sendMultipartTextMessage(
                address,
                null,
                parts,
                sent,
                if (deliveryReports) delivered else null,
            )
        }
        return threadId
    }

    private fun sendMms(
        context: Context,
        addresses: List<String>,
        body: String,
        attachment: ByteArray?,
        mime: String?,
        subId: Int?,
        deliveryReports: Boolean,
    ): Long {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, HashSet(addresses))
        val manager = manager(context, subId)
        val subscription = subId ?: SubscriptionManager.getDefaultSmsSubscriptionId()
        var stored = attachment
        var storedMime = mime
        if (attachment != null) {
            val kind = (mime ?: "image/jpeg").lowercase()
            if (!kind.startsWith("image/") && !kind.startsWith("audio/") && !kind.startsWith("video/")) {
                throw IllegalStateException("MMS can send a picture, GIF, audio clip, or video.")
            }
            val outbound = MmsSender.send(context, manager, addresses, body, attachment, kind, subscription)
            stored = outbound.bytes
            storedMime = outbound.mime
        } else {
            sendTextMms(context, manager, addresses, body)
        }
        if (!recentMmsExists(context, threadId, body)) {
            try {
                insertOutgoingMms(context, threadId, addresses, body, stored, storedMime, subscription)
            } catch (t: Throwable) {
                Log.w(TAG, "Sent MMS was not copied into the conversation", t)
            }
        }
        return threadId
    }

    private fun sendTextMms(
        context: Context,
        manager: SmsManager,
        addresses: List<String>,
        body: String,
    ) {
        val wire = addresses.map { mmsWireAddress(context, it) }.filter { it.isNotEmpty() }
        if (wire.isEmpty()) throw IllegalStateException("Add a recipient.")
        val req = SendReq()
        for (to in wire) req.addTo(EncodedStringValue(to))
        req.setDate(System.currentTimeMillis() / 1000)
        val textBytes = body.toByteArray(Charsets.UTF_8)
        val smilBytes = textSmil().toByteArray(Charsets.UTF_8)
        val pduBody = PduBody()
        val textPart = PduPart()
        textPart.setCharset(CharacterSets.UTF_8)
        textPart.setContentType(ContentType.TEXT_PLAIN.toByteArray())
        textPart.setContentLocation("text_0.txt".toByteArray())
        textPart.setContentId("text_0.txt".toByteArray())
        textPart.setName("text_0.txt".toByteArray())
        textPart.setData(textBytes)
        pduBody.addPart(textPart)
        val smil = PduPart()
        smil.setContentId("<smil>".toByteArray())
        smil.setContentLocation("smil.xml".toByteArray())
        smil.setContentType(ContentType.APP_SMIL.toByteArray())
        smil.setName("smil.xml".toByteArray())
        smil.setData(smilBytes)
        pduBody.addPart(0, smil)
        req.setBody(pduBody)
        req.setMessageSize((textBytes.size + smilBytes.size).toLong())
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray())
        req.setExpiry(7 * 24 * 60 * 60)
        try {
            req.setPriority(PduHeaders.PRIORITY_NORMAL)
            req.setDeliveryReport(if (deliveryReports) PduHeaders.VALUE_YES else PduHeaders.VALUE_NO)
            req.setReadReport(PduHeaders.VALUE_NO)
        } catch (_: InvalidHeaderValueException) {
        }
        val pdu = PduComposer(context, req).make()
            ?: throw IllegalStateException("Could not build the MMS.")
        dispatchPdu(context, manager, pdu)
    }

    private fun dispatchPdu(context: Context, manager: SmsManager, pdu: ByteArray) {
        val fileName = "send." + UUID.randomUUID() + ".dat"
        File(context.cacheDir, fileName).writeBytes(pdu)
        val contentUri = Uri.Builder()
            .authority(context.packageName + ".MmsFileProvider")
            .path(fileName)
            .scheme("content")
            .build()
        for (pkg in listOf("com.android.mms.service", "com.android.phone", "com.android.providers.telephony")) {
            try {
                context.grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }
        val waiter = MmsSentReceiver.register(fileName)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
        val sent = PendingIntent.getBroadcast(
            context,
            fileName.hashCode(),
            Intent(context, MmsSentReceiver::class.java).putExtra(MmsSentReceiver.EXTRA_ID, fileName),
            flags,
        )
        manager.sendMultimediaMessage(context, contentUri, null, null, sent)
        val finished = waiter.latch.await(30, TimeUnit.SECONDS)
        if (!finished) throw IllegalStateException("The MMS is still sending. Leave mobile data on and try again.")
        if (waiter.code.get() != Activity.RESULT_OK) {
            throw IllegalStateException("Could not send the MMS (error ${waiter.code.get()}). Mobile data has to be on.")
        }
    }

    private fun recentMmsExists(context: Context, threadId: Long, body: String): Boolean {
        val since = (System.currentTimeMillis() / 1000) - if (body.isBlank()) 20 else 180
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.THREAD_ID}=? AND ${Telephony.Mms.DATE}>=?",
            arrayOf(threadId.toString(), since.toString()),
            null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            while (cursor.moveToNext()) ids += cursor.getLong(id)
        }
        if (ids.isEmpty()) return false
        if (body.isBlank()) return ids.any { mmsHasMedia(context, it) }
        val needle = body.take(80)
        return ids.any { mmsText(context, it).contains(needle) }
    }

    private fun mmsText(context: Context, mmsId: Long): String {
        val uri = Uri.parse("content://mms/$mmsId/part")
        val out = StringBuilder()
        context.contentResolver.query(
            uri,
            arrayOf(Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT),
            null,
            null,
            null,
        )?.use { cursor ->
            val type = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
            val text = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(type).orEmpty()
                if (mime.startsWith("text/")) out.append(cursor.getString(text).orEmpty())
            }
        }
        return out.toString()
    }

    private fun mmsHasMedia(context: Context, mmsId: Long): Boolean {
        var found = false
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/part"),
            arrayOf(Telephony.Mms.Part.CONTENT_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            val type = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(type).orEmpty()
                if (mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/")) {
                    found = true
                }
            }
        }
        return found
    }

    private fun insertOutgoingMms(
        context: Context,
        threadId: Long,
        addresses: List<String>,
        body: String,
        attachment: ByteArray?,
        mime: String?,
        subId: Int,
    ) {
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.MESSAGE_TYPE, 128)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.TEXT_ONLY, if (attachment == null) 1 else 0)
            put(Telephony.Mms.MESSAGE_SIZE, attachment?.size ?: body.length)
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Mms.SUBSCRIPTION_ID, subId)
            }
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Sent.CONTENT_URI, values) ?: return
        val id = uri.lastPathSegment?.toLongOrNull() ?: return
        for (address in addresses) {
            val addr = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, 151)
                put(Telephony.Mms.Addr.CHARSET, 106)
            }
            context.contentResolver.insert(Uri.parse("content://mms/$id/addr"), addr)
        }
        if (body.isNotBlank()) {
            val part = ContentValues().apply {
                put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                put(Telephony.Mms.Part.CHARSET, 106)
                put(Telephony.Mms.Part.TEXT, body)
                put(Telephony.Mms.Part.CONTENT_LOCATION, "text_0.txt")
            }
            context.contentResolver.insert(Uri.parse("content://mms/$id/part"), part)
        }
        if (attachment != null) {
            val kind = mime ?: "image/jpeg"
            val part = ContentValues().apply {
                put(Telephony.Mms.Part.CONTENT_TYPE, kind)
                put(Telephony.Mms.Part.NAME, "media." + extensionOf(kind))
                put(Telephony.Mms.Part.CONTENT_LOCATION, "media." + extensionOf(kind))
            }
            val partUri = context.contentResolver.insert(Uri.parse("content://mms/$id/part"), part)
            if (partUri != null) {
                context.contentResolver.openOutputStream(partUri)?.use { it.write(attachment) }
            }
        }
    }

    private fun pending(context: Context, action: String, uri: Uri, index: Int, count: Int): PendingIntent {
        val intent = Intent(context, MessageStatusReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("messages://status/${uri.lastPathSegment}/$action/$index")
            putExtra(MessageStatusReceiver.EXTRA_URI, uri.toString())
            putExtra(MessageStatusReceiver.EXTRA_INDEX, index)
            putExtra(MessageStatusReceiver.EXTRA_COUNT, count)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, PartTracker.requestCode(), intent, flags)
    }

    private fun manager(context: Context, subId: Int?): SmsManager {
        val base = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        val id = subId ?: SubscriptionManager.getDefaultSmsSubscriptionId()
        if (id == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return base
        return if (Build.VERSION.SDK_INT >= 31) {
            base.createForSubscriptionId(id)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(id)
        }
    }

    private fun textSmil(): String =
        """<smil><head><layout><root-layout width="320px" height="480px"/><region id="Text" top="0" left="0" height="100%" fit="scroll"/></layout></head><body><par dur="5000ms"><text src="text_0.txt" region="Text"/></par></body></smil>"""

    fun extensionOf(mime: String): String = when {
        mime.contains("png") -> "png"
        mime.contains("gif") -> "gif"
        mime.contains("webp") -> "webp"
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.startsWith("audio/") -> "amr"
        else -> "bin"
    }

    fun mimeFor(path: String): String = mimeForPath(path)

    fun mediaBudget(context: Context): Int = MmsSender.mediaBudget(manager(context, null))
}

object PartTracker {
    private val pending = ConcurrentHashMap<String, AtomicInteger>()
    private val failed = ConcurrentHashMap<String, AtomicBoolean>()
    private val codes = AtomicInteger(1000)

    fun requestCode(): Int = codes.incrementAndGet()

    fun begin(key: String, parts: Int) {
        pending[key] = AtomicInteger(parts)
        failed[key] = AtomicBoolean(false)
    }

    fun onPart(key: String, ok: Boolean): Boolean {
        if (!ok) failed[key]?.set(true)
        val left = pending[key]?.decrementAndGet() ?: 0
        return left <= 0
    }

    fun failed(key: String): Boolean {
        val value = failed.remove(key)?.get() ?: false
        pending.remove(key)
        return value
    }
}

fun finishSmsStatus(context: Context, uri: Uri, failed: Boolean) {
    val values = ContentValues().apply {
        put(
            Telephony.Sms.TYPE,
            if (failed) Telephony.Sms.MESSAGE_TYPE_FAILED else Telephony.Sms.MESSAGE_TYPE_SENT,
        )
    }
    context.contentResolver.update(uri, values, null, null)
    (context.applicationContext as? MessagesApp)?.messaging?.ping()
}
