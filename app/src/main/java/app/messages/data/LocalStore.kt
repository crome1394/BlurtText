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
)

@Serializable
data class Persisted(
    val version: Int = 1,
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    val enterSends: Boolean = false,
    val deliveryReports: Boolean = false,
    val notificationPreview: Boolean = true,
    val fontScale: Float = 1f,
    val palette: String = "fern",
    val launcherIcon: String = "original",
    val searchAtBottom: Boolean = false,
    val stripedInbox: Boolean = false,
    val swipeStart: String = "unread",
    val swipeEnd: String = "delete",
    val swipeHintDismissed: Boolean = false,
    val blockedKeywords: List<String> = emptyList(),
    val lastSubId: Int = -1,
    val defaultSubId: Int = -1,
    val showSimIndicator: Boolean = true,
    val detailDirection: Boolean = true,
    val detailParty: Boolean = true,
    val detailTime: Boolean = true,
    val detailType: Boolean = true,
    val detailSim: Boolean = true,
    val pins: List<Long> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
    val attachments: Map<String, String> = emptyMap(),
    val scheduled: List<ScheduledMessage> = emptyList(),
    val gifApiKey: String = "",
    val ribbon: List<RibbonItem> = emptyList(),
    val ribbonTopAt: Int = 2,
    val footerHeight: Int = 64,
    val footer: List<RibbonItem> = emptyList(),
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

val footerActionIds = listOf("search", "settings", "computer", "filter")

fun footerLabel(id: String): String = when (id) {
    "search" -> "Search"
    "settings" -> "Settings"
    "computer" -> "LanMessaging"
    "filter" -> "Filter"
    else -> id
}

fun resolvedFooter(saved: List<RibbonItem>): List<RibbonItem> {
    val known = saved.filter { it.id in footerActionIds }.distinctBy { it.id }
    val missing = footerActionIds.filter { id -> known.none { it.id == id } }.map { RibbonItem(it) }
    return known + missing
}

fun footerHeightDp(raw: Int): Int = raw.coerceIn(48, 96)

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
