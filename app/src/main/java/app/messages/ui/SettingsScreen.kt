package app.messages.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.BuildConfig
import app.messages.data.footerHeightDp
import app.messages.data.footerLabel
import app.messages.data.resolvedFooter
import app.messages.data.resolvedRibbon
import app.messages.data.ribbonLabel
import app.messages.data.ribbonTopCount
import app.messages.data.simDetail
import app.messages.data.swipeAction
import app.messages.sms.KlipyGifs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val SETTINGS_TAB_LAN_MESSAGING = 11

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MessagesViewModel, onRequestRole: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isDefault by vm.isDefault.collectAsStateWithLifecycle()
    val exact by vm.exactAlarms.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var keywords by remember(settings.blockedKeywords) { mutableStateOf(settings.blockedKeywords.joinToString("\n")) }
    var gifKey by remember(settings.gifApiKey) { mutableStateOf(settings.gifApiKey) }
    var showGifKey by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val pager = rememberPagerState { settingTabs.size }
    val tabPulse by vm.settingsTabPulse.collectAsStateWithLifecycle()
    LaunchedEffect(tabPulse) {
        val tab = vm.takeSettingsTab() ?: return@LaunchedEffect
        val index = tab.coerceIn(0, settingTabs.lastIndex)
        if (pager.currentPage != index) pager.scrollToPage(index)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { vm.importBackup(uri) }
                    .onSuccess { vm.snackbar.showSnackbar("Imported $it texts") }
                    .onFailure { vm.snackbar.showSnackbar(it.message ?: "Could not import") }
            }
        }
    }
    LaunchedEffect(query) {
        delay(180)
        val q = query.trim()
        val here = pager.currentPage
        val matches = q.isEmpty() || tabMatches(here, q)
        if (!matches) {
            val index = settingTabs.indices.firstOrNull { tabMatches(it, q) } ?: return@LaunchedEffect
            pager.animateScrollToPage(index)
        } else if (pager.currentPageOffsetFraction != 0f) {
            pager.animateScrollToPage(here)
        }
    }
    val searchField = @Composable {
        SettingsSearch(query) { query = it }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (settings.searchAtBottom) {
                Column {
                    HorizontalDivider()
                    searchField()
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!settings.searchAtBottom) searchField()
            PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage) {
                settingTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(title, maxLines = 1) },
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f),
                beyondViewportPageCount = 0,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = tween(durationMillis = 180),
                ),
            ) { page ->
                when (page) {
                    0 -> SettingsGeneralPage(vm, settings, isDefault, query, onRequestRole) {
                        scope.launch { pager.animateScrollToPage(SETTINGS_TAB_LAN_MESSAGING) }
                    }
                    1 -> AppearancePage(vm, settings, query)
                    2 -> SendingPage(vm, settings, exact, context, query)
                    3 -> CarrierPage(vm, settings, query)
                    4 -> NotificationsPage(vm, settings, query)
                    5 -> BlockingPage(vm, keywords, query, onKeywords = { keywords = it })
                    6 -> GifsPage(vm, gifKey, showGifKey, query, context, onKey = { gifKey = it }, onShow = { showGifKey = it })
                    7 -> BackupPage(vm, query, context, scope, import)
                    8 -> SwipePage(vm, settings, query)
                    9 -> RibbonPage(vm, settings, query)
                    10 -> FooterPage(vm, settings, query)
                    SETTINGS_TAB_LAN_MESSAGING -> {
                        if (query.isNotBlank() && !tabMatches(page, query)) NoMatches()
                        else LanMessagingPage(active = pager.currentPage == page)
                    }
                    else -> AboutPage(query)
                }
            }
        }
    }
}

private val settingTabs = listOf(
    "Settings",
    "Appearance",
    "Sending",
    "Carrier",
    "Notifications",
    "Blocking",
    "GIFs",
    "Backup",
    "Swipe",
    "Ribbon",
    "Footer",
    "LanMessaging",
    "About",
)

