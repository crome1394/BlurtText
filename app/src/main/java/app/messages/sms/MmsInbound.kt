package app.messages.sms

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import app.messages.data.MessagingRepository
import java.io.File
import java.util.UUID

data class PendingMms(
    val placeholder: Uri,
    val location: String,
    val from: String,
    val subId: Int,
)

object MmsInbound {
    private const val TAG = "Messages"
    private val started = HashSet<String>()

    fun begin(context: Context, placeholder: Uri, location: String, from: String, subId: Int, quiet: Boolean) {
        if (!location.startsWith("http://") && !location.startsWith("https://")) {
            Log.w(TAG, "MMS location is not a carrier URL")
            return
        }
        val key = placeholder.toString()
        synchronized(started) {
            if (!started.add(key)) return
        }
        val fileName = "dl-" + UUID.randomUUID() + ".dat"
        val file = File(context.cacheDir, fileName)
        file.writeBytes(ByteArray(0))
        val contentUri = Uri.Builder()
            .scheme("content")
            .authority(context.packageName + ".MmsFileProvider")
            .path(fileName)
            .build()
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        for (pkg in listOf("com.android.mms.service", "com.android.phone", "com.android.providers.telephony")) {
            try {
                context.grantUriPermission(pkg, contentUri, grant)
            } catch (_: Exception) {
            }
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
        val intent = Intent(context, MmsDownloadReceiver::class.java).apply {
            data = Uri.parse("messages://mms-download/$fileName")
            clipData = ClipData.newRawUri("mms", contentUri)
            addFlags(grant)
            putExtra("file", fileName)
            putExtra("placeholder", placeholder.toString())
            putExtra("address", from)
            putExtra("quiet", quiet)
        }
        val downloaded = PendingIntent.getBroadcast(context, fileName.hashCode(), intent, flags)
        try {
            smsManager(context, subId).downloadMultimediaMessage(context, location, contentUri, null, downloaded)
            Log.i(TAG, "MMS download started")
        } catch (t: Throwable) {
            Log.e(TAG, "MMS download failed to start", t)
            file.delete()
            synchronized(started) { started.remove(key) }
            if (!quiet) MessageNotifier.simple(context, "Picture message", "Could not start the download.")
        }
    }

    fun retryPending(context: Context, repo: MessagingRepository) {
        if (!SmsRole.isDefault(context)) return
        val pending = try {
            repo.pendingMmsNotifications()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not look for picture messages", t)
            return
        }
        for (item in pending) {
            begin(context, item.placeholder, item.location, item.from, item.subId, quiet = true)
        }
    }
}
