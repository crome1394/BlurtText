package app.messages.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.messages.MessagesApp
import app.messages.data.ChatMessage
import app.messages.data.ContactHit
import app.messages.data.InboxListFilter
import app.messages.data.Conversation
import app.messages.data.Persisted
import app.messages.data.ScheduledMessage
import app.messages.data.SearchHit
import app.messages.data.SimLine
import app.messages.data.TrashedConversation
import app.messages.data.TrashedThread
import app.messages.data.draftKey
import app.messages.data.keywordsFrom
import app.messages.data.reconcileTrash
import app.messages.data.trashDueForDeletion
import app.messages.sms.ActiveThread
import app.messages.sms.MessageNotifier
import app.messages.sms.ScheduleController
import app.messages.sms.SendIntents
import app.lantext.notify.Notifications
import app.lantext.sms.GifSearch
import app.messages.sms.KlipyGifs
import app.messages.sms.SendPipeline
import app.messages.sms.SmsRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.util.ArrayDeque
import java.util.UUID

internal class BackupExport(val file: File, val generation: Int)

sealed interface Screen {
    data object Inbox : Screen
    data class Thread(val threadId: Long) : Screen
    data class ComposeNew(
        val addresses: List<String> = emptyList(),
        val body: String = "",
        val attachmentPath: String? = null,
    ) : Screen
    data object Settings : Screen
    data object Archive : Screen
    data object Blocked : Screen
    data object Scheduled : Screen
    data object Trash : Screen
}

class MessagesViewModel(app: Application) : AndroidViewModel(app) {
    private val backupExportGeneration = AtomicInteger()
    private val messages = MessagesApp.from(app)
    private val repo = messages.messaging
    private val local = messages.local
    private val stack = ArrayDeque<Screen>().apply { addLast(Screen.Inbox) }

    val snackbar = SnackbarHostState()
    val screen = MutableStateFlow<Screen>(Screen.Inbox)
    /** Screen sitting under a settings or new-message card. Matches [screen] when both are closed. */
    val screenUnder = MutableStateFlow<Screen>(Screen.Inbox)
    /** New-message card. Stays set while settings is opened on top of it. */
    val composeOverlay = MutableStateFlow<Screen.ComposeNew?>(null)
    var pendingSettingsTab: Int = 0
        private set
    private var settingsTabToken = 0
    private var consumedSettingsTabToken = -1
    val settingsTabPulse = MutableStateFlow(0)
    val canPop = MutableStateFlow(false)
    val query = MutableStateFlow("")
    val recipientQuery = MutableStateFlow("")
    val selection = MutableStateFlow<Set<Long>>(emptySet())
    val isDefault = MutableStateFlow(SmsRole.isDefault(app))
    val contactsGranted = MutableStateFlow(repo.hasContacts())
    val sims = MutableStateFlow<List<SimLine>>(emptyList())
    val blocked = MutableStateFlow<List<String>>(emptyList())
    val listFilter = MutableStateFlow(InboxListFilter.All)
    val filterThreadId = MutableStateFlow(-1L)
    val exactAlarms = MutableStateFlow(ScheduleController.canExact(app))
    val sending = MutableStateFlow(false)
    val settings = local.settings.stateIn(viewModelScope, SharingStarted.Eagerly, local.snapshot)
    val loadError = repo.error

    private val hidden = MutableStateFlow<Set<Long>>(emptySet())

