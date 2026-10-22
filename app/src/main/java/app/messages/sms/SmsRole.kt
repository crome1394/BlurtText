package app.messages.sms

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony

object SmsRole {
    fun isDefault(context: Context): Boolean {
        if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) return true
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleHeld(RoleManager.ROLE_SMS)) return true
        }
        return false
    }
}

object ActiveThread {
    @Volatile
    var id: Long = -1L
}

object IncomingDedup {
    private var key: String = ""
    private var at: Long = 0L

    fun claim(address: String, body: String): Boolean {
        val next = address + "\n" + body
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (next == key && now - at < 8_000) return false
            key = next
            at = now
            return true
        }
    }
}
