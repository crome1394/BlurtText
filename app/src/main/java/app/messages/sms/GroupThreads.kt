package app.messages.sms

/**
 * Who belongs in a group thread.
 *
 * A one-to-one picture message lists this phone as the only recipient. Those
 * stay a one-person thread. A group lists the other people as well, and the
 * thread is that set with this phone removed so replies go to all of them
 * and their later messages land in the same thread.
 */
data class GroupPlacement(
    val participants: List<String>,
)

fun cleanMmsAddress(raw: String): String =
    raw.substringBefore("/").trim().trim('<', '>').trim()

fun addressKey(raw: String): String {
    val cleaned = cleanMmsAddress(raw)
    if (cleaned.isEmpty() || cleaned.equals("insert-address-token", true)) return ""
    if ('@' in cleaned) return cleaned.lowercase()
    val digits = cleaned.filter { it.isDigit() }
    if (digits.isEmpty()) return cleaned.lowercase()
    return if (digits.length >= 10) digits.takeLast(10) else digits
}

fun sameAddress(left: String, right: String): Boolean {
    val a = addressKey(left)
    val b = addressKey(right)
    return a.isNotEmpty() && a == b
}

fun placeGroup(
    from: String,
    to: List<String>,
    cc: List<String>,
    ownNumbers: List<String>,
): GroupPlacement {
    val ownKeys = ownNumbers.map { addressKey(it) }.filter { it.isNotEmpty() }.toSet()
    fun owned(address: String): Boolean = addressKey(address) in ownKeys
    val people = ArrayList<String>()
    fun add(address: String) {
        val key = addressKey(address)
        if (key.isEmpty() || owned(address)) return
        if (people.any { addressKey(it) == key }) return
        people += cleanMmsAddress(address)
    }
    add(from)
    val copies = (to + cc).filter { addressKey(it).isNotEmpty() && !owned(it) }
    // One extra address and no known own number is the usual one-to-one
    // message, where that address is this phone. Two or more are a group.
    val includeCopies = ownKeys.isNotEmpty() || copies.size >= 2
    if (includeCopies) copies.forEach(::add)
    return GroupPlacement(people)
}

/**
 * Inbox line under the name. A thread that once contained a picture still has
 * the provider attachment flag set after a later text, so the flag must not
 * replace the newest message.
 */
fun inboxSnippet(
    providerSnippet: String,
    threadHasAttachment: Boolean,
    latestBody: String,
    latestMedia: String,
): String {
    if (threadHasAttachment || providerSnippet.isBlank()) {
        val body = latestBody.trim()
        if (body.isNotBlank()) return reactionInboxLine(body) ?: body
        val media = latestMedia.trim()
        if (media.isNotBlank()) return media
    }
    val provider = providerSnippet.trim()
    return reactionInboxLine(provider) ?: provider
}
