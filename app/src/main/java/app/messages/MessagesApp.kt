package app.messages

import android.app.Application
import android.content.Context
import app.lantext.LanTextApp
import app.lantext.data.PhoneWeb
import app.messages.data.LocalStore
import app.messages.data.MessagingRepository
import app.messages.data.Persisted
import app.messages.data.resolvedRibbon
import app.messages.sms.MessageNotifier
import app.messages.ui.LauncherIcons
import app.messages.ui.resolvedPalette
import app.messages.sms.MmsInbound
import app.messages.sms.ScheduleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessagesApp : LanTextApp() {
    lateinit var local: LocalStore
        private set
    lateinit var messaging: MessagingRepository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var phonePushReady = false

    @Volatile
    private var pushedAppearance: Pair<String, String>? = null

    override fun onCreate() {
        super.onCreate()
        local = LocalStore(this)
        messaging = MessagingRepository(this, local)
        MessageNotifier.ensureChannel(this)
        messaging.start()
        scope.launch {
            local.settings.collect { persisted ->
                LauncherIcons.apply(this@MessagesApp, persisted.launcherIcon)
                pushPhoneSettings(persisted)
            }
        }
        scope.launch {
            settings.settings.collect { web ->
                if (!phonePushReady) return@collect
                val pushed = pushedAppearance
                if (pushed != null && pushed.first == web.webPalette && pushed.second == web.webMode) return@collect
                val phone = local.snapshot
                val phoneMode = webMode(phone.theme)
                val phonePalette = resolvedPalette(phone.palette)
                if (web.webPalette == phonePalette && web.webMode == phoneMode) return@collect
                val theme = when (web.webMode) {
                    "light" -> "light"
                    "dark" -> "dark"
                    else -> "system"
                }
                pushedAppearance = web.webPalette to web.webMode
                local.update { it.copy(palette = web.webPalette, theme = theme, dynamicColor = false) }
            }
        }
        scope.launch(Dispatchers.IO) {
            ScheduleController.rearmAll(this@MessagesApp)
            MmsInbound.retryPending(this@MessagesApp, messaging)
        }
    }

    private suspend fun pushPhoneSettings(persisted: Persisted) {
        val mode = webMode(persisted.theme)
        val palette = resolvedPalette(persisted.palette)
        val ribbon = resolvedRibbon(persisted.ribbon)
        fun shown(id: String) = ribbon.firstOrNull { it.id == id }?.shown != false
        val web = PhoneWeb(
            palette = palette,
            mode = mode,
            fontScale = persisted.fontScale.coerceIn(0.85f, 1.4f),
            striped = persisted.stripedInbox,
            searchAtBottom = persisted.searchAtBottom,
            enterSends = persisted.enterSends,
            showSim = persisted.showSimIndicator,
            detailDirection = persisted.detailDirection,
            detailParty = persisted.detailParty,
            detailTime = persisted.detailTime,
            detailType = persisted.detailType,
            detailSim = persisted.detailSim,
            defaultSubId = persisted.defaultSubId,
            ribbonRead = shown("read"),
            ribbonDelete = shown("delete"),
            ribbonPin = shown("pin"),
        )
        val current = settings.current()
        if (current.gifApiKey != persisted.gifApiKey) settings.setGifApiKey(persisted.gifApiKey)
        if (current.phoneWeb != web) settings.setPhoneWeb(web)
        if (current.webPalette != palette || current.webMode != mode) {
            pushedAppearance = palette to mode
            settings.setAppearance(palette, mode)
        }
        phonePushReady = true
    }

    private fun webMode(theme: String): String = when (theme) {
        "light" -> "light"
        "dark" -> "dark"
        else -> "auto"
    }

    companion object {
        fun from(context: Context): MessagesApp = context.applicationContext as MessagesApp
    }
}
