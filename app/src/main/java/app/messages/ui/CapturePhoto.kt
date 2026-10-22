package app.messages.ui

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract

/**
 * Camera capture that grants the camera app write access to [input].
 * Clip data is attached only after the camera component is known. Some versions
 * fail to resolve an implicit capture intent once that URI is on it.
 */
class CapturePhoto : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent {
        val probe = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addCategory(Intent.CATEGORY_DEFAULT)
        val matches = context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val intent = Intent(probe)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .addFlags(flags)
        intent.clipData = ClipData.newRawUri("photo", input)
        val target = matches.firstOrNull()?.activityInfo
        val component = if (target != null) {
            ComponentName(target.packageName, target.name)
        } else {
            knownCamera(context)
        }
        if (component != null) {
            intent.component = component
            context.grantUriPermission(component.packageName, input, flags)
        }
        for (info in matches) {
            context.grantUriPermission(info.activityInfo.packageName, input, flags)
        }
        Log.i(TAG, "camera matches=${matches.size} component=$component")
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK

    companion object {
        private const val TAG = "Messages"

        fun hasCamera(context: Context): Boolean {
            val probe = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addCategory(Intent.CATEGORY_DEFAULT)
            if (probe.resolveActivity(context.packageManager) != null) return true
            return knownCamera(context) != null
        }

        private fun knownCamera(context: Context): ComponentName? {
            val manager = context.packageManager
            for (component in KnownCameras) {
                val info = runCatching { manager.getActivityInfo(component, 0) }.getOrNull() ?: continue
                if (info.exported) return component
            }
            return null
        }

        private val KnownCameras = listOf(
            ComponentName("com.google.android.GoogleCamera", "com.android.camera.activity.CaptureActivity"),
            ComponentName("org.calyxos.aperture", "org.lineageos.aperture.CaptureActivity"),
            ComponentName("org.lineageos.aperture", "org.lineageos.aperture.CaptureActivity"),
            ComponentName("com.android.camera2", "com.android.camera.CaptureActivity"),
        )
    }
}
