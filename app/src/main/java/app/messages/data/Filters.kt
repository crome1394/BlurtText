package app.messages.data

fun matchesBlockedKeyword(body: String, keywords: List<String>): Boolean {
    if (body.isEmpty()) return false
    return keywords.any { keyword ->
        val needle = keyword.trim()
        needle.isNotEmpty() && body.contains(needle, ignoreCase = true)
    }
}

fun keywordsFrom(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