private fun tabMatches(index: Int, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val title = settingTabs.getOrNull(index).orEmpty()
    if (title.contains(q, ignoreCase = true)) return true
    val blob = when (index) {
        0 -> "computer default sms search at the bottom wifi"
        1 -> "theme system light dark color palette dynamic wallpaper alternating threads text size small normal large extra app icon launcher original black cyan dark blue dark purple dark red green light blue orange purple red white yellow fern ocean dusk ember slate sakura meadow nord contrast"
        2 -> "enter key sends delivery reports exact alarms scheduled"
        3 -> "default sim carrier indicator sent received name number time message type sms mms system"
        4 -> "show message text lock screen notification"
        5 -> "keyword filter blocked numbers phrase"
        6 -> "klipy api key partner gif"
        7 -> "export import texts json backup"
        8 -> "left side right side unread delete none thread"
        9 -> "ribbon pin unpin mark as read mark as unread delete block archive show hide drag move to the top selected arrangement"
        10 -> "footer height search settings computer filter icon show hide"
        11 -> "lanmessaging wifi port paired computers web access networks listen"
        12 -> "version lanmessaging computer access"
        else -> ""
    }
    return blob.contains(q, ignoreCase = true)
}

private fun visible(query: String, page: String, vararg text: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (page.contains(q, ignoreCase = true)) return true
    return text.any { it.contains(q, ignoreCase = true) }
}

@Composable
private fun SettingsSearch(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search settings") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear search") }
            }
        },
    )
}

@Composable
private fun NoMatches() {
    Text("No matches", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsGeneralPage(
    vm: MessagesViewModel,
    settings: app.messages.data.Persisted,
    isDefault: Boolean,
    query: String,
    onRequestRole: () -> Unit,
    onOpenLanMessaging: () -> Unit,
) {
    val page = "Settings"
    val rows = listOf(
        visible(query, page, "LanMessaging", "Computer", "Wi-Fi", "Type on a computer"),
        visible(query, page, "Default SMS app", "Not the default SMS app", "New texts"),
        visible(query, page, "Search at the bottom", "search field"),
    )
    if (rows.none { it }) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (rows[0]) {
            item {
                ListItem(
                    headlineContent = { Text("LanMessaging") },
                    supportingContent = { Text("Type on a computer over your Wi-Fi. Nothing is uploaded.") },
                    leadingContent = { Icon(Icons.Filled.Computer, null) },
                    trailingContent = { TextButton(onClick = onOpenLanMessaging) { Text("Open") } },
                )
            }
        }
        if (rows[1]) {
            item {
                ListItem(
                    headlineContent = { Text(if (isDefault) "Default SMS app" else "Not the default SMS app") },
                    supportingContent = { Text(if (isDefault) "New texts arrive here." else "Sending and new texts stay with the current default app.") },
                    trailingContent = {
                        if (!isDefault) TextButton(onClick = onRequestRole) { Text("Set") }
                    },
                )
            }
        }
        if (rows[2]) {
            item {
                ListItem(
                    headlineContent = { Text("Search at the bottom") },
                    supportingContent = { Text("Keeps the search field at the bottom of the screen.") },
                    trailingContent = {
                        AppSwitch(checked = settings.searchAtBottom, onCheckedChange = { checked ->
                            vm.update { it.copy(searchAtBottom = checked) }
                        })
                    },
                )
            }
        }
    }
}

@Composable
private fun AppearancePage(vm: MessagesViewModel, settings: app.messages.data.Persisted, query: String) {
    val page = "Appearance"
    val paletteNames = ThemeChoices.joinToString(" ") { it.label }
    val rows = listOf(
        visible(query, page, "Theme", "System", "Light", "Dark"),
        visible(query, page, "App icon", "Launcher", "Original", "Black", "Cyan", "Green", "White"),
        visible(query, page, "Color palette", paletteNames),
        visible(query, page, "Dynamic color", "Wallpaper"),
        visible(query, page, "Alternating threads", "Shade every other"),
        visible(query, page, "Text size", "Small", "Normal", "Large", "Extra large"),
    )
    if (rows.none { it }) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (rows[0]) {
            item {
                Choice("Theme", listOf("system" to "System", "light" to "Light", "dark" to "Dark"), settings.theme) {
                    vm.update { state -> state.copy(theme = it) }
                }
            }
        }
        if (rows[1]) {
            item {
                AppIconPicker(settings.launcherIcon) { id ->
                    vm.update { it.copy(launcherIcon = id) }
                }
            }
        }
        if (rows[2]) {
            item {
                ThemePalettePicker(settings) { id ->
                    vm.update { it.copy(palette = id, dynamicColor = false) }
                }
            }
        }
        if (rows[3]) {
            item {
                ListItem(
                    headlineContent = { Text("Dynamic color") },
                    supportingContent = { Text("Wallpaper colors. Choosing a theme above turns this off.") },
                    trailingContent = {
                        AppSwitch(checked = settings.dynamicColor, onCheckedChange = { checked ->
                            vm.update { it.copy(dynamicColor = checked) }
                        })
                    },
                )
            }
        }
        if (rows[4]) {
            item {
                ListItem(
                    headlineContent = { Text("Alternating threads") },
                    supportingContent = { Text("Shade every other conversation so the rows are easier to separate.") },
                    trailingContent = {
                        AppSwitch(checked = settings.stripedInbox, onCheckedChange = { checked ->
                            vm.update { it.copy(stripedInbox = checked) }
                        })
                    },
                )
            }
        }
        if (rows[5]) {
            item {
                Choice(
                    "Text size",
                    listOf("0.9" to "Small", "1.0" to "Normal", "1.15" to "Large", "1.3" to "Extra large"),
                    settings.fontScale.toString().let { if (it == "1.0" || it == "1") "1.0" else it },
                ) { value ->
                    vm.update { it.copy(fontScale = value.toFloat()) }
                }
            }
        }
    }
}

