package app.messages.sms

/**
 * Reads an m-retrieve-conf PDU (OMA-TS-MMS-ENC) far enough to store its parts.
 * The platform download writes this PDU to a file. The telephony provider does
 * not parse that file, so the inbox has to.
 */
object MmsRetrieveParser {
    data class Part(
        val mime: String,
        val name: String,
        val bytes: ByteArray,
        val charset: Int = UTF_8,
    ) {
        fun text(): String = bytes.toString(charsetFor(charset))
    }
    data class Retrieved(
        val from: String,
        val dateSeconds: Long,
        val parts: List<Part>,
        val to: List<String> = emptyList(),
        val cc: List<String> = emptyList(),
    )

    fun parse(pdu: ByteArray?): Retrieved? {
        if (pdu == null || pdu.size < 4) return null
        return try {
            read(pdu)
        } catch (_: Exception) {
            null
        }
    }

    private fun read(pdu: ByteArray): Retrieved? {
        var i = 0
        var type = -1
        var from = ""
        var date = 0L
        val to = ArrayList<String>()
        val cc = ArrayList<String>()
        val limit = pdu.size
        while (i < limit) {
            val field = u8(pdu, i)
            if (field < 0x80) return null
            i += 1
            when (field) {
                0x8C -> {
                    type = u8(pdu, i)
                    i += 1
                }
                0x8D, 0x8F, 0x86, 0x90 -> i += 1
                0x98, 0x8B -> i = readText(pdu, i, limit).next
                0x85 -> {
                    val number = readLong(pdu, i, limit)
                    date = number.value
                    i = number.next
                }
                0x89 -> {
                    val parsed = readFrom(pdu, i, limit)
                    from = parsed.value
                    i = parsed.next
                }
                0x97, 0x82 -> {
                    val parsed = readEncoded(pdu, i, limit)
                    val address = cleanAddress(parsed.value)
                    if (address.isNotBlank() && !address.equals("insert-address-token", true)) {
                        if (field == 0x97) to += address else cc += address
                    }
                    i = parsed.next
                }
                0x84 -> {
                    if (type != 0x84) return null
                    i = skipContentType(pdu, i, limit)
                    val parts = readParts(pdu, i, limit)
                    return Retrieved(cleanAddress(from), date, parts, to, cc)
                }
                else -> i = skipValue(pdu, i, limit)
            }
        }
        return null
    }

    private fun readParts(pdu: ByteArray, start: Int, limit: Int): List<Part> {
        val count = readUintvar(pdu, start, limit)
        var i = count.next
        val total = count.value.coerceIn(0L, 16L).toInt()
        val out = ArrayList<Part>(total)
        repeat(total) {
            val headerLen = readUintvar(pdu, i, limit)
            val dataLen = readUintvar(pdu, headerLen.next, limit)
            val headerStart = dataLen.next
            val headerEnd = headerStart + headerLen.value
            val dataEnd = headerEnd + dataLen.value
            if (headerEnd > limit || dataEnd > limit) return out
            val part = readPart(pdu, headerStart, headerEnd.toInt(), dataEnd.toInt(), out.size)
            if (part != null) out += part
            i = dataEnd.toInt()
        }
        return out
    }

    private fun readPart(pdu: ByteArray, start: Int, headerEnd: Int, dataEnd: Int, index: Int): Part? {
        val typed = readContentType(pdu, start, headerEnd)
        var mime = typed.mime.lowercase()
        val charset = typed.charset
        var i = typed.next
        var name = ""
        while (i < headerEnd) {
            val field = u8(pdu, i)
            if (field >= 0x80) {
                i += 1
                when (field) {
                    0x8E, 0xC0 -> {
                        val text = readText(pdu, i, headerEnd)
                        val value = text.value.trim().trim('"').trim('<', '>')
                        if (name.isBlank() && value.isNotBlank()) name = value
                        i = text.next
                    }
                    else -> i = skipValue(pdu, i, headerEnd)
                }
            } else if (field < 32) {
                i = skipLength(pdu, i, headerEnd)
            } else {
                i = readText(pdu, i, headerEnd).next
                if (i < headerEnd) i = skipValue(pdu, i, headerEnd)
            }
        }
        if (mime.isBlank() || mime == "application/smil") return null
        if (name.isBlank()) name = "part$index"
        val bytes = pdu.copyOfRange(headerEnd, dataEnd)
        if (mime == "text/plain" && bytes.isEmpty()) return null
        return Part(mime, name.substringAfterLast('/').ifBlank { name }, bytes, charset)
    }