    val inbox: StateFlow<List<Conversation>> = combine(repo.conversations, hidden, settings) { all, hide, state ->
        val trashed = state.trash.map { it.threadId }.toSet()
        all.filter { !it.archived && it.threadId !in hide && it.threadId !in trashed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived: StateFlow<List<Conversation>> = combine(repo.conversations, settings) { list, state ->
        val trashed = state.trash.map { it.threadId }.toSet()
        list.filter { it.archived && it.threadId !in trashed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashed: StateFlow<List<TrashedConversation>> = combine(repo.conversations, settings) { list, state ->
        val byId = list.associateBy { it.threadId }
        state.trash.mapNotNull { entry ->
            byId[entry.threadId]?.let { TrashedConversation(it, entry.trashedAt) }
        }.sortedByDescending { it.trashedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<List<SearchHit>> = combine(query.debounce(150), settings) { raw, state ->
        raw to state.trash.map { it.threadId }.toSet()
    }.map { (raw, trashedIds) ->
        val q = raw.trim()
        if (q.length < 2) emptyList() else withContext(Dispatchers.IO) {
            repo.search(q).filter { it.threadId !in trashedIds }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<ContactHit>> = recipientQuery.debounce(120).map { raw ->
        if (raw.isBlank()) emptyList() else withContext(Dispatchers.IO) { repo.suggest(raw) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(repo.conversations, settings) { _, _ -> Unit }.collect {
                sweepTrash()
                purgeExpiredTrash()
            }
        }
    }

    fun systemSmsSub(): Int = repo.defaultSmsSubscription()

    fun onResume() {
        val context = getApplication<Application>()
        isDefault.value = SmsRole.isDefault(context)
        contactsGranted.value = repo.hasContacts()
        sims.value = repo.simLines()
        exactAlarms.value = ScheduleController.canExact(context)
        refreshBlocked()
        repo.ping()
    }

    fun pop() {
        if (stack.size <= 1) return
        stack.removeLast()
        publish()
        selection.value = emptySet()
        if (stack.last() !is Screen.Thread) ActiveThread.id = -1L
    }

    fun setListFilter(filter: InboxListFilter) {
        listFilter.value = filter
        if (filter != InboxListFilter.Contact) filterThreadId.value = -1L
    }

    fun filterContact(threadId: Long) {
        filterThreadId.value = threadId
        listFilter.value = InboxListFilter.Contact
    }

    fun showInbox() {
        stack.clear()
        stack.add(Screen.Inbox)
        publish()
        selection.value = emptySet()
        ActiveThread.id = -1L
    }

    fun showThread(threadId: Long) {
        stack.clear()
        stack.add(Screen.Inbox)
        stack.add(Screen.Thread(threadId))
        publish()
        selection.value = emptySet()
    }

    fun showCompose(addresses: List<String> = emptyList(), body: String = "", attachmentPath: String? = null) {
        push(Screen.ComposeNew(addresses, body, attachmentPath))
    }

    fun showSettings(tab: Int = 0) {
        pendingSettingsTab = tab
        settingsTabToken += 1
        settingsTabPulse.value = settingsTabToken
        if (stack.last() !is Screen.Settings) push(Screen.Settings)
    }

    fun closeSettings() {
        if (stack.lastOrNull() is Screen.Settings) pop()
    }

    fun takeSettingsTab(): Int? {
        if (consumedSettingsTabToken == settingsTabToken) return null
        consumedSettingsTabToken = settingsTabToken
        return pendingSettingsTab
    }
    fun showArchive() = push(Screen.Archive)

    fun showTrash() = push(Screen.Trash)

    fun openTrashedThread(threadId: Long) = push(Screen.Thread(threadId))

    fun restoreTrash(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            local.update { state -> state.copy(trash = state.trash.filter { it.threadId !in ids }) }
            hidden.value = hidden.value - ids
            snackbar.showSnackbar(if (ids.size == 1) "Restored" else "${ids.size} conversations restored")
        }
    }

    fun deleteFromTrash(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.deleteThreads(ids) }
                local.update { state -> state.copy(trash = state.trash.filter { it.threadId !in ids }) }
                hidden.value = hidden.value - ids
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not delete")
            }
        }
    }
    fun showBlocked() {
        refreshBlocked()
        push(Screen.Blocked)
    }
    fun showScheduled() = push(Screen.Scheduled)

    fun openFromIntent(intent: Intent) {
        if (intent.getBooleanExtra(Notifications.EXTRA_OPEN_LAN_MESSAGING, false)) {
            showSettings(SETTINGS_TAB_LAN_MESSAGING)
            return
        }
        val thread = intent.getLongExtra(MessageNotifier.EXTRA_THREAD, -1L)
        if (thread > 0L) {
            showThread(thread)
            return
        }
        val outbound = SendIntents.parse(intent) ?: return
        viewModelScope.launch {
            val packageName = getApplication<Application>().packageName
            val shared = outbound.attachment?.takeIf {
                SendIntents.shareAttachmentAllowed(it.scheme, it.authority, packageName)
            }
            val path = shared?.let { uri ->
                withContext(Dispatchers.IO) { runCatching { repo.copyAttachment(uri) }.getOrNull() }
            }
            if (path == null && outbound.addresses.isEmpty() && outbound.body.isBlank()) return@launch
            showCompose(outbound.addresses, outbound.body, path)
        }
    }

    fun conversation(threadId: Long): Conversation? =
        repo.conversations.value.firstOrNull { it.threadId == threadId }

    fun messages(threadId: Long): Flow<List<ChatMessage>> = repo.observeMessages(threadId)

    fun addresses(threadId: Long): List<String> =
        conversation(threadId)?.addresses?.takeIf { it.isNotEmpty() } ?: repo.addressesFor(threadId)

    private val composerText = HashMap<String, String>()
    private val composerFile = HashMap<String, String?>()
    private val composerTouched = HashSet<String>()

    fun draft(key: String): String =
        if (key in composerTouched) composerText[key].orEmpty() else local.snapshot.drafts[key].orEmpty()

    fun attachment(key: String): String? {
        val path = if (key in composerTouched) composerFile[key] else local.snapshot.attachments[key]
        return path?.takeIf { File(it).isFile }
    }

    fun contactCard(uri: Uri): String? = repo.contactCard(uri)

    fun releaseAttachment(path: String?) {
        if (path.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) { repo.releaseAttachment(path) }
    }

    /** Keeps the unsent text and photo for this thread, including after leaving the screen. */
    fun saveComposer(key: String, text: String, attachment: String?) {
        val kept = attachment?.takeIf { File(it).isFile }
        composerTouched += key
        composerText[key] = text
        composerFile[key] = kept
        viewModelScope.launch {
            local.update { state ->
                val drafts = state.drafts.toMutableMap()
                val files = state.attachments.toMutableMap()
                if (text.isBlank()) drafts.remove(key) else drafts[key] = text
                if (kept == null) files.remove(key) else files[key] = kept
                state.copy(drafts = drafts, attachments = files)
            }
            repo.ping()
        }
    }

    fun setActiveThread(threadId: Long) {
        ActiveThread.id = threadId
        if (threadId > 0) markRead(setOf(threadId), quiet = true)
    }

    fun clearActiveThread() {
        ActiveThread.id = -1L
        sweepTrash()
    }

    private fun sweepTrash() {
        val state = local.snapshot
        val conversations = repo.conversations.value
        val next = reconcileTrash(
            conversations = conversations,
            trash = state.trash,
            autoEnabled = state.autoTrashEnabled,
            days = state.autoTrashDays,
            now = System.currentTimeMillis(),
            openThreadId = ActiveThread.id,
            pinned = state.pins.toSet(),
        )
        if (next == state.trash) return
        viewModelScope.launch {
            local.update { current ->
                val resolved = reconcileTrash(
                    conversations = repo.conversations.value,
                    trash = current.trash,
                    autoEnabled = current.autoTrashEnabled,
                    days = current.autoTrashDays,
                    now = System.currentTimeMillis(),
                    openThreadId = ActiveThread.id,
                    pinned = current.pins.toSet(),
                )
                if (resolved == current.trash) current else current.copy(trash = resolved)
            }
        }
    }

    private var purgingTrash = false

    private fun purgeExpiredTrash() {
        if (purgingTrash) return
        val state = local.snapshot
        val due = trashDueForDeletion(
            trash = state.trash,
            enabled = state.autoDeleteTrashEnabled,
            days = state.autoDeleteTrashDays,
            now = System.currentTimeMillis(),
            openThreadId = ActiveThread.id,
        )
        if (due.isEmpty()) return
        purgingTrash = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.deleteThreads(due) }
                local.update { current ->
                    val still = trashDueForDeletion(
                        trash = current.trash,
                        enabled = current.autoDeleteTrashEnabled,
                        days = current.autoDeleteTrashDays,
                        now = System.currentTimeMillis(),
                        openThreadId = ActiveThread.id,
                    )
                    val drop = due.intersect(still)
                    if (drop.isEmpty()) current else current.copy(trash = current.trash.filter { it.threadId !in drop })
                }
                hidden.value = hidden.value - due
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not delete old trash")
            } finally {
                purgingTrash = false
            }
        }
    }

    fun toggleSelect(id: Long) {
        selection.value = if (id in selection.value) selection.value - id else selection.value + id
    }

    fun startSelect(id: Long) {
        selection.value = selection.value + id
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun swipeUnread(id: Long) = markUnread(setOf(id))

    fun swipeDelete(id: Long) = deleteConversations(setOf(id))

    fun deleteSelection() = deleteConversations(selection.value)

    fun markRead(ids: Set<Long>, quiet: Boolean = false) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (id in ids) repo.markRead(id)
            }
            selection.value = emptySet()
            if (!quiet) snackbar.showSnackbar("Marked as read")
        }
    }

    fun markUnread(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) { ids.count { repo.markUnread(it) } }
            selection.value = emptySet()
            if (changed == 0) snackbar.showSnackbar("No incoming message to mark unread")
        }
    }

    fun archive(ids: Set<Long>, archived: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.setArchived(ids, archived) }
                selection.value = emptySet()
                if (archived) {
                    val result = snackbar.showSnackbar("Archived", "Undo", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) {
                        withContext(Dispatchers.IO) { repo.setArchived(ids, false) }
                    }
                }
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not update the conversation")
            }
        }
    }

    fun togglePin(id: Long) {
        viewModelScope.launch {
            local.update { state ->
                val pins = state.pins.toMutableSet()
                if (!pins.add(id)) pins.remove(id)
                state.copy(pins = pins.toList())
            }
            repo.ping()
        }
    }

    fun pinSelection(ids: Set<Long>, pinned: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            local.update { state ->
                val pins = state.pins.toMutableSet()
                if (pinned) pins.addAll(ids) else pins.removeAll(ids)
                state.copy(pins = pins.toList())
            }
            selection.value = emptySet()
            repo.ping()
            snackbar.showSnackbar(if (pinned) "Pinned" else "Unpinned")
        }
    }

    fun blockSelection() {
        val numbers = selection.value.flatMap { id -> conversation(id)?.addresses.orEmpty() }.distinct()
        val threads = selection.value
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.block(numbers)
                    repo.setArchived(threads, true)
                    threads.forEach { repo.markRead(it) }
                }
                selection.value = emptySet()
                refreshBlocked()
                snackbar.showSnackbar("Blocked")
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not block that number")
            }
        }
    }

    fun blockNumber(number: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.block(listOf(number)) }
                refreshBlocked()
                snackbar.showSnackbar("Blocked")
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not block that number")
            }
        }
    }

    fun unblock(number: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.unblock(number) }
                refreshBlocked()
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not unblock that number")
            }
        }
    }

    fun note(message: String) {
        viewModelScope.launch { snackbar.showSnackbar(message) }
    }

    fun contactUri(address: String): Uri? = repo.contactLookupUri(address)

    fun addNumberToContact(uri: Uri, number: String) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { repo.addNumberToContact(uri, number) }
            if (added) {
                repo.ping()
                snackbar.showSnackbar("Number added to the contact")
            } else {
                snackbar.showSnackbar("Could not add that number")
            }
        }
    }

    fun send(addresses: List<String>, body: String, attachmentPath: String?, subId: Int?, threadId: Long = -1L) {
        if (sending.value) return
        viewModelScope.launch {
            sending.value = true
            try {
                val sentThread = withContext(Dispatchers.IO) {
                    val known = if (threadId > 0) repo.addressesFor(threadId) else emptyList()
                    val dest = if (known.size > addresses.size) known else addresses
                    repo.send(dest, body, attachmentPath, subId)
                }
                val keys = listOf(draftKey(sentThread), draftKey(addresses))
                keys.forEach { key ->
                    composerTouched += key
                    composerText[key] = ""
                    composerFile[key] = null
                }
                local.update { state ->
                    state.copy(
                        drafts = state.drafts - keys.toSet(),
                        attachments = state.attachments - keys.toSet(),
                    )
                }
                if (attachmentPath != null) withContext(Dispatchers.IO) { repo.releaseAttachment(attachmentPath) }
                showThread(sentThread)
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not send")
            } finally {
                sending.value = false
            }
        }
    }

    /** Sends a tapback and leaves the reply draft alone. */
    fun react(addresses: List<String>, body: String, subId: Int?, threadId: Long) {
        if (body.isBlank() || addresses.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val known = if (threadId > 0) repo.addressesFor(threadId) else emptyList()
                    val dest = if (known.size > addresses.size) known else addresses
                    if (dest.isEmpty()) error("No recipients on this conversation")
                    repo.send(dest, body, null, subId)
                }
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not send")
            }
        }
    }

    fun retry(message: ChatMessage) {
        val address = message.address
        if (address.isBlank() && message.threadId <= 0) return
        viewModelScope.launch {
            sending.value = true
            try {
                withContext(Dispatchers.IO) {
                    val targets = repo.addressesFor(message.threadId).ifEmpty {
                        listOf(address).filter { it.isNotBlank() }
                    }
                    if (targets.isEmpty()) return@withContext
                    repo.send(targets, message.body, null, message.subId.takeIf { it >= 0 })
                    repo.deleteMessage(message)
                }
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not send")
            } finally {
                sending.value = false
            }
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.deleteMessage(message) }
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not delete the message")
            }
        }
    }

    fun schedule(addresses: List<String>, body: String, at: Long, subId: Int?, replaceId: String? = null) {
        if (body.isBlank()) {
            viewModelScope.launch { snackbar.showSnackbar("Write a message first") }
            return
        }
        if (at <= System.currentTimeMillis()) {
            viewModelScope.launch { snackbar.showSnackbar("Pick a time in the future") }
            return
        }
        viewModelScope.launch {
            val item = ScheduledMessage(
                id = replaceId ?: UUID.randomUUID().toString(),
                addresses = addresses,
                body = body.trim(),
                sendAt = at,
                subscriptionId = subId,
            )
            local.update { state ->
                state.copy(scheduled = state.scheduled.filter { it.id != item.id } + item)
            }
            ScheduleController.cancel(getApplication(), item.id)
            ScheduleController.arm(getApplication(), item)
            snackbar.showSnackbar(if (replaceId == null) "Scheduled" else "Schedule updated")
        }
    }

    fun cancelSchedule(id: String) {
        viewModelScope.launch {
            local.update { state -> state.copy(scheduled = state.scheduled.filter { it.id != id }) }
            ScheduleController.cancel(getApplication(), id)
        }
    }

    fun update(block: (Persisted) -> Persisted) {
        viewModelScope.launch {
            local.update(block)
            repo.ping()
        }
    }

    fun setKeywords(text: String) {
        update { it.copy(blockedKeywords = keywordsFrom(text)) }
    }

    fun copyAttachment(uri: Uri): String = repo.copyAttachment(uri)

    /** Copies a picked video when it fits the carrier MMS budget. Call off the main thread. */
    fun takeVideo(uri: Uri): String {
        val budget = SendPipeline.mediaBudget(getApplication()).toLong()
        val known = repo.streamSize(uri)
        if (known > budget) error("That video is too large for this carrier.")
        val path = repo.copyAttachment(uri)
        if (File(path).length() > budget) {
            repo.releaseAttachment(path)
            error("That video is too large for this carrier.")
        }
        return path
    }

    private fun gifCap(): Int = minOf(GifSearch.MAX_BYTES, SendPipeline.mediaBudget(getApplication())).coerceAtLeast(1)

    suspend fun searchGifs(query: String): List<KlipyGifs.Hit> = withContext(Dispatchers.IO) {
        KlipyGifs.search(local.snapshot.gifApiKey, query, gifCap())
    }

    suspend fun attachGif(hit: KlipyGifs.Hit): String = withContext(Dispatchers.IO) {
        val cap = gifCap()
        if (hit.bytes !in 1..cap) error("That GIF is too large to send.")
        val bytes = KlipyGifs.download(hit.sendUrl, cap)
        repo.saveOutgoing(bytes, "image/gif")
    }

    suspend fun loadPart(partId: Long): ByteArray? = withContext(Dispatchers.IO) { repo.loadPart(partId) }

    fun stagePart(partId: Long, mime: String): Uri? = repo.stagePart(partId, mime)

    fun exportThreadText(threadId: Long): String = repo.exportThread(threadId)

    suspend fun exportBackup(): BackupExport = withContext(Dispatchers.IO) {
        val file = backupExportFile()
        val generation = backupExportGeneration.incrementAndGet()
        try {
            repo.exportTo(file)
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        BackupExport(file, generation)
    }

    fun releaseBackupExport(generation: Int, delayMs: Long = 20_000) {
        viewModelScope.launch(Dispatchers.IO) {
            // The share target opens the file after the sheet closes.
            if (delayMs > 0) delay(delayMs)
            if (backupExportGeneration.get() != generation) return@launch
            backupExportFile().delete()
        }
    }

    private fun backupExportFile(): File =
        File(getApplication<Application>().cacheDir, "messages-backup.json")

    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not read that file")
        repo.importFrom(text)
    }

    private fun deleteConversations(ids: Set<Long>) {
        if (ids.isEmpty()) return
        hidden.value = hidden.value + ids
        selection.value = emptySet()
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                local.update { state ->
                    val kept = state.trash.filter { it.threadId !in ids }
                    state.copy(trash = kept + ids.map { TrashedThread(it, now) })
                }
                val result = snackbar.showSnackbar(
                    message = if (ids.size == 1) "Moved to Trash" else "${ids.size} conversations moved to Trash",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    local.update { state -> state.copy(trash = state.trash.filter { it.threadId !in ids }) }
                }
            } catch (t: Throwable) {
                snackbar.showSnackbar(t.message ?: "Could not move that conversation to Trash")
            } finally {
                hidden.value = hidden.value - ids
            }
        }
    }

    private fun refreshBlocked() {
        viewModelScope.launch(Dispatchers.IO) {
            blocked.value = try {
                repo.blockedNumbers()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun push(next: Screen) {
        if (stack.lastOrNull() != next) stack.addLast(next)
        publish()
        selection.value = emptySet()
    }

    private fun publish() {
        val top = stack.last()
        screen.value = top
        canPop.value = stack.size > 1
        val cardOnTop = top is Screen.Settings || top is Screen.ComposeNew
        screenUnder.value = if (cardOnTop) screenBelowCards() else top
        composeOverlay.value = stack.lastOrNull { it is Screen.ComposeNew } as? Screen.ComposeNew
    }

    private fun screenBelowCards(): Screen {
        val below = stack.descendingIterator()
        while (below.hasNext()) {
            val item = below.next()
            if (item !is Screen.Settings && item !is Screen.ComposeNew) return item
        }
        return Screen.Inbox
    }
}
