package app.messages.sms

import app.lantext.sms.SELF_ECHO_WINDOW_MS
import app.messages.data.ChatMessage
import kotlin.math.abs

data class BubbleReaction(val symbol: String, val names: List<String>)

data class TapbackSymbol(
    val symbol: String,
    val verb: String,
    val removal: String,
    val label: String,
)

val tapbackSymbols = listOf(
    TapbackSymbol("👍", "Liked", "Removed a like from", "Like"),
    TapbackSymbol("❤️", "Loved", "Removed a heart from", "Love"),
    TapbackSymbol("👎", "Disliked", "Removed a dislike from", "Dislike"),
    TapbackSymbol("😂", "Laughed at", "Removed a laugh from", "Laugh"),
    TapbackSymbol("‼️", "Emphasized", "Removed an exclamation from", "Emphasize"),
    TapbackSymbol("❓", "Questioned", "Removed a question from", "Question"),
)

/**
 * Text sent back when you hold a message pill and pick a symbol.
 * iPhones show this as a tapback. A second tap on your own symbol removes it.
 */
fun tapbackBody(message: ChatMessage, symbol: TapbackSymbol, remove: Boolean): String? {
    val lead = if (remove) symbol.removal else symbol.verb
    val quote = tapbackQuote(message.body)
    if (quote != null) return "$lead “$quote”"
    val media = when {
        message.attachments.any { it.mime.startsWith("image/") } -> "an image"
        message.attachments.any { it.mime.startsWith("video/") } -> "a video"
        message.attachments.any { it.mime.startsWith("audio/") } -> "an audio message"
        else -> return null
    }
    return "$lead $media"
}

fun tapbackQuote(body: String): String? {
    val trimmed = body.trim().replace("“", "").replace("”", "").replace("\"", "").trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.length <= 80) return trimmed
    return trimmed.take(80).trimEnd() + "…"
}

data class ReactionLayout(
    val hiddenKeys: Set<String>,
    val byKey: Map<String, List<BubbleReaction>>,
)

private enum class MediaHint { IMAGE, VIDEO, AUDIO, ANY }

private data class Tapback(
    val kind: String,
    val symbol: String,
    val removal: Boolean,
    val quote: String,
    val media: MediaHint?,
)

private val quotedTapback = Regex(
    """^(Removed a heart from|Removed a like from|Removed a dislike from|Removed a laugh from|Removed an exclamation from|Removed a question from|Laughed at|Emphasized|Questioned|Disliked|Loved|Liked)\s+[“"](.+)[”"]\s*\.?\s*$""",
    RegexOption.DOT_MATCHES_ALL,
)

private val emojiTapback = Regex(
    """^Reacted (\S+) to [“"](.+)[”"]\s*\.?\s*$""",
    RegexOption.DOT_MATCHES_ALL,
)

private val bareTapback = Regex(
    """^(Removed a heart from|Removed a like from|Removed a dislike from|Removed a laugh from|Removed an exclamation from|Removed a question from|Laughed at|Emphasized|Questioned|Disliked|Loved|Liked)\s+(an image|a photo|a picture|a gif|a video|a movie|an audio message|a voice message|an attachment)\s*\.?\s*$""",
    RegexOption.IGNORE_CASE,
)

/**
 * An iPhone tapback arrives as its own text, such as Liked "…didn't pick it up."
 * [reactionLayout] hangs that reaction on the earlier message and hides the tapback row.
 */
fun reactionLayout(messages: List<ChatMessage>): ReactionLayout {
    val hidden = mutableSetOf<String>()
    val marks = mutableMapOf<String, LinkedHashMap<String, MutableList<String>>>()
    messages.forEachIndexed { index, message ->
        val tap = parseTapback(message.body) ?: return@forEachIndexed
        val target = findTarget(messages, index, tap) ?: return@forEachIndexed
        hidden += message.key
        // An inbox copy of a tapback you just sent would otherwise count as the other person.
        if (isOwnEcho(messages, index)) return@forEachIndexed
        val people = marks.getOrPut(messages[target].key) { linkedMapOf() }
            .getOrPut(tap.symbol) { mutableListOf() }
        val name = if (!message.incoming) "You" else message.senderName.ifBlank { message.address }.ifBlank { "Someone" }
        if (tap.removal) {
            people.removeAll { it == name }
        } else if (name !in people) {
            people += name
        }
    }
    val byKey = marks.mapValues { (_, kinds) ->
        kinds.filterValues { it.isNotEmpty() }.map { (symbol, names) -> BubbleReaction(symbol, names.toList()) }
    }.filterValues { it.isNotEmpty() }
    return ReactionLayout(hidden, byKey)
}

fun reactionDescription(marks: List<BubbleReaction>): String = marks.joinToString(". ") { mark ->
    val who = mark.names.distinct().joinToString(" and ").ifBlank { "Someone" }
    val verb = when (mark.symbol) {
        "❤️" -> "loved"
        "👎" -> "disliked"
        "😂" -> "laughed at"
        "‼️" -> "emphasized"
        "❓" -> "questioned"
        "👍" -> "liked"
        else -> "reacted ${mark.symbol} to"
    }
    "$who $verb this"
}

