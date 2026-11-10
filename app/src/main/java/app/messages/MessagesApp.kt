package app.messages

import android.app.Application
import android.content.Context
import app.lantext.LanTextApp
import app.lantext.PhoneDesktop
import app.lantext.data.PhoneWeb
import app.lantext.sms.DesktopSchedule
import app.lantext.sms.DesktopTrash
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
import kotlinx.coroutines.runBlocking

class MessagesApp : LanTextApp() {
    init {
        productLabel = "BlurtText"
    }

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
        val staleBackup = java.io.File(cacheDir, "messages-backup.json")
        if (staleBackup.isFile && System.currentTimeMillis() - staleBackup.lastModified() > 120_000) {
            staleBackup.delete()
        }
        phoneDesktop = phoneDesktopBridge()
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
            messaging.repairGroupThreads()
        }
    }

    private suspend fun pushPhoneSettings(persisted: Persisted) {
        mmsDeliveryReports = persisted.deliveryReports
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
            launcherIcon = LauncherIcons.canonical(persisted.launcherIcon),
            inboxGroup = persisted.inboxGroup,
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

    private fun phoneDesktopBridge(): PhoneDesktop = object : PhoneDesktop {
        override fun trashed(): List<DesktopTrash> =
            local.snapshot.trash.map { DesktopTrash(it.threadId.toString(), it.trashedAt) }

        override fun scheduled(): List<DesktopSchedule> =
            local.snapshot.scheduled.map { item ->
                DesktopSchedule(
                    id = item.id,
                    address = item.addresses.joinToString(),
                    sendAt = item.sendAt,
                    body = item.body,
                )
            }

        override fun restoreTrash(threadId: String) {
            val id = threadId.toLongOrNull() ?: return
            runBlocking {
                local.update { state -> state.copy(trash = state.trash.filter { it.threadId != id }) }
            }
            messaging.ping()
        }

        override fun deleteTrash(threadId: String) {
            val id = threadId.toLongOrNull() ?: return
            messaging.deleteThreads(setOf(id))
            runBlocking {
                local.update { state -> state.copy(trash = state.trash.filter { it.threadId != id }) }
            }
        }

        override fun cancelScheduled(id: String) {
            if (id.isBlank()) return
            runBlocking {
                local.update { state -> state.copy(scheduled = state.scheduled.filter { it.id != id }) }
            }
            ScheduleController.cancel(this@MessagesApp, id)
        }
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
