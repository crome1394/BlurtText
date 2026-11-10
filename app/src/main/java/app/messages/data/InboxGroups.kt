package app.messages.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

const val INBOX_GROUP_NONE = "none"
const val INBOX_GROUP_READ = "read"
const val INBOX_GROUP_DATE = "date"
const val INBOX_GROUP_TYPE = "type"
const val INBOX_GROUP_PINNED = "pinned"

fun inboxGroupMode(raw: String): String = when (raw) {
    INBOX_GROUP_READ, INBOX_GROUP_DATE, INBOX_GROUP_TYPE, INBOX_GROUP_PINNED -> raw
    else -> INBOX_GROUP_NONE
}

data class InboxSection(
    val title: String,
    val conversations: List<Conversation>,
)

sealed class InboxListEntry {
    data class Heading(val title: String) : InboxListEntry()
    data class Row(val conversation: Conversation, val stripe: Int) : InboxListEntry()
}

data class TrashedConversation(
    val conversation: Conversation,
    val trashedAt: Long,
)

data class TrashSection(
    val title: String,
    val items: List<TrashedConversation>,
)

private val dateHeadings = listOf("Today", "Yesterday", "This week", "This month", "Older")

fun groupInbox(
    rows: List<Conversation>,
    mode: String,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
): List<InboxSection> {
    if (rows.isEmpty()) return emptyList()
    val normalized = inboxGroupMode(mode)
    if (normalized == INBOX_GROUP_NONE) return listOf(InboxSection("", rows))
    val order = when (normalized) {
        INBOX_GROUP_READ -> listOf("Unread", "Read")
        INBOX_GROUP_DATE -> dateHeadings
        INBOX_GROUP_TYPE -> listOf("SMS", "MMS")
        INBOX_GROUP_PINNED -> listOf("Pinned", "Other")
        else -> return listOf(InboxSection("", rows))
    }
    val buckets = LinkedHashMap<String, MutableList<Conversation>>()
    for (title in order) buckets[title] = mutableListOf()
    for (row in rows) {
        val title = when (normalized) {
            INBOX_GROUP_READ -> if (row.unread > 0) "Unread" else "Read"
            INBOX_GROUP_DATE -> receivedBucket(row.timestamp, now, zone, weekFields)
            INBOX_GROUP_TYPE -> if (row.latestMms) "MMS" else "SMS"
            INBOX_GROUP_PINNED -> if (row.pinned) "Pinned" else "Other"
            else -> ""
        }
        buckets.getValue(title).add(row)
    }
    return order.mapNotNull { title ->
        val items = buckets.getValue(title)
        if (items.isEmpty()) null else InboxSection(title, items)
    }
}

/** Flat inbox stays one list. A grouped inbox inserts a heading and keeps row order inside each heading. */
fun inboxEntries(
    rows: List<Conversation>,
    mode: String,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
): List<InboxListEntry> {
    val out = ArrayList<InboxListEntry>()
    var stripe = 0
    for (section in groupInbox(rows, mode, now, zone, weekFields)) {
        if (section.title.isNotEmpty()) out += InboxListEntry.Heading(section.title)
        for (conversation in section.conversations) {
            out += InboxListEntry.Row(conversation, stripe)
            stripe++
        }
    }
    return out
}

fun trashSections(
    entries: List<TrashedConversation>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
): List<TrashSection> {
    if (entries.isEmpty()) return emptyList()
    val buckets = LinkedHashMap<String, MutableList<TrashedConversation>>()
    for (title in dateHeadings) buckets[title] = mutableListOf()
    for (entry in entries.sortedByDescending { it.trashedAt }) {
        buckets.getValue(receivedBucket(entry.trashedAt, now, zone, weekFields)).add(entry)
    }
    return dateHeadings.mapNotNull { title ->
        val items = buckets.getValue(title)
        if (items.isEmpty()) null else TrashSection(title, items)
    }
}

/** Today and Yesterday come out of the week and the month. A future time counts as today. */
fun receivedBucket(
    timestamp: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
): String {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(timestamp.coerceAtLeast(0L)).atZone(zone).toLocalDate()
    if (!day.isBefore(today)) return "Today"
    if (day == today.minusDays(1)) return "Yesterday"
    val weekStart = today.with(weekFields.dayOfWeek(), 1)
    if (!day.isBefore(weekStart)) return "This week"
    val monthStart = today.withDayOfMonth(1)
    if (!day.isBefore(monthStart)) return "This month"
    return "Older"
}