    private data class Typed(val mime: String, val next: Int, val charset: Int)
    private data class Text(val value: String, val next: Int)
    private data class Number(val value: Long, val next: Int)

    private fun skipContentType(pdu: ByteArray, start: Int, limit: Int): Int = readContentType(pdu, start, limit).next

    private fun readContentType(pdu: ByteArray, start: Int, limit: Int): Typed {
        val marker = u8(pdu, start)
        if (marker >= 0x80) return Typed(mimeOf(marker and 0x7F), start + 1, UTF_8)
        if (marker <= 31) {
            val end = skipLength(pdu, start, limit)
            val contents = lengthContents(pdu, start)
            if (contents >= end) return Typed("application/octet-stream", end, UTF_8)
            val media = readMediaToken(pdu, contents, end)
            return Typed(media.mime, end, charsetParameter(pdu, media.next, end))
        }
        val text = readText(pdu, start, limit)
        return Typed(text.value.ifBlank { "application/octet-stream" }, text.next, UTF_8)
    }

    private data class Media(val mime: String, val next: Int)

    private fun readMediaToken(pdu: ByteArray, start: Int, end: Int): Media {
        if (start >= end) return Media("application/octet-stream", start)
        val marker = u8(pdu, start)
        if (marker >= 0x80) return Media(mimeOf(marker and 0x7F), start + 1)
        val text = readText(pdu, start, end)
        return Media(text.value.ifBlank { "application/octet-stream" }, text.next)
    }

    private fun charsetParameter(pdu: ByteArray, start: Int, end: Int): Int {
        var i = start
        var charset = UTF_8
        while (i < end) {
            val marker = u8(pdu, i)
            if (marker >= 0x80) {
                val value = readTypedValue(pdu, i + 1, end)
                if (marker and 0x7F == 0x01) charset = value.mib ?: mibForName(value.text) ?: UTF_8
                i = value.next
            } else if (marker < 32) {
                val next = skipLength(pdu, i, end)
                if (next <= i) break
                i = next
            } else {
                val name = readText(pdu, i, end)
                val value = readTypedValue(pdu, name.next, end)
                if (name.value.equals("charset", ignoreCase = true)) {
                    charset = value.mib ?: mibForName(value.text) ?: UTF_8
                }
                i = value.next
            }
        }
        return charset
    }

    private data class TypedValue(val mib: Int?, val text: String, val next: Int)

    private fun readTypedValue(pdu: ByteArray, start: Int, end: Int): TypedValue {
        if (start >= end) return TypedValue(null, "", start)
        val marker = u8(pdu, start)
        if (marker >= 0x80) return TypedValue(marker and 0x7F, "", start + 1)
        if (marker in 1..30 && start + 1 + marker <= end) {
            var value = 0
            for (offset in 1..marker) value = (value shl 8) or u8(pdu, start + offset)
            return TypedValue(value, "", start + 1 + marker)
        }
        val text = readText(pdu, start, end)
        return TypedValue(mibForName(text.value), text.value, text.next)
    }

    private fun readFrom(pdu: ByteArray, start: Int, limit: Int): Text {
        val end = skipLength(pdu, start, limit)
        val valueStart = lengthContents(pdu, start)
        if (valueStart >= end) return Text("", end)
        val token = u8(pdu, valueStart)
        val address = when (token) {
            0x81 -> ""
            0x80 -> readEncoded(pdu, valueStart + 1, end).value
            else -> readEncoded(pdu, valueStart, end).value
        }
        return Text(address, end)
    }

    private fun readEncoded(pdu: ByteArray, start: Int, end: Int): Text {
        if (start >= end) return Text("", start)
        val marker = u8(pdu, start)
        if (marker <= 31) {
            val block = skipLength(pdu, start, end)
            val contents = lengthContents(pdu, start)
            val text = if (contents < block) readText(pdu, contents + 1, block) else Text("", block)
            return Text(text.value, block)
        }
        return readText(pdu, start, end)
    }

