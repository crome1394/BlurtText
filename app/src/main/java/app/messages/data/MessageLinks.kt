package app.messages.data

object MessageLinks {
    private val pattern = Regex(
        """(?i)(?<![\w@])((?:https?://|www\.)[^\s<>"']+)""",
    )
    private val trailing = Regex("""[.,;:!?)]+$""")

    data class Hit(val start: Int, val end: Int, val url: String)

    fun find(text: String): List<Hit> {
        if (text.isBlank()) return emptyList()
        return pattern.findAll(text).mapNotNull { match ->
            val raw = match.groupValues[1]
            val trimmed = raw.replace(trailing, "")
            if (trimmed.length < 8) return@mapNotNull null
            val start = match.range.first
            Hit(start, start + trimmed.length, trimmed)
        }.toList()
    }

    fun href(url: String): String =
        if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url

    fun host(url: String): String {
        val rest = href(url).substringAfter("://", href(url)).substringBefore("/").substringBefore("?")
        return rest.removePrefix("www.").ifBlank { "link" }
    }
}
