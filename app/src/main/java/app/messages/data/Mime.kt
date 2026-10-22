package app.messages.data

fun mimeForPath(path: String): String = when (path.substringAfterLast('.').lowercase()) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "jpg", "jpeg" -> "image/jpeg"
    "mp4" -> "video/mp4"
    "3gp", "3gpp" -> "video/3gpp"
    "amr" -> "audio/amr"
    "aac", "m4a" -> "audio/aac"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}

fun extensionFor(mime: String): String = when {
    mime.contains("png") -> "png"
    mime.contains("gif") -> "gif"
    mime.contains("webp") -> "webp"
    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
    mime.contains("3gpp") -> "3gp"
    mime.startsWith("video/") -> "mp4"
    mime.contains("amr") -> "amr"
    mime.startsWith("audio/") -> "aac"
    else -> "bin"
}
