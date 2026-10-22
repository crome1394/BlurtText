package app.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.messages.data.footerHeightDp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MessagesRoot(vm: MessagesViewModel, onRequestRole: () -> Unit) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val canPop by vm.canPop.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = canPop) { vm.pop() }
    MessagesTheme(settings) {
        Box(Modifier.fillMaxSize()) {
            when (val current = screen) {
                Screen.Inbox -> InboxScreen(vm, onRequestRole)
                is Screen.Thread -> ThreadScreen(vm, current.threadId)
                is Screen.ComposeNew -> ComposeScreen(vm, current.addresses, current.body, current.attachmentPath)
                Screen.Settings -> SettingsScreen(vm, onRequestRole)
                Screen.Archive -> ArchiveScreen(vm)
                Screen.Blocked -> BlockedScreen(vm)
                Screen.Scheduled -> ScheduledScreen(vm)
            }
            SnackbarHost(
                hostState = vm.snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = (footerHeightDp(settings.footerHeight) + 40).dp).imePadding(),
            )
        }
    }
}
