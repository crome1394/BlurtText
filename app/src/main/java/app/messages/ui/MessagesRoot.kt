package app.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val screenUnder by vm.screenUnder.collectAsStateWithLifecycle()
    val compose by vm.composeOverlay.collectAsStateWithLifecycle()
    var heldCompose by remember { mutableStateOf<Screen.ComposeNew?>(null) }
    SideEffect {
        if (compose != null) heldCompose = compose
    }
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
            val cardOnTop = screen is Screen.Settings || screen is Screen.ComposeNew
            when (val current = if (cardOnTop) screenUnder else screen) {
                Screen.Inbox -> InboxScreen(vm, onRequestRole)
                is Screen.Thread -> ThreadScreen(vm, current.threadId)
                is Screen.ComposeNew -> InboxScreen(vm, onRequestRole)
                Screen.Settings -> InboxScreen(vm, onRequestRole)
                Screen.Archive -> ArchiveScreen(vm)
                Screen.Blocked -> BlockedScreen(vm)
                Screen.Scheduled -> ScheduledScreen(vm)
                Screen.Trash -> TrashScreen(vm)
            }
            AnimatedVisibility(
                visible = compose != null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = slideInVertically(tween(280)) { height -> height },
                exit = slideOutVertically(tween(200)) { height -> height },
            ) {
                val card = heldCompose
                if (card != null) ComposeScreen(vm, card.addresses, card.body, card.attachmentPath)
            }
            AnimatedVisibility(
                visible = screen is Screen.Settings,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = slideInVertically(tween(280)) { height -> height },
                exit = slideOutVertically(tween(200)) { height -> height },
            ) {
                SettingsScreen(vm, onRequestRole)
            }
            SnackbarHost(
                hostState = vm.snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = (footerHeightDp(settings.footerHeight) + 40).dp).imePadding(),
            )
        }
    }
}