    private fun readText(pdu: ByteArray, start: Int, end: Int): Text {
        var i = start
        if (i < end && u8(pdu, i) == 127) i += 1
        if (i < end && u8(pdu, i) == 34) i += 1
        val begin = i
        while (i < end && pdu[i].toInt() != 0) i += 1
        val value = pdu.copyOfRange(begin, i.coerceAtMost(end)).toString(Charsets.UTF_8)
        if (i < end && pdu[i].toInt() == 0) i += 1
        return Text(value, i.coerceAtMost(end))
    }

    private fun readLong(pdu: ByteArray, start: Int, limit: Int): Number {
        val len = u8(pdu, start)
        if (start + 1 + len > limit) error("mms length")
        var value = 0L
        for (offset in 1..len) value = (value shl 8) or u8(pdu, start + offset).toLong()
        return Number(value, start + 1 + len)
    }

    private fun skipValue(pdu: ByteArray, start: Int, limit: Int): Int {
        if (start >= limit) return start
        val marker = u8(pdu, start)
        return when {
            marker >= 0x80 -> start + 1
            marker <= 31 -> skipLength(pdu, start, limit)
            else -> readText(pdu, start, limit).next
        }
    }

    private fun skipLength(pdu: ByteArray, start: Int, limit: Int): Int {
        val marker = u8(pdu, start)
        val end = when {
            marker < 31 -> start + 1 + marker
            marker == 31 -> {
                val length = readUintvar(pdu, start + 1, limit)
                val next = length.next.toLong() + length.value
                if (next > Int.MAX_VALUE) error("mms length")
                next.toInt()
            }
            else -> start
        }
        if (end <= start || end > limit) error("mms length")
        return end
    }

    private fun lengthContents(pdu: ByteArray, start: Int): Int {
        val marker = u8(pdu, start)
        return if (marker == 31) readUintvar(pdu, start + 1, pdu.size).next else start + 1
    }

    private fun readUintvar(pdu: ByteArray, start: Int, limit: Int): Number {
        var value = 0L
        var i = start
        repeat(5) {
            if (i >= limit) return Number(value, i)
            val byte = u8(pdu, i)
            i += 1
            value = (value shl 7) or (byte and 0x7F).toLong()
            if (byte and 0x80 == 0) return Number(value, i)
        }
        return Number(value, i)
    }

    private fun mimeOf(index: Int): String = when (index) {
        0x03 -> "text/plain"
        0x02 -> "text/html"
        0x1C -> "image/*"
        0x1D -> "image/gif"
        0x1E -> "image/jpeg"
        0x20 -> "image/png"
        0x21 -> "image/vnd.wap.wbmp"
        0x23 -> "application/vnd.wap.multipart.mixed"
        0x33 -> "application/vnd.wap.multipart.related"
        0x4F -> "audio/*"
        0x50 -> "video/*"
        else -> "application/octet-stream"
    }

    private const val UTF_8 = 106

    private fun charsetFor(mib: Int): java.nio.charset.Charset = when (mib) {
        3 -> Charsets.US_ASCII
        in 4..12 -> java.nio.charset.Charset.forName("ISO-8859-${mib - 3}")
        17 -> java.nio.charset.Charset.forName("Shift_JIS")
        106 -> Charsets.UTF_8
        1000, 1013 -> Charsets.UTF_16BE
        1014 -> Charsets.UTF_16LE
        1015 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    private fun mibForName(name: String): Int? {
        val clean = name.trim().lowercase().replace('_', '-')
        when (clean) {
            "utf-8" -> return 106
            "us-ascii", "ascii" -> return 3
            "shift-jis" -> return 17
            "utf-16" -> return 1015
            "utf-16be" -> return 1013
            "utf-16le" -> return 1014
            "ucs-2", "iso-10646-ucs-2" -> return 1000
            "latin-1", "latin1" -> return 4
        }
        val iso = Regex("iso-8859-(\\d+)").matchEntire(clean) ?: return null
        val part = iso.groupValues[1].toIntOrNull() ?: return null
        return if (part in 1..9) part + 3 else null
    }

    private fun cleanAddress(raw: String): String = raw.substringBefore("/").trim().trim('<', '>')

    private fun u8(pdu: ByteArray, index: Int): Int {
        if (index < 0 || index >= pdu.size) error("mms pdu ended early")
        return pdu[index].toInt() and 0xFF
    }
}
