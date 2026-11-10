package app.messages.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.messagesStore by preferencesDataStore(name = "messages-local")

@Serializable
data class ScheduledMessage(
    val id: String,
    val addresses: List<String>,
    val body: String,
    val sendAt: Long,
    val subscriptionId: Int? = null,
    val failed: Boolean = false,
)

@Serializable
data class TrashedThread(
    val threadId: Long,
    val trashedAt: Long,
)

@Serializable
data class Persisted(
    val version: Int = 1,
    val theme: String = "system",
    val dynamicColor: Boolean = false,
    val enterSends: Boolean = false,
    val deliveryReports: Boolean = false,
    val notificationPreview: Boolean = true,
    val fontScale: Float = 1f,
    val palette: String = "ocean",
    val launcherIcon: String = "cyan",
    val searchAtBottom: Boolean = true,
    val stripedInbox: Boolean = true,
    val swipeStart: String = "delete",
    val swipeEnd: String = "unread",
    val swipeHintDismissed: Boolean = false,
    val blockedKeywords: List<String> = emptyList(),
    val lastSubId: Int = -1,
    val defaultSubId: Int = -1,
    val showSimIndicator: Boolean = false,
    val detailDirection: Boolean = false,
    val detailParty: Boolean = true,
    val detailTime: Boolean = true,
    val detailType: Boolean = true,
    val detailSim: Boolean = false,
    val pins: List<Long> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
    val attachments: Map<String, String> = emptyMap(),
    val scheduled: List<ScheduledMessage> = emptyList(),
    val gifApiKey: String = "",
    val ribbon: List<RibbonItem> = listOf(
        RibbonItem("read"),
        RibbonItem("unread"),
        RibbonItem("delete"),
        RibbonItem("block"),
        RibbonItem("archive", shown = false),
        RibbonItem("pin"),
    ),
    val ribbonTopAt: Int = 2,
    val footerHeight: Int = 68,
    val footer: List<RibbonItem> = listOf(
        RibbonItem("filter"),
        RibbonItem("search"),
        RibbonItem("settings"),
        RibbonItem("computer"),
    ),
    val trash: List<TrashedThread> = emptyList(),
    val autoTrashEnabled: Boolean = false,
    val autoTrashDays: Int = 30,
    val autoDeleteTrashEnabled: Boolean = false,
    val autoDeleteTrashDays: Int = 30,
    val inboxGroup: String = "none",
)

@Serializable
data class RibbonItem(
    val id: String,
    val shown: Boolean = true,
)

class LocalStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("state")

    @Volatile
    var snapshot: Persisted = Persisted()
        private set

    val settings: Flow<Persisted> = context.messagesStore.data.map { prefs ->
        decode(prefs[key]).also { snapshot = it }
    }

    suspend fun current(): Persisted {
        val value = settings.first()
        snapshot = value
        return value
    }

    suspend fun update(block: (Persisted) -> Persisted) {
        context.messagesStore.edit { prefs ->
            val next = block(decode(prefs[key]))
            prefs[key] = json.encodeToString(Persisted.serializer(), next)
            snapshot = next
        }
    }

    private fun decode(raw: String?): Persisted {
        if (raw.isNullOrBlank()) return Persisted()
        return try {
            json.decodeFromString(Persisted.serializer(), raw)
        } catch (_: Exception) {
            Persisted()
        }
    }
}

fun draftKey(threadId: Long): String = "t:$threadId"

fun draftKey(addresses: List<String>): String =
    "a:" + addresses.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")

fun swipeAction(raw: String, fallback: String): String = when (raw) {
    "delete", "unread", "none" -> raw
    else -> fallback
}

val ribbonActionIds = listOf("read", "unread", "delete", "block", "archive", "pin")

fun ribbonLabel(id: String, unpin: Boolean = false): String = when (id) {
    "read" -> "Read"
    "unread" -> "Unread"
    "delete" -> "Delete"
    "block" -> "Block"
    "archive" -> "Archive"
    "pin" -> if (unpin) "Unpin" else "Pin"
    else -> id
}

/** Saved order wins. Actions added later are appended, and unknown ids are dropped. */
fun resolvedRibbon(saved: List<RibbonItem>): List<RibbonItem> {
    val known = saved.filter { it.id in ribbonActionIds }.distinctBy { it.id }
    val missing = ribbonActionIds.filter { id -> known.none { it.id == id } }.map { RibbonItem(it) }
    return known + missing
}

fun moveRibbonItem(items: List<RibbonItem>, index: Int, delta: Int): List<RibbonItem> {
    val target = index + delta
    if (index !in items.indices || target !in items.indices) return items
    val next = items.toMutableList()
    val item = next.removeAt(index)
    next.add(target, item)
    return next
}

fun ribbonTopCount(raw: Int): Int = raw.coerceIn(1, 5)

val footerActionIds = listOf("search", "settings", "computer", "filter", "trash")

