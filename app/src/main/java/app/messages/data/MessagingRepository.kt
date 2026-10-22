@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.messages.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.lantext.LanTextApp
import app.lantext.sms.echoStoredTimestamp
import app.lantext.sms.orderEchoes
import app.lantext.util.PhoneNumbers
import app.lantext.sms.mmsWireAddress
import app.messages.sms.MessageNotifier
import app.messages.sms.MmsRetrieveParser
import app.messages.sms.PendingMms
import app.messages.sms.SendPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class MessagingRepository(
    private val context: Context,
    private val local: LocalStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val ticks = MutableStateFlow(0)
    private val canonical = HashMap<String, String>()
    private var observer: ContentObserver? = null
    private var observerThread: HandlerThread? = null
    private var namesAt = 0L
    private var namesCache: Map<String, NameEntry> = emptyMap()

    val error = MutableStateFlow<String?>(null)

    val conversations: StateFlow<List<Conversation>> = ticks
        .flatMapLatest {
            flow { emit(withContext(Dispatchers.IO) { loadConversations() }) }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun start() {
        if (observer != null) return
        val thread = HandlerThread("messages-observer").also { it.start() }
        observerThread = thread
        val obs = object : ContentObserver(Handler(thread.looper)) {
            override fun onChange(selfChange: Boolean) {
                ping()
            }
        }
        observer = obs
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        context.contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, obs)
        context.contentResolver.registerContentObserver(Telephony.Threads.CONTENT_URI, true, obs)
        ping()
    }

    fun ping() {
        main.removeCallbacks(pingRunnable)
        main.postDelayed(pingRunnable, 120)
    }

    private val pingRunnable = Runnable { ticks.value = ticks.value + 1 }

    fun observeMessages(threadId: Long): Flow<List<ChatMessage>> = ticks.flatMapLatest {
        flow { emit(withContext(Dispatchers.IO) { loadMessages(threadId) }) }
    }

    fun hasContacts(): Boolean = granted(Manifest.permission.READ_CONTACTS)

    fun contactLookupUri(address: String): Uri? {
        if (address.isBlank() || !hasContacts()) return null
        val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return try {
            context.contentResolver.query(
                lookup,
                arrayOf(ContactsContract.PhoneLookup.CONTACT_ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.CONTACT_ID))
                val key = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY))
                if (key.isNullOrBlank()) null else ContactsContract.Contacts.getLookupUri(id, key)
            }
        } catch (_: SecurityException) {
            null
        }
    }

    fun addNumberToContact(contactUri: Uri, number: String): Boolean {
        if (number.isBlank() || !granted(Manifest.permission.WRITE_CONTACTS)) return false
        return try {
            val contactId = context.contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null } ?: return false
            val rawId = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID}=?",
                arrayOf(contactId.toString()),
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null } ?: return false
            val values = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            val inserted = context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
            namesAt = 0L
            inserted != null
        } catch (_: SecurityException) {
            false
        }
    }

    /** Name and phone numbers for a picked contact, one line each. */
    fun contactCard(contactUri: Uri): String? {
        if (!hasContacts()) return null
        return try {
            val identity = context.contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (idIndex < 0) return@use null
                cursor.getLong(idIndex) to if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            } ?: return null
            val (contactId, name) = identity
            val numbers = mutableListOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(contactId.toString()),
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (index < 0) return@use
                while (cursor.moveToNext()) {
                    val number = cursor.getString(index)?.trim().orEmpty()
                    if (number.isNotBlank() && number !in numbers) numbers += number
                }
            }
            buildString {
                if (name.isNotBlank()) append(name.trim())
                numbers.forEach { number ->
                    if (isNotEmpty()) append('\n')
                    append(number)
                }
            }.ifBlank { null }
        } catch (_: SecurityException) {
            null
        }
    }

    suspend fun send(addresses: List<String>, body: String, attachmentPath: String?, subId: Int?): Long {
        val bytes = attachmentPath?.let { path -> File(path).takeIf { it.exists() }?.readBytes() }
        val mime = attachmentPath?.let { mimeForPath(it) }
        val thread = SendPipeline.send(context, addresses, body, bytes, mime, subId, local.snapshot.deliveryReports)
        (context.applicationContext as? LanTextApp)?.sms?.emitRefresh()
        ping()
        return thread
    }

    fun streamSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                if (fd.statSize > 0) fd.statSize else -1L
            } ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }

    fun copyAttachment(uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val dir = File(context.filesDir, "outgoing").apply { mkdirs() }
        val file = File(dir, "attach-${System.currentTimeMillis()}.${extensionFor(mime)}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: error("Could not read that attachment")
        if (file.length() == 0L) {
            file.delete()
            error("Could not read that attachment")
        }
        return file.absolutePath
    }

    fun saveOutgoing(bytes: ByteArray, mime: String): String {
        val dir = File(context.filesDir, "outgoing").apply { mkdirs() }
        val file = File(dir, "attach-${System.currentTimeMillis()}.${extensionFor(mime)}")
        file.writeBytes(bytes)
        if (file.length() == 0L) {
            file.delete()
            error("Could not save that attachment")
        }
        return file.absolutePath
    }

    fun releaseAttachment(path: String?) {
        if (path.isNullOrBlank()) return
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        val outgoing = File(context.filesDir, "outgoing").canonicalFile
        val cache = context.cacheDir.canonicalFile
        val allowed = target.path.startsWith(outgoing.path + File.separator) ||
            (target.path.startsWith(cache.path + File.separator) && target.name.startsWith("attach-"))
        if (allowed) target.delete()
    }

    fun insertInbox(address: String, body: String, timestamp: Long, subId: Int, unread: Boolean): Long {
        val stored = echoTimestamp(address, body, timestamp)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, stored)
            put(Telephony.Sms.DATE_SENT, stored)
            put(Telephony.Sms.READ, if (unread) 0 else 1)
            put(Telephony.Sms.SEEN, if (unread) 0 else 1)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return -1L
        return threadOf(uri)
    }

    private fun echoTimestamp(address: String, body: String, incoming: Long): Long {
        val text = body.trim()
        val digits = address.filter { it.isDigit() }
        if (text.isEmpty() || digits.isEmpty()) return incoming
        val sent = ArrayList<Long>()
        val since = (incoming - app.lantext.sms.SELF_ECHO_WINDOW_MS).coerceAtLeast(0L)
        context.contentResolver.query(
            Telephony.Sms.Sent.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE, Telephony.Sms.BODY, Telephony.Sms.ADDRESS),
            "${Telephony.Sms.DATE}>=?",
            arrayOf(since.toString()),
            null,
        )?.use { cursor ->
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            while (cursor.moveToNext()) {
                if (cursor.getString(bodyIndex).orEmpty().trim() != text) continue
                val who = cursor.getString(addressIndex).orEmpty().filter { it.isDigit() }
                if (who != digits) continue
                sent += cursor.getLong(dateIndex)
            }
        }
        return echoStoredTimestamp(incoming, sent)
    }

    fun deleteAddress(address: String): Int {
        val threads = HashSet<Long>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.ADDRESS}=?",
            arrayOf(address),
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            while (cursor.moveToNext()) threads += cursor.getLong(index)
        }
        var removed = 0
        for (thread in threads) {
            val others = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.ADDRESS}!=?",
                arrayOf(thread.toString(), address),
                null,
            )?.use { it.count } ?: 0
            if (others == 0) {
                context.contentResolver.delete(conversationUri(thread), null, null)
                removed += 1
            } else {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.ADDRESS}=?",
                    arrayOf(address),
                )
            }
        }
        ping()
        return removed
    }

    fun deleteThreads(ids: Collection<Long>) {
        for (id in ids) {
            context.contentResolver.delete(conversationUri(id), null, null)
        }
        ping()
    }

    fun deleteMessage(message: ChatMessage) {
        val uri = when {
            message.smsId != null -> ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, message.smsId)
            message.mmsId != null -> ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, message.mmsId)
            else -> return
        }
        context.contentResolver.delete(uri, null, null)
        ping()
    }

    fun setArchived(ids: Collection<Long>, archived: Boolean) {
        val values = ContentValues().apply { put("archived", if (archived) 1 else 0) }
        for (id in ids) {
            context.contentResolver.update(
                ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, id),
                values,
                null,
                null,
            )
        }
        ping()
    }

    fun markRead(threadId: Long) {
        val sms = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            sms,
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
        )
        val mms = ContentValues().apply {
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
        }
        context.contentResolver.update(
            Telephony.Mms.CONTENT_URI,
            mms,
            "${Telephony.Mms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
        )
        MessageNotifier.cancel(context, threadId)
        (context.applicationContext as? LanTextApp)?.sms?.markRead(threadId.toString())
        ping()
    }

    fun markUnread(threadId: Long): Boolean {
        val hit = newestIncoming(threadId) ?: return false
        val values = ContentValues().apply {
            put("read", 0)
            put("seen", 0)
        }
        val rows = context.contentResolver.update(hit, values, null, null)
        ping()
        return rows > 0
    }

    fun isBlocked(address: String): Boolean = try {
        address.isNotBlank() && BlockedNumberContract.isBlocked(context, address)
    } catch (_: Exception) {
        false
    }

    fun block(numbers: List<String>) {
        for (number in numbers) {
            if (number.isBlank() || isBlocked(number)) continue
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
        }
    }

    fun unblock(number: String) {
        context.contentResolver.delete(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER}=?",
            arrayOf(number),
        )
    }

    fun blockedNumbers(): List<String> {
        val out = mutableListOf<String>()
        context.contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(index).orEmpty()
                if (number.isNotBlank()) out += number
            }
        }
        return out
    }

    fun displayAddress(address: String): String {
        val region = PhoneNumbers.defaultRegion(context)
        return display(names(), address, region)
    }

    fun addressesFor(threadId: Long): List<String> {
        val fromThread = resolveRecipients(recipientIds(threadId))
        if (fromThread.isNotEmpty()) return fromThread
        return fallbackAddresses(threadId)
    }

    fun defaultSmsSubscription(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()

    fun simLines(): List<SimLine> {
        if (!granted(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            manager.activeSubscriptionInfoList.orEmpty().map { info ->
                val name = info.displayName?.toString()?.trim().orEmpty()
                    .ifBlank { info.carrierName?.toString()?.trim().orEmpty() }
                SimLine(info.subscriptionId, info.simSlotIndex.coerceAtLeast(0), name)
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun suggest(query: String): List<ContactHit> {
        if (!hasContacts()) return emptyList()
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val like = "%$q%"
        val out = mutableListOf<ContactHit>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf(like, like),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
        )?.use { cursor ->
            val name = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val number = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photo = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            while (cursor.moveToNext() && out.size < 8) {
                out += ContactHit(
                    name = cursor.getString(name).orEmpty(),
                    number = cursor.getString(number).orEmpty(),
                    photoUri = if (photo >= 0) cursor.getString(photo) else null,
                )
            }
        }
        return out
    }

    fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val byId = loadConversations().associateBy { it.threadId }
        val hits = mutableListOf<SearchHit>()
        for (conversation in byId.values) {
            val hay = conversation.title + " " + conversation.addresses.joinToString(" ") + " " + conversation.snippet
            if (hay.contains(q, ignoreCase = true)) {
                hits += SearchHit(conversation.threadId, conversation.title, conversation.snippet, conversation.timestamp)
            }
        }
        val like = "%$q%"
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.BODY} LIKE ? COLLATE NOCASE",
            arrayOf(like),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val thread = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var count = 0
            while (cursor.moveToNext() && count < 60) {
                val id = cursor.getLong(thread)
                val conversation = byId[id] ?: continue
                hits += SearchHit(id, conversation.title, cursor.getString(body).orEmpty(), cursor.getLong(date))
                count += 1
            }
        }
        return hits.distinctBy { "${it.threadId}:${it.snippet}" }.take(80)
    }

    fun loadPart(partId: Long): ByteArray? = try {
        context.contentResolver.openInputStream(Uri.parse("content://mms/part/$partId"))?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    fun stagePart(partId: Long, mime: String): Uri? {
        val bytes = loadPart(partId) ?: return null
        val file = File(context.cacheDir, "part-$partId.${extensionFor(mime)}")
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    fun exportTo(file: File) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            writer.write("{\"version\":1,\"messages\":[")
            var first = true
            fun writeMessage(address: String, body: String, timestamp: Long, incoming: Boolean, type: String) {
                if (!first) writer.write(",")
                first = false
                writer.write("{\"address\":")
                writer.write(jsonString(address))
                writer.write(",\"body\":")
                writer.write(jsonString(body))
                writer.write(",\"timestamp\":")
                writer.write(timestamp.toString())
                writer.write(",\"incoming\":")
                writer.write(incoming.toString())
                writer.write(",\"type\":")
                writer.write(jsonString(type))
                writer.write("}")
            }
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null,
                null,
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (cursor.moveToNext()) {
                    val kind = cursor.getInt(type)
                    if (kind == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                    writeMessage(
                        cursor.getString(address).orEmpty(),
                        cursor.getString(body).orEmpty(),
                        cursor.getLong(date),
                        kind == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        "sms",
                    )
                }
            }
            writer.write("]}")
        }
    }

    fun importFrom(text: String): Int {
        val backup = Json { ignoreUnknownKeys = true }.decodeFromString(BackupFile.serializer(), text)
        val existing = HashSet<String>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            null,
        )?.use { cursor ->
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                existing += key(cursor.getString(address).orEmpty(), cursor.getLong(date), cursor.getString(body).orEmpty())
            }
        }
        var added = 0
        for (message in backup.messages) {
            if (message.type != "sms" || message.body.isBlank() || message.address.isBlank()) continue
            val id = key(message.address, message.timestamp, message.body)
            if (!existing.add(id)) continue
            val uri = if (message.incoming) Telephony.Sms.Inbox.CONTENT_URI else Telephony.Sms.Sent.CONTENT_URI
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, message.address)
                put(Telephony.Sms.BODY, message.body)
                put(Telephony.Sms.DATE, message.timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.insert(uri, values)
            added += 1
        }
        ping()
        return added
    }

    fun exportThread(threadId: Long): String {
        val messages = orderEchoes(
            loadMessages(threadId),
            { it.key },
            { it.threadId.toString() },
            { it.body },
            { it.timestamp },
            { it.incoming },
        )
        return buildString {
            val title = loadConversations().firstOrNull { it.threadId == threadId }?.title ?: threadId.toString()
            append(title).append('\n')
            for (message in messages) {
                append(if (message.incoming) message.senderName else "Me")
                append(" · ")
                append(formatFullStamp(message.timestamp))
                append('\n')
                if (message.body.isNotBlank()) append(message.body).append('\n')
                if (message.attachments.isNotEmpty()) append("[attachment]\n")
                append('\n')
            }
        }
    }

    fun acceptMmsNotification(notification: app.messages.sms.MmsNotificationParser.Notification, subId: Int): Uri? {
        if (notification.transactionId.isNotBlank()) {
            var existing: Uri? = null
            var existingType = -1
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.MESSAGE_TYPE),
                "${Telephony.Mms.TRANSACTION_ID}=?",
                arrayOf(notification.transactionId),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    existingType = cursor.getInt(1)
                    existing = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, cursor.getLong(0))
                }
            }
            if (existing != null) return if (existingType == 130) existing else null
        }
        val values = ContentValues().apply {
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_TYPE, 130)
            put(Telephony.Mms.MMS_VERSION, 18)
            put(Telephony.Mms.CONTENT_LOCATION, notification.contentLocation)
            put(Telephony.Mms.TRANSACTION_ID, notification.transactionId)
            if (notification.size > 0) put(Telephony.Mms.MESSAGE_SIZE, notification.size.toInt())
            if (subId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subId)
            val who = mmsWireAddress(context, notification.from)
            if (who.isNotBlank()) {
                try {
                    put(Telephony.Mms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(context, hashSetOf(who)))
                } catch (_: Exception) {
                }
            }
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = uri.lastPathSegment?.toLongOrNull()
        val storedFrom = mmsWireAddress(context, notification.from).ifBlank { notification.from }
        if (id != null && storedFrom.isNotBlank()) {
            val addr = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, storedFrom)
                put(Telephony.Mms.Addr.TYPE, 137)
                put(Telephony.Mms.Addr.CHARSET, 106)
            }
            context.contentResolver.insert(Uri.parse("content://mms/$id/addr"), addr)
        }
        return uri
    }

    fun pendingMmsNotifications(): List<PendingMms> {
        val since = (System.currentTimeMillis() / 1000) - 3 * 24 * 60 * 60
        val out = mutableListOf<PendingMms>()
        context.contentResolver.query(
            Telephony.Mms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.MESSAGE_TYPE,
                Telephony.Mms.DATE,
                Telephony.Mms.CONTENT_LOCATION,
                Telephony.Mms.SUBSCRIPTION_ID,
            ),
            "${Telephony.Mms.MESSAGE_TYPE}=? AND ${Telephony.Mms.DATE}>=?",
            arrayOf("130", since.toString()),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(Telephony.Mms._ID)
            val typeCol = cursor.getColumnIndex(Telephony.Mms.MESSAGE_TYPE)
            val dateCol = cursor.getColumnIndex(Telephony.Mms.DATE)
            val locationCol = cursor.getColumnIndex(Telephony.Mms.CONTENT_LOCATION)
            val subCol = cursor.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)
            if (idCol < 0 || typeCol < 0 || dateCol < 0 || locationCol < 0) return emptyList()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val type = cursor.getInt(typeCol)
                val whenSec = cursor.getLong(dateCol)
                if (type != 130 || whenSec < since) continue
                val location = cursor.getString(locationCol).orEmpty()
                if (location.isBlank()) continue
                val parts = mmsParts(id)
                if (parts.any { it.mime.startsWith("image/") || it.mime.startsWith("text/") || it.mime.startsWith("audio/") }) {
                    markRetrieved(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id), parts)
                    continue
                }
                val sub = if (subCol >= 0) cursor.getInt(subCol) else -1
                out += PendingMms(
                    placeholder = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id),
                    location = location,
                    from = mmsSender(id, Telephony.Mms.MESSAGE_BOX_INBOX),
                    subId = sub,
                )
            }
        }
        Log.i(TAG, "pending picture notifications=${out.size}")
        return out
    }

    fun storeRetrievedMms(placeholder: Uri, pdu: ByteArray, fallbackFrom: String): Long {
        val id = placeholder.lastPathSegment?.toLongOrNull() ?: return -1L
        val existing = mmsParts(id)
        if (existing.any { it.mime.startsWith("image/") || it.mime.startsWith("text/") || it.mime.startsWith("audio/") }) {
            markRetrieved(placeholder, existing)
            val thread = threadOf(placeholder)
            return if (thread >= 0) thread else 0L
        }
        val parsed = MmsRetrieveParser.parse(pdu)
        if (parsed == null) {
            Log.w(TAG, "Retrieved MMS could not be read bytes=${pdu.size}")
            return -1L
        }
        val visible = parsed.parts.filter {
            it.mime.startsWith("image/") || it.mime.startsWith("audio/") || it.mime.startsWith("video/") || it.mime.startsWith("text/")
        }
        if (visible.isEmpty()) {
            Log.w(TAG, "Retrieved MMS had no picture or text bytes=${pdu.size}")
            return -1L
        }
        for (part in visible) {
            val name = part.name.substringAfterLast('/').substringAfterLast('\\').filter { it.code in 32..126 }.take(40)
                .ifBlank { "part" }
            val values = ContentValues().apply {
                put(Telephony.Mms.Part.CONTENT_TYPE, part.mime)
                put(Telephony.Mms.Part.NAME, name)
                put(Telephony.Mms.Part.CONTENT_LOCATION, name)
                if (part.mime.startsWith("text/")) {
                    put(Telephony.Mms.Part.TEXT, part.bytes.toString(Charsets.UTF_8))
                    put(Telephony.Mms.Part.CHARSET, 106)
                }
            }
            val partUri = context.contentResolver.insert(Uri.parse("content://mms/$id/part"), values) ?: continue
            if (!part.mime.startsWith("text/") && part.bytes.isNotEmpty()) {
                context.contentResolver.openOutputStream(partUri)?.use { it.write(part.bytes) }
            }
        }
        val who = mmsWireAddress(context, parsed.from.ifBlank { fallbackFrom }).ifBlank { fallbackFrom }
        if (who.isNotBlank() && mmsSender(id, Telephony.Mms.MESSAGE_BOX_INBOX).isBlank()) {
            val addr = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, who)
                put(Telephony.Mms.Addr.TYPE, 137)
                put(Telephony.Mms.Addr.CHARSET, 106)
            }
            context.contentResolver.insert(Uri.parse("content://mms/$id/addr"), addr)
        }
        if (who.isNotBlank() && threadOf(placeholder) <= 0) {
            try {
                val thread = Telephony.Threads.getOrCreateThreadId(context, hashSetOf(who))
                val threadValues = ContentValues().apply { put(Telephony.Mms.THREAD_ID, thread) }
                context.contentResolver.update(placeholder, threadValues, null, null)
            } catch (_: Exception) {
            }
        }
        if (parsed.dateSeconds > 0) {
            val dated = ContentValues().apply { put(Telephony.Mms.DATE, parsed.dateSeconds) }
            context.contentResolver.update(placeholder, dated, null, null)
        }
        markRetrieved(placeholder, visible.map { Part(0, it.mime, null) })
        val thread = threadOf(placeholder)
        return if (thread >= 0) thread else 0L
    }

    private fun markRetrieved(uri: Uri, parts: List<Part>) {
        val media = parts.any { it.mime.startsWith("image/") || it.mime.startsWith("audio/") || it.mime.startsWith("video/") }
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_TYPE, 132)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.TEXT_ONLY, if (media) 0 else 1)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    fun threadOf(uri: Uri): Long = context.contentResolver.query(
        uri,
        arrayOf("thread_id"),
        null,
        null,
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L } ?: -1L

    private fun loadConversations(): List<Conversation> {
        return try {
            val persisted = local.snapshot
            val pins = persisted.pins.toSet()
            val names = names()
            val region = PhoneNumbers.defaultRegion(context)
            val unread = unreadCounts()
            val out = mutableListOf<Conversation>()
            val uri = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
            context.contentResolver.query(uri, null, null, null, "date DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndex("_id")
                val dateIndex = cursor.getColumnIndex("date")
                val snippetIndex = cursor.getColumnIndex("snippet")
                val recipientIndex = cursor.getColumnIndex("recipient_ids")
                val archivedIndex = cursor.getColumnIndex("archived")
                val attachmentIndex = cursor.getColumnIndex("has_attachment")
                if (idIndex < 0) return@use
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val recipients = if (recipientIndex >= 0) cursor.getString(recipientIndex).orEmpty() else ""
                    var addresses = resolveRecipients(recipients)
                    if (addresses.isEmpty()) addresses = fallbackAddresses(id)
                    val labels = addresses.map { display(names, it, region) }
                    val draftText = persisted.drafts[draftKey(id)].orEmpty()
                    val attachmentPath = persisted.attachments[draftKey(id)]?.takeIf { File(it).isFile }
                    val draft = when {
                        draftText.isNotBlank() -> draftText
                        attachmentPath != null && mimeForPath(attachmentPath) == "image/gif" -> "GIF"
                        attachmentPath != null && mimeForPath(attachmentPath).startsWith("image/") -> "Picture"
                        attachmentPath != null && mimeForPath(attachmentPath).startsWith("video/") -> "Video"
                        attachmentPath != null -> "Attachment"
                        else -> ""
                    }
                    val rawSnippet = if (snippetIndex >= 0) cursor.getString(snippetIndex).orEmpty() else ""
                    val hasAttachment = attachmentIndex >= 0 && cursor.getInt(attachmentIndex) == 1
                    out += Conversation(
                        threadId = id,
                        addresses = addresses,
                        title = formatThreadTitle(labels),
                        snippet = rawSnippet.ifBlank { if (hasAttachment) "Picture" else "" },
                        timestamp = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                        unread = unread[id] ?: 0,
                        archived = archivedIndex >= 0 && cursor.getInt(archivedIndex) == 1,
                        pinned = id in pins,
                        draft = draft,
                        photoUri = addresses.firstNotNullOfOrNull { lookup(names, it, region)?.photo },
                    )
                }
            }
            error.value = null
            out.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.timestamp })
        } catch (e: SecurityException) {
            error.value = "Allow message access, or set lMessages as the default SMS app."
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "load conversations", e)
            error.value = e.message ?: "Could not load conversations."
            emptyList()
        }
    }

    private fun loadMessages(threadId: Long): List<ChatMessage> {
        val names = names()
        val region = PhoneNumbers.defaultRegion(context)
        val out = mutableListOf<ChatMessage>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.STATUS,
                Telephony.Sms.SUBSCRIPTION_ID,
            ),
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val thread = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val status = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            val sub = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            var count = 0
            while (cursor.moveToNext() && count < 2000) {
                val kind = cursor.getInt(type)
                if (kind == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                val who = cursor.getString(address).orEmpty()
                out += ChatMessage(
                    smsId = cursor.getLong(id),
                    mmsId = null,
                    threadId = cursor.getLong(thread),
                    address = who,
                    senderName = display(names, who, region),
                    body = cursor.getString(body).orEmpty(),
                    timestamp = cursor.getLong(date),
                    incoming = kind == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = smsStatus(kind, cursor.getInt(status)),
                    subId = if (sub >= 0) cursor.getInt(sub) else -1,
                    attachments = emptyList(),
                )
                count += 1
            }
        }
        out += loadMms(threadId, names, region)
        return out.sortedByDescending { it.timestamp }.take(2000)
    }

    private fun loadMms(threadId: Long, names: Map<String, NameEntry>, region: String): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.SUBSCRIPTION_ID,
            ),
            "${Telephony.Mms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Mms.DATE} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            val thread = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
            val date = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
            val boxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
            val sub = cursor.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)
            var count = 0
            while (cursor.moveToNext() && count < 500) {
                val mmsId = cursor.getLong(id)
                val box = cursor.getInt(boxIndex)
                if (box == Telephony.Mms.MESSAGE_BOX_DRAFTS) continue
                val parts = mmsParts(mmsId)
                val text = parts.firstOrNull { it.mime.startsWith("text/") }?.text.orEmpty()
                val attachments = parts.filter {
                    it.mime.startsWith("image/") || it.mime.startsWith("video/") || it.mime.startsWith("audio/")
                }.map { PartRef(it.id, it.mime) }
                if (text.isBlank() && attachments.isEmpty() && box == Telephony.Mms.MESSAGE_BOX_INBOX) {
                    val type = mmsType(mmsId)
                    if (type == 130) continue
                }
                val who = mmsSender(mmsId, box)
                out += ChatMessage(
                    smsId = null,
                    mmsId = mmsId,
                    threadId = cursor.getLong(thread),
                    address = who,
                    senderName = display(names, who, region),
                    body = text,
                    timestamp = mmsMillis(cursor.getLong(date)),
                    incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX,
                    status = mmsStatus(box),
                    subId = if (sub >= 0) cursor.getInt(sub) else -1,
                    attachments = attachments,
                )
                count += 1
            }
        }
        return out
    }

    private fun mmsType(mmsId: Long): Int = context.contentResolver.query(
        ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId),
        arrayOf(Telephony.Mms.MESSAGE_TYPE),
        null,
        null,
        null,
    )?.use { if (it.moveToFirst()) it.getInt(0) else -1 } ?: -1

    private data class Part(val id: Long, val mime: String, val text: String?)

    private fun mmsParts(mmsId: Long): List<Part> {
        val out = mutableListOf<Part>()
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT),
            null,
            null,
            null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._ID)
            val type = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
            val text = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(type).orEmpty()
                if (mime == "application/smil") continue
                out += Part(cursor.getLong(id), mime, cursor.getString(text))
            }
        }
        return out
    }

    private fun mmsSender(mmsId: Long, box: Int): String {
        val want = if (box == Telephony.Mms.MESSAGE_BOX_INBOX) 137 else 151
        var fallback = ""
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            val address = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
            val type = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
            while (cursor.moveToNext()) {
                val value = cursor.getString(address).orEmpty()
                if (value.isBlank() || value == "insert-address-token") continue
                val kind = cursor.getInt(type)
                if (kind == want) return value.substringBefore("/")
                if (fallback.isBlank()) fallback = value.substringBefore("/")
            }
        }
        return fallback
    }

    private fun newestIncoming(threadId: Long): Uri? {
        var smsUri: Uri? = null
        var smsAt = -1L
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.DATE),
            "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.TYPE}=?",
            arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                smsAt = cursor.getLong(1)
                smsUri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, cursor.getLong(0))
            }
        }
        var mmsUri: Uri? = null
        var mmsAt = -1L
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
            "${Telephony.Mms.THREAD_ID}=? AND ${Telephony.Mms.MESSAGE_BOX}=?",
            arrayOf(threadId.toString(), Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
            "${Telephony.Mms.DATE} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                mmsAt = mmsMillis(cursor.getLong(1))
                mmsUri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, cursor.getLong(0))
            }
        }
        return when {
            smsUri == null -> mmsUri
            mmsUri == null -> smsUri
            mmsAt > smsAt -> mmsUri
            else -> smsUri
        }
    }

    private fun unreadCounts(): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        fun add(uri: Uri, threadColumn: String, selection: String) {
            context.contentResolver.query(uri, arrayOf(threadColumn), selection, null, null)?.use { cursor ->
                val index = cursor.getColumnIndexOrThrow(threadColumn)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(index)
                    counts[id] = (counts[id] ?: 0) + 1
                }
            }
        }
        add(
            Telephony.Sms.CONTENT_URI,
            Telephony.Sms.THREAD_ID,
            "${Telephony.Sms.TYPE}=${Telephony.Sms.MESSAGE_TYPE_INBOX} AND ${Telephony.Sms.READ}=0",
        )
        add(
            Telephony.Mms.CONTENT_URI,
            Telephony.Mms.THREAD_ID,
            "${Telephony.Mms.MESSAGE_BOX}=${Telephony.Mms.MESSAGE_BOX_INBOX} AND ${Telephony.Mms.READ}=0",
        )
        return counts
    }

    private fun recipientIds(threadId: Long): String {
        val uri = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        return context.contentResolver.query(uri, arrayOf("recipient_ids"), "_id=?", arrayOf(threadId.toString()), null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use ""
                val index = cursor.getColumnIndex("recipient_ids")
                if (index >= 0) cursor.getString(index).orEmpty() else ""
            }
            .orEmpty()
    }

    private fun resolveRecipients(ids: String): List<String> {
        if (ids.isBlank()) return emptyList()
        return ids.split(' ', ',').mapNotNull { raw ->
            val id = raw.trim()
            if (id.isEmpty()) return@mapNotNull null
            val address = synchronized(canonical) {
                canonical.getOrPut(id) {
                    context.contentResolver.query(
                        Uri.parse("content://mms-sms/canonical-addresses"),
                        arrayOf("address"),
                        "_id=?",
                        arrayOf(id),
                        null,
                    )?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use ""
                        val index = cursor.getColumnIndex("address")
                        if (index >= 0) cursor.getString(index).orEmpty() else ""
                    }.orEmpty()
                }
            }
            address.ifBlank { null }
        }
    }

    private fun fallbackAddresses(threadId: Long): List<String> {
        val sms = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }.orEmpty()
        if (sms.isNotBlank()) return listOf(sms)
        return emptyList()
    }

    private fun names(): Map<String, NameEntry> {
        val now = System.currentTimeMillis()
        if (now - namesAt < 30_000) return namesCache
        if (!hasContacts()) {
            namesCache = emptyMap()
            namesAt = now
            return namesCache
        }
        val region = PhoneNumbers.defaultRegion(context)
        val map = HashMap<String, NameEntry>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val name = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val number = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photo = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(name).orEmpty()
                val raw = cursor.getString(number).orEmpty()
                val entry = NameEntry(displayName.ifBlank { raw }, if (photo >= 0) cursor.getString(photo) else null)
                val norm = PhoneNumbers.normalize(raw, region)
                if (norm.isNotBlank()) map.putIfAbsent(norm, entry)
                val digits = PhoneNumbers.digitsKeepPlus(raw)
                if (digits.isNotBlank()) map.putIfAbsent(digits, entry)
            }
        }
        namesCache = map
        namesAt = now
        return map
    }

    private fun lookup(map: Map<String, NameEntry>, address: String, region: String): NameEntry? {
        if (address.isBlank()) return null
        val norm = PhoneNumbers.normalize(address, region)
        return map[norm] ?: map[PhoneNumbers.digitsKeepPlus(address)]
    }

    private fun display(map: Map<String, NameEntry>, address: String, region: String): String {
        if (address.isBlank()) return "Unknown"
        lookup(map, address, region)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (address.contains('@')) return address
        return PhoneNumbers.display(address, region).ifBlank { address }
    }

    private fun smsStatus(type: Int, status: Int): String = when (type) {
        Telephony.Sms.MESSAGE_TYPE_FAILED -> Delivery.FAILED
        Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> Delivery.SENDING
        Telephony.Sms.MESSAGE_TYPE_SENT -> when (status) {
            Telephony.Sms.STATUS_COMPLETE -> Delivery.DELIVERED
            Telephony.Sms.STATUS_FAILED -> Delivery.NOT_DELIVERED
            else -> Delivery.SENT
        }
        else -> Delivery.RECEIVED
    }

    private fun mmsStatus(box: Int): String = when (box) {
        Telephony.Mms.MESSAGE_BOX_FAILED -> Delivery.FAILED
        Telephony.Mms.MESSAGE_BOX_OUTBOX -> Delivery.SENDING
        Telephony.Mms.MESSAGE_BOX_SENT -> Delivery.SENT
        else -> Delivery.RECEIVED
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private data class NameEntry(val name: String, val photo: String?)

    companion object {
        private const val TAG = "Messages"

        private fun conversationUri(threadId: Long) = Uri.parse("content://mms-sms/conversations/$threadId")

        private fun mmsMillis(raw: Long): Long = if (raw > 10_000_000_000L) raw else raw * 1000

        private fun key(address: String, timestamp: Long, body: String) = "$address|$timestamp|$body"

        private fun jsonString(value: String): String = buildString {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
            append('"')
        }
    }
}

@Serializable
data class BackupFile(
    val version: Int = 1,
    val messages: List<BackupMessage> = emptyList(),
)

@Serializable
data class BackupMessage(
    val address: String = "",
    val body: String = "",
    val timestamp: Long = 0,
    val incoming: Boolean = true,
    val type: String = "sms",
)
