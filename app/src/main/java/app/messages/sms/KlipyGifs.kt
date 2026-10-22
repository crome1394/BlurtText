package app.messages.sms

import app.lantext.sms.GifHit
import app.lantext.sms.GifSearch

/** Phone GIF picker. Search and download go through the shared KLIPY client. */
object KlipyGifs {
    const val PARTNER_KEYS = "https://partner.klipy.com/api-keys"

    data class Hit(
        val id: String,
        val previewUrl: String,
        val sendUrl: String,
        val bytes: Int,
    )

    fun search(apiKey: String, query: String, maxBytes: Int): List<Hit> =
        GifSearch.search(query, apiKey, maxBytes).map { it.toHit() }

    fun download(url: String, maxBytes: Int): ByteArray = GifSearch.download(url, maxBytes)

    fun isGif(bytes: ByteArray): Boolean =
        bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()

    fun isAllowed(url: String): Boolean = GifSearch.isAllowed(url)

    fun hitsFrom(json: String, maxBytes: Int): List<Hit> =
        GifSearch.hitsFrom(json, maxBytes).map { it.toHit() }

    private fun GifHit.toHit() = Hit(
        id = id,
        previewUrl = url,
        sendUrl = sendUrl.ifBlank { url },
        bytes = bytes,
    )
}