fun footerLabel(id: String): String = when (id) {
    "search" -> "Search"
    "settings" -> "Settings"
    "computer" -> "BlurtText"
    "filter" -> "Filter"
    "trash" -> "Trash"
    else -> id
}

fun resolvedFooter(saved: List<RibbonItem>): List<RibbonItem> {
    val known = saved.filter { it.id in footerActionIds }.distinctBy { it.id }
    val missing = footerActionIds.filter { id -> known.none { it.id == id } }.map { id ->
        RibbonItem(id, shown = id != "trash")
    }
    return known + missing
}

fun coerceTrashDays(raw: Int): Int = raw.coerceIn(1, 3650)

/**
 * Keeps Trash in step with the inbox.
 * A newer message removes that conversation from Trash.
 * When [autoEnabled] is on, conversations older than [days] move to Trash.
 * An empty [conversations] list is left alone so a slow load cannot empty Trash.
 * The open conversation and pinned conversations stay where they are.
 */
fun reconcileTrash(
    conversations: List<Conversation>,
    trash: List<TrashedThread>,
    autoEnabled: Boolean,
    days: Int,
    now: Long,
    openThreadId: Long,
    pinned: Set<Long>,
): List<TrashedThread> {
    if (conversations.isEmpty()) return trash
    val byId = conversations.associateBy { it.threadId }
    val kept = trash.filter { entry ->
        val conversation = byId[entry.threadId] ?: return@filter true
        conversation.timestamp <= entry.trashedAt
    }
    if (!autoEnabled) return kept
    val cutoff = now - coerceTrashDays(days).toLong() * 86_400_000L
    val already = kept.map { it.threadId }.toSet()
    val add = conversations.mapNotNull { conversation ->
        if (conversation.threadId in already) return@mapNotNull null
        if (conversation.threadId == openThreadId) return@mapNotNull null
        if (conversation.threadId in pinned) return@mapNotNull null
        if (conversation.timestamp <= 0L || conversation.timestamp >= cutoff) return@mapNotNull null
        TrashedThread(conversation.threadId, now)
    }
    return kept + add
}

/**
 * Threads that have been in Trash for at least [days].
 * Nothing is selected while [enabled] is off, and the open thread is left in Trash.
 */
fun trashDueForDeletion(
    trash: List<TrashedThread>,
    enabled: Boolean,
    days: Int,
    now: Long,
    openThreadId: Long = -1L,
): Set<Long> {
    if (!enabled || trash.isEmpty()) return emptySet()
    val cutoff = now - coerceTrashDays(days).toLong() * 86_400_000L
    return trash.mapNotNull { entry ->
        if (entry.threadId == openThreadId) return@mapNotNull null
        if (entry.trashedAt > cutoff) return@mapNotNull null
        entry.threadId
    }.toSet()
}

fun footerHeightDp(raw: Int): Int = raw.coerceIn(48, 96)

/** Icon button size for a footer of [height] dp. Taller footers get larger icons. */
fun footerButtonDp(height: Int): Int = (footerHeightDp(height) * 0.78f).toInt().coerceIn(40, 76)

/** Glyph size inside [footerButtonDp]. */
fun footerGlyphDp(height: Int): Int = (footerButtonDp(height) * 0.52f).toInt().coerceIn(22, 40)

/** A horizontal swipe commits once it has crossed about a quarter of the row. */
fun swipeCommitPx(widthPx: Float): Float = widthPx * 0.25f

/** A read thread offers unread. An unread thread offers read. */
fun swipeReadUnreadLabel(unread: Boolean): String =
    if (unread) "Mark as read" else "Mark as unread"

/** A sideways move waits this long before the row reveals Delete or Read, so a scroll does not flash it. */
const val swipeArmMs = 100L

/**
 * True once the finger has moved sideways past the slop and has been down for [armMs].
 * A mostly vertical move stays false so the list can scroll.
 */
fun swipeRevealReady(
    elapsedMs: Long,
    travelX: Float,
    travelY: Float,
    slop: Float,
    armMs: Long = swipeArmMs,
): Boolean {
    val pastSlop = kotlin.math.abs(travelX) > slop * 3f || kotlin.math.abs(travelY) > slop * 3f
    if (!pastSlop) return false
    if (kotlin.math.abs(travelX) <= kotlin.math.abs(travelY) * 2f) return false
    return elapsedMs >= armMs
}

fun addressesMatchBlocked(addresses: List<String>, blocked: List<String>): Boolean {
    val keys = blocked.map { raw -> raw.filter { it.isDigit() } }.filter { it.isNotEmpty() }.toSet()
    if (keys.isEmpty()) return false
    return addresses.any { raw -> raw.filter { it.isDigit() } in keys }
}

fun sameRecipients(left: List<String>, right: List<String>): Boolean {
    fun key(values: List<String>) = values.map { raw -> raw.filter { it.isDigit() } }
        .filter { it.isNotEmpty() }
        .sorted()
    return key(left) == key(right)
}