@Composable
private fun SendingPage(
    vm: MessagesViewModel,
    settings: app.messages.data.Persisted,
    exact: Boolean,
    context: android.content.Context,
    query: String,
) {
    val page = "Sending"
    val rows = listOf(
        visible(query, page, "Enter key sends", "Shift"),
        visible(query, page, "Delivery reports", "carrier"),
        visible(query, page, "Exact alarms", "scheduled"),
    )
    if (rows.none { it }) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (rows[0]) {
            item {
                ListItem(
                    headlineContent = { Text("Enter key sends") },
                    supportingContent = { Text("Shift+Enter still adds a line. The keyboard send button always sends.") },
                    trailingContent = {
                        AppSwitch(checked = settings.enterSends, onCheckedChange = { checked ->
                            vm.update { it.copy(enterSends = checked) }
                        })
                    },
                )
            }
        }
        if (rows[1]) {
            item {
                ListItem(
                    headlineContent = { Text("Delivery reports") },
                    supportingContent = { Text("Ask the carrier when a text arrives. Not every carrier answers.") },
                    trailingContent = {
                        AppSwitch(checked = settings.deliveryReports, onCheckedChange = { checked ->
                            vm.update { it.copy(deliveryReports = checked) }
                        })
                    },
                )
            }
        }
        if (rows[2]) {
            item {
                ListItem(
                    headlineContent = { Text("Exact alarms for scheduled texts") },
                    supportingContent = { Text(if (exact) "Scheduled texts can send on time." else "Allow alarms so a scheduled text is not late.") },
                    trailingContent = {
                        if (!exact) {
                            TextButton(onClick = {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                context.startActivity(intent)
                            }) { Text("Allow") }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarrierPage(vm: MessagesViewModel, settings: app.messages.data.Persisted, query: String) {
    val sims by vm.sims.collectAsStateWithLifecycle()
    val page = "Carrier"
    val rows = listOf(
        visible(query, page, "Default SIM", "System default", "No active SIM", "No SIM cards are active"),
        visible(query, page, "Show a SIM indicator", "Shows the SIM"),
        visible(query, page, "Message details", "Sent or received", "Name or number", "Time", "Message type", "SIM", "Small text"),
    )
    if (rows.none { it }) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (rows[0]) {
            item {
                if (sims.isEmpty()) {
                    ListItem(
                        headlineContent = { Text("No active SIM") },
                        supportingContent = { Text("No SIM cards are active.") },
                    )
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Default SIM", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "New messages use this SIM unless you pick another while sending.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val selected = settings.defaultSubId
                            TextButton(onClick = { vm.update { it.copy(defaultSubId = -1) } }) {
                                Text(if (selected < 0) "● System default" else "System default")
                            }
                            sims.forEach { sim ->
                                val label = simDetail(sim.id, sims)
                                TextButton(onClick = { vm.update { it.copy(defaultSubId = sim.id) } }) {
                                    Text(if (selected == sim.id) "● $label" else label)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (rows[1]) {
            item {
                ListItem(
                    headlineContent = { Text("Show a SIM indicator") },
                    supportingContent = { Text("Shows the SIM on each message.") },
                    trailingContent = {
                        AppSwitch(
                            checked = settings.showSimIndicator,
                            onCheckedChange = { checked -> vm.update { it.copy(showSimIndicator = checked) } },
                            modifier = Modifier.semantics { contentDescription = "Show a SIM indicator" },
                        )
                    },
                )
            }
        }
        if (rows[2]) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Message details", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Small text under each message.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { DetailAppSwitch("Sent or received", settings.detailDirection) { checked -> vm.update { it.copy(detailDirection = checked) } } }
            item { DetailAppSwitch("Name or number", settings.detailParty) { checked -> vm.update { it.copy(detailParty = checked) } } }
            item { DetailAppSwitch("Time", settings.detailTime) { checked -> vm.update { it.copy(detailTime = checked) } } }
            item { DetailAppSwitch("Message type", settings.detailType) { checked -> vm.update { it.copy(detailType = checked) } } }
            item { DetailAppSwitch("SIM", settings.detailSim) { checked -> vm.update { it.copy(detailSim = checked) } } }
        }
    }
}

@Composable
private fun DetailAppSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            AppSwitch(
                checked = checked,
                onCheckedChange = onChecked,
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
    )
}

@Composable
private fun NotificationsPage(vm: MessagesViewModel, settings: app.messages.data.Persisted, query: String) {
    val page = "Notifications"
    if (!visible(query, page, "Show message text", "lock screen")) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("Show message text") },
                supportingContent = { Text("Turn off to hide the sender and the message on the lock screen notification.") },
                trailingContent = {
                    AppSwitch(checked = settings.notificationPreview, onCheckedChange = { checked ->
                        vm.update { it.copy(notificationPreview = checked) }
                    })
                },
            )
        }
    }
}

@Composable
private fun BlockingPage(
    vm: MessagesViewModel,
    keywords: String,
    query: String,
    onKeywords: (String) -> Unit,
) {
    val page = "Blocking"
    if (!visible(query, page, "Keyword filter", "Blocked numbers", "phrase")) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Keyword filter", style = MaterialTheme.typography.titleSmall)
                Text(
                    "One phrase per line. A matching text is saved as read and does not notify you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = onKeywords,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 3,
                )
                TextButton(onClick = { vm.setKeywords(keywords) }) { Text("Save keywords") }
                TextButton(onClick = vm::showBlocked) { Text("Blocked numbers") }
            }
        }
    }
}

@Composable
private fun GifsPage(
    vm: MessagesViewModel,
    gifKey: String,
    showGifKey: Boolean,
    query: String,
    context: android.content.Context,
    onKey: (String) -> Unit,
    onShow: (Boolean) -> Unit,
) {
    val page = "GIFs"
    if (!visible(query, page, "KLIPY API key", "partner.klipy.com", "test key")) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("KLIPY API key", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Request a test key at partner.klipy.com. A test key allows 100 calls an hour. Paste it here for the GIF picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = gifKey,
                    onValueChange = onKey,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = if (showGifKey) VisualTransformation.None else PasswordVisualTransformation(),
                )
                Row {
                    TextButton(onClick = {
                        vm.update { it.copy(gifApiKey = gifKey.trim()) }
                        vm.note("API key saved")
                    }) { Text("Save API key") }
                    TextButton(onClick = { onShow(!showGifKey) }) { Text(if (showGifKey) "Hide" else "Show") }
                    TextButton(onClick = {
                        val view = Intent(Intent.ACTION_VIEW, Uri.parse(KlipyGifs.PARTNER_KEYS))
                        runCatching { context.startActivity(view) }
                    }) { Text("Get a key") }
                }
            }
        }
    }
}

