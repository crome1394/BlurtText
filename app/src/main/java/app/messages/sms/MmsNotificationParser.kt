package app.messages.sms

/**
 * Reads an MMS notification PDU (m-notification-ind) far enough to download it.
 * Header numbers follow OMA-TS-MMS-ENC.
 */
object MmsNotificationParser {
    data class Notification(
        val transactionId: String,
        val contentLocation: String,
        val from: String,
        val size: Long,
    )

    fun parse(pdu: ByteArray?): Notification? {
        if (pdu == null || pdu.isEmpty()) return null
        return try {
            read(pdu)
        } catch (_: Exception) {
            null
        }
    }

    private fun read(pdu: ByteArray): Notification? {
        var i = 0
        var type = -1
        var transactionId = ""
        var location = ""
        var from = ""
        var size = 0L
        while (i < pdu.size) {
            val field = u8(pdu, i)
            if (field < 0x80) break
            i += 1
            when (field and 0x7F) {
                0x0C -> {
                    type = u8(pdu, i)
                    i += 1
                }
                0x0D -> i += 1
                0x18 -> {
                    val text = readText(pdu, i)
                    transactionId = text.value
                    i = text.next
                }
                0x03 -> {
                    val text = readText(pdu, i)
                    location = text.value
                    i = text.next
                }
                0x09 -> {
                    val parsed = readFrom(pdu, i)
                    from = parsed.value
                    i = parsed.next
                }
                0x0E -> {
                    val number = readLong(pdu, i)
                    size = number.value
                    i = number.next
                }
                0x08, 0x05 -> i = skipLength(pdu, i)
                0x16, 0x13 -> i = skipEncodedString(pdu, i)
                0x0A -> i = skipMessageClass(pdu, i)
                0x0B -> i = readText(pdu, i).next
                else -> i = skipUnknown(pdu, i)
            }
        }
        if (type != 0x82 || location.isBlank()) return null
        return Notification(
            transactionId = transactionId,
            contentLocation = location,
            from = cleanAddress(from),
            size = size,
        )
    }

    private data class Text(val value: String, val next: Int)
    private data class Number(val value: Long, val next: Int)

    private fun readText(pdu: ByteArray, start: Int): Text {
        var i = start
        if (i < pdu.size && u8(pdu, i) == 127) i += 1
        val begin = i
        while (i < pdu.size && pdu[i].toInt() != 0) i += 1
        val value = pdu.copyOfRange(begin, i).toString(Charsets.UTF_8)
        if (i < pdu.size && pdu[i].toInt() == 0) i += 1
        return Text(value, i)
    }

    private fun readLong(pdu: ByteArray, start: Int): Number {
        val len = u8(pdu, start)
        var value = 0L
        for (offset in 1..len) {
            value = (value shl 8) or u8(pdu, start + offset).toLong()
        }
        return Number(value, start + 1 + len)
    }

    private fun readFrom(pdu: ByteArray, start: Int): Text {
        val end = skipLength(pdu, start)
        val valueStart = lengthContents(pdu, start)
        if (valueStart >= end || valueStart >= pdu.size) return Text("", end)
        val token = u8(pdu, valueStart)
        val address = when (token) {
            0x81 -> ""
            0x80 -> readEncodedString(pdu, valueStart + 1).value
            else -> readEncodedString(pdu, valueStart).value
        }
        return Text(address, end)
    }

    private fun readEncodedString(pdu: ByteArray, start: Int): Text {
        if (start >= pdu.size) return Text("", start)
        val marker = u8(pdu, start)
        if (marker < 32) {
            val end = skipLength(pdu, start)
            val contents = lengthContents(pdu, start)
            val text = if (contents < end) readText(pdu, contents + 1) else Text("", end)
            return Text(text.value, end)
        }
        return readText(pdu, start)
    }

    private fun skipEncodedString(pdu: ByteArray, start: Int): Int {
        if (start >= pdu.size) return start
        val marker = u8(pdu, start)
        return if (marker < 32) skipLength(pdu, start) else readText(pdu, start).next
    }

    private fun skipMessageClass(pdu: ByteArray, start: Int): Int {
        if (start >= pdu.size) return start
        return if (u8(pdu, start) >= 128) start + 1 else readText(pdu, start).next
    }

    private fun skipUnknown(pdu: ByteArray, start: Int): Int {
        if (start >= pdu.size) return start
        val marker = u8(pdu, start)
        return when {
            marker >= 0x80 -> start + 1
            marker <= 31 -> skipLength(pdu, start)
            else -> readText(pdu, start).next
        }
    }

    private fun skipLength(pdu: ByteArray, start: Int): Int {
        if (start < 0 || start >= pdu.size) error("mms length")
        val marker = u8(pdu, start)
        val end = when {
            marker < 31 -> start + 1 + marker
            marker == 31 -> {
                val length = readUintvar(pdu, start + 1)
                val next = length.next.toLong() + length.value
                if (next > Int.MAX_VALUE) error("mms length")
                next.toInt()
            }
            else -> start
        }
        if (end <= start || end > pdu.size) error("mms length")
        return end
    }

    private fun lengthContents(pdu: ByteArray, start: Int): Int {
        val marker = u8(pdu, start)
        return if (marker == 31) readUintvar(pdu, start + 1).next else start + 1
    }

    private fun readUintvar(pdu: ByteArray, start: Int): Number {
        var value = 0L
        var i = start
        repeat(5) {
            if (i >= pdu.size) return Number(value, i)
            val byte = u8(pdu, i)
            i += 1
            value = (value shl 7) or (byte and 0x7F).toLong()
            if (byte and 0x80 == 0) return Number(value, i)
        }
        return Number(value, i)
    }

    private fun cleanAddress(raw: String): String = raw.substringBefore("/").trim()

    private fun u8(pdu: ByteArray, index: Int): Int {
        if (index < 0 || index >= pdu.size) error("mms pdu ended early")
        return pdu[index].toInt() and 0xFF
    }
}
