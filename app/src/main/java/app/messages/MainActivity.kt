package app.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import app.messages.ui.MessagesRoot
import app.messages.ui.MessagesViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MessagesViewModel by viewModels()

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onResume()
        requestRemaining()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.openFromIntent(intent)
        setContent {
            MessagesRoot(viewModel, onRequestRole = ::requestRole)
        }
        requestRemaining()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.openFromIntent(intent)
    }

    private fun requestRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_SMS) && !roles.isRoleHeld(RoleManager.ROLE_SMS)) {
                roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
                return
            }
        }
        val change = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        roleLauncher.launch(change)
    }

    private fun requestRemaining() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        needed += Manifest.permission.READ_CONTACTS
        needed += Manifest.permission.READ_PHONE_STATE
        needed += Manifest.permission.READ_SMS
        needed += Manifest.permission.SEND_SMS
        needed += Manifest.permission.RECEIVE_SMS
        needed += Manifest.permission.RECEIVE_MMS
        needed += Manifest.permission.CAMERA
        val missing = needed.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}