@Composable
private fun BackupPage(
    vm: MessagesViewModel,
    query: String,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    import: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
) {
    val page = "Backup"
    val rows = listOf(
        visible(query, page, "Export texts", "JSON"),
        visible(query, page, "Import texts", "backup"),
    )
    if (rows.none { it }) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (rows[0]) {
            item {
                ListItem(
                    headlineContent = { Text("Export texts") },
                    supportingContent = { Text("A JSON file of texts. Picture files are not included.") },
                    trailingContent = {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { vm.exportBackup() }
                                    .onSuccess { file ->
                                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                                        val share = Intent(Intent.ACTION_SEND)
                                            .setType("application/json")
                                            .putExtra(Intent.EXTRA_STREAM, uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        context.startActivity(Intent.createChooser(share, "Export texts"))
                                    }
                                    .onFailure { vm.snackbar.showSnackbar(it.message ?: "Could not export") }
                            }
                        }) { Text("Export") }
                    },
                )
            }
        }
        if (rows[1]) {
            item {
                ListItem(
                    headlineContent = { Text("Import texts") },
                    supportingContent = { Text("Adds texts from a backup made by this app. Matching texts are skipped.") },
                    trailingContent = {
                        TextButton(onClick = { import.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Import") }
                    },
                )
            }
        }
    }
}