/** Inbox line for a tapback, so the list does not show the quoted fragment. */
fun reactionInboxLine(body: String): String? {
    val tap = parseTapback(body) ?: return null
    val noun = when (tap.kind) {
        "love" -> "heart"
        "dislike" -> "dislike"
        "laugh" -> "laugh"
        "emphasis" -> "emphasis"
        "question" -> "question"
        else -> "like"
    }
    return if (tap.removal) "Removed a $noun" else "${tap.symbol} ${tap.past} a message"
}

private val Tapback.past: String
    get() = when (kind) {
        "love" -> "Loved"
        "dislike" -> "Disliked"
        "laugh" -> "Laughed at"
        "emphasis" -> "Emphasized"
        "question" -> "Questioned"
        "custom" -> "Reacted to"
        else -> "Liked"
    }

private fun parseTapback(body: String): Tapback? {
    val text = body.trim()
    val quoted = quotedTapback.matchEntire(text)
    if (quoted != null) {
        val verb = quoted.groupValues[1]
        val quote = quoted.groupValues[2].trim()
        val (kind, symbol) = kindOf(verb)
        return Tapback(kind, symbol, verb.startsWith("Removed", ignoreCase = true), quote, mediaHint(quote))
    }
    val emoji = emojiTapback.matchEntire(text)
    if (emoji != null) {
        val symbol = emoji.groupValues[1]
        val quote = emoji.groupValues[2].trim()
        return Tapback("custom", symbol, false, quote, mediaHint(quote))
    }
    val bare = bareTapback.matchEntire(text) ?: return null
    val verb = bare.groupValues[1]
    val phrase = bare.groupValues[2]
    val (kind, symbol) = kindOf(verb)
    return Tapback(kind, symbol, verb.startsWith("Removed", ignoreCase = true), phrase, mediaHint(phrase) ?: MediaHint.ANY)
}

private fun kindOf(verb: String): Pair<String, String> {
    val v = verb.lowercase()
    return when {
        v.contains("heart") || v == "loved" -> "love" to "❤️"
        v.contains("dislike") || v == "disliked" -> "dislike" to "👎"
        v.contains("laugh") || v.startsWith("laughed") -> "laugh" to "😂"
        v.contains("exclamation") || v == "emphasized" -> "emphasis" to "‼️"
        v.contains("question") || v == "questioned" -> "question" to "❓"
        else -> "like" to "👍"
    }
}

private fun mediaHint(phrase: String): MediaHint? {
    val p = phrase.trim().lowercase().removePrefix("…").removePrefix("...").trim()
    return when (p) {
        "an image", "a photo", "a picture", "a gif" -> MediaHint.IMAGE
        "a video", "a movie" -> MediaHint.VIDEO
        "an audio message", "a voice message" -> MediaHint.AUDIO
        "an attachment" -> MediaHint.ANY
        else -> null
    }
}

private fun isOwnEcho(messages: List<ChatMessage>, index: Int): Boolean {
    val message = messages[index]
    if (!message.incoming) return false
    val text = message.body.trim()
    if (text.isEmpty()) return false
    return messages.any { other ->
        other !== message &&
            !other.incoming &&
            other.threadId == message.threadId &&
            other.body.trim() == text &&
            abs(other.timestamp - message.timestamp) <= SELF_ECHO_WINDOW_MS
    }
}

private fun findTarget(messages: List<ChatMessage>, index: Int, tap: Tapback): Int? {
    var found = -1
    for (i in 0 until index) {
        val candidate = messages[i]
        if (parseTapback(candidate.body) != null) continue
        val matches = if (tap.media != null) matchesMedia(candidate, tap.media) else quoteMatches(candidate.body, tap.quote)
        if (matches) found = i
    }
    return found.takeIf { it >= 0 }
}

private fun matchesMedia(message: ChatMessage, hint: MediaHint): Boolean = when (hint) {
    MediaHint.IMAGE -> message.attachments.any { it.mime.startsWith("image/") }
    MediaHint.VIDEO -> message.attachments.any { it.mime.startsWith("video/") }
    MediaHint.AUDIO -> message.attachments.any { it.mime.startsWith("audio/") }
    MediaHint.ANY -> message.attachments.isNotEmpty()
}

private fun quoteMatches(body: String, rawQuote: String): Boolean {
    val trimmed = rawQuote.trim()
    val lead = trimmed.startsWith("…") || trimmed.startsWith("...")
    val tail = trimmed.endsWith("…") || trimmed.endsWith("...")
    var core = trimmed
    if (lead) core = core.removePrefix("…").removePrefix("...").trim()
    if (tail) core = core.removeSuffix("…").removeSuffix("...").trim()
    if (core.length < 2) return false
    val hay = normalizeQuote(body)
    val needle = normalizeQuote(core)
    if (needle.length < 2) return false
    if (hay == needle || hay == normalizeQuote(trimmed)) return true
    return when {
        lead && !tail -> hay.endsWith(needle)
        tail && !lead -> hay.startsWith(needle)
        lead && tail -> hay.contains(needle)
        else -> needle.length >= 12 && hay.contains(needle)
    }
}

private fun normalizeQuote(value: String): String = value
    .replace('\u2019', '\'')
    .replace('\u2018', '\'')
    .replace(Regex("\\s+"), " ")
    .trim()
