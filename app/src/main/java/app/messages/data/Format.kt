package app.messages.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun formatThreadTitle(names: List<String>): String {
    val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
    if (clean.isEmpty()) return "Unknown"
    if (clean.size <= 2) return clean.joinToString(", ")
    return clean.first() + " +" + (clean.size - 1)
}

fun initials(title: String): String {
    val words = title.split(" ", ",", "+").map { it.trim() }.filter { it.any { ch -> ch.isLetter() } }
    if (words.isEmpty()) return "#"
    if (words.size == 1) return words[0].take(2).uppercase()
    return (words[0].take(1) + words[1].take(1)).uppercase()
}

/** Label inside the round unread counter. Empty when the thread is caught up. */
fun unreadBadge(count: Int): String = when {
    count <= 0 -> ""
    count > 99 -> "99+"
    else -> count.toString()
}

fun formatTimestamp(
    millis: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (millis <= 0L) return ""
    val zdt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val date = zdt.toLocalDate()
    val time = DateTimeFormatter.ofPattern("h:mm a")
    return when {
        date == today -> zdt.format(time)
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> zdt.format(DateTimeFormatter.ofPattern("MMM d"))
        else -> zdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

fun preferredSub(defaultSubId: Int, systemSubId: Int): Int =
    if (defaultSubId >= 0) defaultSubId else systemSubId

fun chosenSub(sims: List<SimLine>, preferred: Int): Int? {
    if (sims.isEmpty()) return null
    if (preferred >= 0) sims.firstOrNull { it.id == preferred }?.let { return it.id }
    return sims.first().id
}

fun simCaption(subId: Int, sims: List<SimLine>): String {
    if (subId < 0) return ""
    val sim = sims.firstOrNull { it.id == subId } ?: return "SIM"
    return sim.shortName
}

fun simDetail(subId: Int, sims: List<SimLine>): String {
    if (subId < 0) return ""
    val sim = sims.firstOrNull { it.id == subId } ?: return "SIM"
    val carrier = sim.carrier.trim()
    val digits = carrier.count { it.isDigit() }
    if (carrier.isBlank() || digits >= 6 || carrier.equals(sim.shortName, ignoreCase = true)) return sim.shortName
    return "${sim.shortName} $carrier"
}

fun messageParty(incoming: Boolean, senderName: String, address: String): String {
    if (!incoming) return "You"
    val name = senderName.trim()
    val number = address.trim()
    return when {
        name.isNotBlank() && name != number -> name
        number.isNotBlank() -> number
        else -> name
    }
}

fun messageCaption(
    incoming: Boolean,
    party: String,
    time: String,
    mms: Boolean,
    sim: String,
    showDirection: Boolean,
    showParty: Boolean,
    showTime: Boolean,
    showType: Boolean,
    showSim: Boolean,
): String {
    val parts = ArrayList<String>(5)
    if (showDirection) parts += if (incoming) "Received" else "Sent"
    if (showParty && party.isNotBlank()) parts += party
    if (showTime && time.isNotBlank()) parts += time
    if (showType) parts += if (mms) "MMS" else "SMS"
    if (showSim && sim.isNotBlank()) parts += sim
    return parts.joinToString(" · ")
}

/** A brand-new message has no recipients, so it must not reuse the empty-address draft. */
fun composeStartText(initialBody: String, initialAddresses: List<String>, savedDraft: String): String {
    if (initialBody.isNotBlank()) return initialBody
    if (initialAddresses.isEmpty()) return ""
    return savedDraft
}

fun formatMessageStamp(
    millis: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (millis <= 0L) return ""
    val zdt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val date = zdt.toLocalDate()
    val time = DateTimeFormatter.ofPattern("h:mm:ss a")
    return when {
        date == today -> zdt.format(time)
        date == today.minusDays(1) -> "Yesterday " + zdt.format(time)
        date.year == today.year -> zdt.format(DateTimeFormatter.ofPattern("MMM d, h:mm:ss a"))
        else -> zdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a"))
    }
}

/** Day and clock the schedule picker opens on. The date is UTC midnight, which is how Material stores a calendar day. */
data class SchedulePickerSeed(val dateUtcMillis: Long, val hour: Int, val minute: Int)

fun schedulePickerSeed(
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): SchedulePickerSeed {
    val zdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val dateUtcMillis = zdt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return SchedulePickerSeed(dateUtcMillis, zdt.hour, zdt.minute)
}

fun formatFullStamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (millis <= 0L) return ""
    val zdt = Instant.ofEpochMilli(millis).atZone(zone)
    return zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm:ss a"))
}