@Composable
private fun SwipePage(vm: MessagesViewModel, settings: app.messages.data.Persisted, query: String) {
    val page = "Swipe"
    val rows = listOf(
        visible(query, page, "left side", "right side", "swipe a thread"),
        visible(query, page, "Left side", "Unread", "Delete", "None"),
        visible(query, page, "Right side", "Unread", "Delete", "None"),
    )
    if (rows.none { it }) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (rows[0]) {
            item {
                Text(
                    "The left side appears when you swipe a thread to the right. The right side appears when you swipe it to the left. A delete can be undone.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (rows[1]) {
            item {
                Choice(
                    "Left side",
                    listOf("unread" to "Unread", "delete" to "Delete", "none" to "None"),
                    swipeAction(settings.swipeStart, "unread"),
                ) { value ->
                    vm.update { it.copy(swipeStart = value) }
                }
            }
        }
        if (rows[2]) {
            item {
                Choice(
                    "Right side",
                    listOf("unread" to "Unread", "delete" to "Delete", "none" to "None"),
                    swipeAction(settings.swipeEnd, "delete"),
                ) { value ->
                    vm.update { it.copy(swipeEnd = value) }
                }
            }
        }
    }
}

@Composable
private fun AboutPage(query: String) {
    if (!visible(query, "About", "lMessages", "LanMessaging", "LanText", BuildConfig.VERSION_NAME)) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("About") },
                supportingContent = {
                    Text("lMessages ${BuildConfig.VERSION_NAME}. Computer access is LanMessaging, included in this app.")
                },
            )
        }
    }
}

