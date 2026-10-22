package app.messages.sms

import android.content.Intent
import android.net.Uri
import android.os.Build

data class Outbound(
    val addresses: List<String>,
    val body: String,
    val attachment: Uri?,
)

object SendIntents {
    fun parse(intent: Intent): Outbound? {
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SENDTO && action != Intent.ACTION_VIEW) {
            return null
        }
        val addresses = addresses(intent.data)
        val body = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra("sms_body")
            ?: intent.data?.getQueryParameter("body")
            ?: ""
        val attachment = intent.parcelUri(Intent.EXTRA_STREAM)
        if (addresses.isEmpty() && body.isBlank() && attachment == null) return null
        return Outbound(addresses, body, attachment)
    }

    private fun addresses(uri: Uri?): List<String> {
        if (uri == null) return emptyList()
        val scheme = uri.scheme?.lowercase() ?: return emptyList()
        if (scheme !in setOf("sms", "smsto", "mms", "mmsto")) return emptyList()
        val raw = Uri.decode(uri.schemeSpecificPart.substringBefore('?').removePrefix("//"))
        return raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun Intent.parcelUri(name: String): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }
}