@Composable
private fun RibbonPage(vm: MessagesViewModel, settings: app.messages.data.Persisted, query: String) {
    val page = "Ribbon"
    val actions = resolvedRibbon(settings.ribbon)
    val topChoices = (1..5).map { it.toString() to it.toString() }
    val showTop = visible(query, page, "Move to the top", "selected conversations", "ribbon stays")
    val visibleActions = actions.filter { item ->
        visible(query, page, ribbonLabel(item.id), "show", "hide", "up", "down")
    }
    if (!showTop && visibleActions.isEmpty()) {
        NoMatches()
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (showTop) {
            Text(
                "The ribbon stays with the selected conversations until this many are selected. Then it moves under the selection bar at the top.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Choice(
                "Move to the top after",
                topChoices,
                ribbonTopCount(settings.ribbonTopAt).toString(),
            ) { value ->
                vm.update { it.copy(ribbonTopAt = value.toInt()) }
            }
            Text(
                "Press and hold a row, then drag to change the order.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (query.isBlank()) {
            OrderableRows(
                items = actions,
                label = { ribbonLabel(it.id) },
                onReorder = { next -> vm.update { it.copy(ribbon = next) } },
                onShown = { item, shown ->
                    val next = actions.map { entry -> if (entry.id == item.id) entry.copy(shown = shown) else entry }
                    vm.update { it.copy(ribbon = next) }
                },
                icon = { item -> RibbonRowIcon(item.id) },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(visibleActions, key = { it.id }) { item ->
                    ListItem(
                        leadingContent = { RibbonRowIcon(item.id) },
                        headlineContent = { Text(ribbonLabel(item.id)) },
                        trailingContent = {
                            AppSwitch(
                                checked = item.shown,
                                onCheckedChange = { shown ->
                                    val next = actions.map { entry -> if (entry.id == item.id) entry.copy(shown = shown) else entry }
                                    vm.update { it.copy(ribbon = next) }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RibbonRowIcon(id: String) {
    val res = ribbonGlyph(id) ?: return
    Icon(
        painterResource(res),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = Color.Unspecified,
    )
}

@Composable
private fun FooterPage(vm: MessagesViewModel, settings: app.messages.data.Persisted, query: String) {
    val page = "Footer"
    val actions = resolvedFooter(settings.footer)
    val height = footerHeightDp(settings.footerHeight)
    val showHeight = visible(query, page, "Footer height", "height")
    val visibleActions = actions.filter { item ->
        visible(query, page, footerLabel(item.id), "show", "hide", "icon")
    }
    if (!showHeight && visibleActions.isEmpty()) {
        NoMatches()
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (showHeight) {
            Text(
                "Footer height",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "$height dp",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = height.toFloat(),
                onValueChange = { value ->
                    val snapped = (kotlin.math.round(value / 4f) * 4f).toInt().coerceIn(48, 96)
                    vm.update { it.copy(footerHeight = snapped) }
                },
                valueRange = 48f..96f,
                steps = 11,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                "Press and hold a row, then drag to change the order.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (query.isBlank()) {
            OrderableRows(
                items = actions,
                label = { footerLabel(it.id) },
                onReorder = { next -> vm.update { it.copy(footer = next) } },
                onShown = { item, shown ->
                    val next = actions.map { entry -> if (entry.id == item.id) entry.copy(shown = shown) else entry }
                    vm.update { it.copy(footer = next) }
                },
                icon = { item -> FooterRowIcon(item.id) },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(visibleActions, key = { it.id }) { item ->
                    ListItem(
                        leadingContent = { FooterRowIcon(item.id) },
                        headlineContent = { Text(footerLabel(item.id)) },
                        trailingContent = {
                            AppSwitch(
                                checked = item.shown,
                                onCheckedChange = { shown ->
                                    val next = actions.map { entry -> if (entry.id == item.id) entry.copy(shown = shown) else entry }
                                    vm.update { it.copy(footer = next) }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterRowIcon(id: String) {
    when (id) {
        "search" -> Icon(Icons.Filled.Search, contentDescription = null)
        "settings" -> Icon(Icons.Filled.Settings, contentDescription = null)
        "computer" -> Icon(Icons.Filled.Computer, contentDescription = null)
        "filter" -> Icon(
            painterResource(filterGlyph()),
            contentDescription = null,
            tint = Color.Unspecified,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(vm: MessagesViewModel) {
    val archived by vm.archived.collectAsStateWithLifecycle()
    SimpleList(title = "Archived", onBack = vm::pop) {
        if (archived.isEmpty()) {
            item { Text("No archived conversations", modifier = Modifier.padding(16.dp)) }
        }
        items(archived, key = { it.threadId }) { conversation ->
            ListItem(
                headlineContent = { Text(conversation.title, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(conversation.snippet, maxLines = 1) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = { vm.archive(setOf(conversation.threadId), false) }) { Text("Unarchive") }
                },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedScreen(vm: MessagesViewModel) {
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    var entry by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked") },
                navigationIcon = { IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = entry,
                    onValueChange = { entry = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    label = { Text("Phone number") },
                    trailingIcon = {
                        TextButton(onClick = {
                            if (entry.isNotBlank()) {
                                vm.blockNumber(entry.trim())
                                entry = ""
                            }
                        }) { Text("Block") }
                    },
                )
            }
            if (blocked.isEmpty()) {
                item { Text("No blocked numbers", modifier = Modifier.padding(16.dp)) }
            }
            items(blocked, key = { it }) { number ->
                ListItem(
                    headlineContent = { Text(number) },
                    trailingContent = {
                        IconButton(onClick = { vm.unblock(number) }) { Icon(Icons.Filled.Delete, "Unblock") }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledScreen(vm: MessagesViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    SimpleList(title = "Scheduled", onBack = vm::pop) {
        if (settings.scheduled.isEmpty()) {
            item { Text("No scheduled texts", modifier = Modifier.padding(16.dp)) }
        }
        items(settings.scheduled, key = { it.id }) { item ->
            val whenText = Instant.ofEpochMilli(item.sendAt).atZone(ZoneId.systemDefault()).format(formatter)
            ListItem(
                headlineContent = { Text(item.addresses.joinToString()) },
                supportingContent = { Text("$whenText\n${item.body}") },
                trailingContent = {
                    TextButton(onClick = { vm.cancelSchedule(item.id) }) { Text("Cancel") }
                },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleList(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIconPicker(selectedId: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val selected = LauncherIcons.canonical(selectedId)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("App icon", style = MaterialTheme.typography.titleSmall)
        Text(
            "Launcher icon. The original green bubble stays until you pick another.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LauncherIcons.presets.forEach { preset ->
                val on = preset.id == selected
                val packed = context.getColor(preset.background)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Color(
                                    red = android.graphics.Color.red(packed) / 255f,
                                    green = android.graphics.Color.green(packed) / 255f,
                                    blue = android.graphics.Color.blue(packed) / 255f,
                                ),
                            )
                            .border(
                                width = if (on) 2.dp else 1.dp,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { onPick(preset.id) }
                            .semantics { contentDescription = "${preset.label} app icon" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(preset.foreground),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color.Unspecified,
                        )
                    }
                    Text(
                        preset.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePalettePicker(settings: app.messages.data.Persisted, onPick: (String) -> Unit) {
    val active = resolvedPalette(settings.palette)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Color palette", style = MaterialTheme.typography.titleSmall)
        Text(
            "Same palettes as the computer inbox. Light or dark follows the theme setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeChoices.forEach { choice ->
                val selected = !settings.dynamicColor && choice.id == active
                TextButton(onClick = { onPick(choice.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(choice.swatch),
                        )
                        Text(if (selected) "  ● ${choice.label}" else "  ${choice.label}")
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.Row {
            options.forEach { (value, label) ->
                TextButton(onClick = { onSelect(value) }) {
                    Text(if (value == selected || (value.toFloatOrNull() != null && selected.toFloatOrNull() == value.toFloatOrNull())) "● $label" else label)
                }
            }
        }
    }
}
