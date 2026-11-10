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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import app.messages.data.inboxGroupMode
import app.messages.data.swipeAction
import app.messages.sms.KlipyGifs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val SETTINGS_TAB_LAN_MESSAGING = 11

internal const val SETTINGS_CARD_MIN = 0.34f
internal const val SETTINGS_CARD_MAX = 0.94f
internal const val SETTINGS_CARD_START = 0.68f

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
    val fraction = rememberSaveable { mutableStateOf(SETTINGS_CARD_START) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        val spanPx = with(LocalDensity.current) { maxHeight.toPx() }
        val cap = maxHeight * SETTINGS_CARD_MAX
        val floor = if (280.dp < cap) 280.dp else cap
        val cardHeight = (maxHeight * fraction.value).coerceIn(floor, cap)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .pointerInput(Unit) {
                    // Keep taps on empty card space here, so they do not land on the screen behind.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .dragToResize(spanPx, fraction)
                        .padding(top = 10.dp, bottom = 4.dp)
                        .semantics { contentDescription = "Drag to resize settings" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Settings",
                        modifier = Modifier
                            .weight(1f)
                            .dragToResize(spanPx, fraction)
                            .padding(start = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = vm::closeSettings) { Icon(Icons.Filled.Close, "Close settings") }
                }
                if (!settings.searchAtBottom) searchField()
                PrimaryScrollableTabRow(
                    selectedTabIndex = pager.currentPage,
                    containerColor = Color.Transparent,
                ) {
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
                    modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                    beyondViewportPageCount = 0,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pager,
                        snapAnimationSpec = tween(durationMillis = 180),
                    ),
                ) { page ->
                    val scheme = MaterialTheme.colorScheme
                    val pageScheme = if (contrastRatio(scheme.onSurface, Color.Black) >= 4.5f) {
                        scheme
                    } else {
                        scheme.copy(
                            onSurface = Color.White,
                            onSurfaceVariant = Color(0xFFCAC4D0),
                            onBackground = Color.White,
                        )
                    }
                    MaterialTheme(colorScheme = pageScheme) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                if (settings.searchAtBottom) {
                    HorizontalDivider()
                    searchField()
                }
            }
        }
    }
}

/** Finger moving down (positive [dragPx]) makes the card shorter. */
internal fun settingsCardFraction(current: Float, dragPx: Float, spanPx: Float): Float {
    if (spanPx <= 0f) return current.coerceIn(SETTINGS_CARD_MIN, SETTINGS_CARD_MAX)
    return (current - dragPx / spanPx).coerceIn(SETTINGS_CARD_MIN, SETTINGS_CARD_MAX)
}

internal fun Modifier.dragToResize(spanPx: Float, fraction: MutableState<Float>): Modifier =
    pointerInput(spanPx) {
        detectVerticalDragGestures { _, dragAmount ->
            fraction.value = settingsCardFraction(fraction.value, dragAmount, spanPx)
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
    "BlurtText",
    "About",
)

private fun tabMatches(index: Int, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val title = settingTabs.getOrNull(index).orEmpty()
    if (title.contains(q, ignoreCase = true)) return true
    val blob = when (index) {
        0 -> "computer default sms search at the bottom wifi delete old conversations days duration group conversations read unread received date sms mms pinned delete trash permanently"
        1 -> "theme system light dark color palette dynamic wallpaper alternating threads text size small normal large extra app icon launcher original black cyan dark blue dark purple dark red green light blue orange purple red white yellow fern ocean dusk ember slate sakura meadow nord contrast"
        2 -> "enter key sends delivery reports exact alarms scheduled"
        3 -> "default sim carrier indicator sent received name number time message type sms mms system"
        4 -> "show message text lock screen notification"
        5 -> "keyword filter blocked numbers phrase"
        6 -> "klipy api key partner gif"
        7 -> "export import texts json backup"
        8 -> "right swipe left swipe change mark as read unread delete none thread trash"
        9 -> "ribbon pin unpin mark as read mark as unread delete block archive show hide drag move to the top selected arrangement"
        10 -> "footer height search settings computer filter trash icon show hide"
        11 -> "blurttext wifi port paired devices web access networks listen"
        12 -> "version blurttext computer access github apache license apk download"
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

/** List rows use the card color. A filled row container made the page alternate black and grey. */
@Composable
private fun SettingsListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = headlineContent,
        modifier = modifier,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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
        visible(query, page, "BlurtText", "Computer", "Wi-Fi", "Type on a computer"),
        visible(query, page, "Default SMS app", "Not the default SMS app", "New texts"),
        visible(query, page, "Search at the bottom", "search field"),
        visible(query, page, "Group conversations", "Read and unread", "Received date", "SMS", "MMS", "Pinned"),
        visible(query, page, "Delete old conversations", "Trash", "Pinned", "days"),
        visible(query, page, "Older than", "Days", "duration"),
        visible(query, page, "Delete trash", "Permanently deletes"),
        visible(query, page, "In trash longer than", "stays in Trash"),
    )
    var daysText by remember { mutableStateOf(settings.autoTrashDays.coerceIn(1, 3650).toString()) }
    var trashDaysText by remember { mutableStateOf(settings.autoDeleteTrashDays.coerceIn(1, 3650).toString()) }
    if (rows.none { it }) {
        NoMatches()
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (rows[0]) {
            item {
                SettingsListItem(
                    headlineContent = { Text("BlurtText") },
                    supportingContent = { Text("Type on a computer over your Wi-Fi. Nothing is uploaded.") },
                    leadingContent = { Icon(Icons.Filled.Computer, null) },
                    trailingContent = { TextButton(onClick = onOpenLanMessaging) { Text("Open") } },
                )
            }
        }
        if (rows[1]) {
            item {
                SettingsListItem(
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
                SettingsListItem(
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
        if (rows[3]) {
            item {
                InboxGroupPicker(inboxGroupMode(settings.inboxGroup)) { value ->
                    vm.update { it.copy(inboxGroup = value) }
                }
            }
        }
        if (rows[4]) {
            item {
                SettingsListItem(
                    headlineContent = { Text("Delete old conversations") },
                    supportingContent = {
                        Text("Moves a conversation to Trash when its newest message is older than the number of days below. Pinned conversations stay in the inbox.")
                    },
                    trailingContent = {
                        AppSwitch(checked = settings.autoTrashEnabled, onCheckedChange = { checked ->
                            vm.update { it.copy(autoTrashEnabled = checked) }
                        })
                    },
                )
            }
        }
        if (rows[5]) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Older than", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Number of days before a conversation moves to Trash.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(4)
                            daysText = digits
                            val days = digits.toIntOrNull()
                            if (days != null && days in 1..3650) vm.update { it.copy(autoTrashDays = days) }
                        },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("days") },
                    )
                }
            }
        }
        if (rows[6]) {
            item {
                SettingsListItem(
                    headlineContent = { Text("Delete trash") },
                    supportingContent = {
                        Text("Permanently deletes a conversation after it has been in Trash longer than the number of days below.")
                    },
                    trailingContent = {
                        AppSwitch(checked = settings.autoDeleteTrashEnabled, onCheckedChange = { checked ->
                            vm.update { it.copy(autoDeleteTrashEnabled = checked) }
                        })
                    },
                )
            }
        }
        if (rows[7]) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("In trash longer than", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Number of days a conversation stays in Trash.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = trashDaysText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(4)
                            trashDaysText = digits
                            val days = digits.toIntOrNull()
                            if (days != null && days in 1..3650) vm.update { it.copy(autoDeleteTrashDays = days) }
                        },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("days") },
                    )
                }
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
        visible(query, page, "Color palette", paletteNames),
        visible(query, page, "App icon", "Launcher", "Original", "Black", "Cyan", "Green", "White"),
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
                ThemePalettePicker(settings) { id ->
                    vm.update { it.copy(palette = id, dynamicColor = false) }
                }
            }
        }
        if (rows[2]) {
            item {
                AppIconPicker(settings.launcherIcon) { id ->
                    vm.update { it.copy(launcherIcon = id) }
                }
            }
        }
        if (rows[3]) {
            item {
                SettingsListItem(
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
                SettingsListItem(
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
                SettingsListItem(
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
                SettingsListItem(
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
                SettingsListItem(
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
                    SettingsListItem(
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
                SettingsListItem(
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
    SettingsListItem(
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
            SettingsListItem(
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
    val exportGeneration = remember { mutableIntStateOf(0) }
    val exportShare = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val generation = exportGeneration.intValue
        if (generation != 0) vm.releaseBackupExport(generation)
    }
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
                SettingsListItem(
                    headlineContent = { Text("Export texts") },
                    supportingContent = { Text("A JSON file of texts. Picture files are not included.") },
                    trailingContent = {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { vm.exportBackup() }
                                    .onSuccess { exported ->
                                        exportGeneration.intValue = exported.generation
                                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", exported.file)
                                        val share = Intent(Intent.ACTION_SEND)
                                            .setType("application/json")
                                            .putExtra(Intent.EXTRA_STREAM, uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        runCatching {
                                            exportShare.launch(Intent.createChooser(share, "Export texts"))
                                        }.onFailure {
                                            vm.releaseBackupExport(exported.generation, delayMs = 0)
                                            vm.snackbar.showSnackbar(it.message ?: "Could not export")
                                        }
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
                SettingsListItem(
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

private val swipeChoices = listOf(
    "unread" to "Mark as read/unread",
    "delete" to "Delete",
    "none" to "None",
)

private fun swipeActionLabel(action: String): String = swipeChoices.firstOrNull { it.first == action }?.second ?: "None"

@Composable
private fun SwipePage(vm: MessagesViewModel, settings: app.messages.data.Persisted, query: String) {
    val page = "Swipe"
    val rows = listOf(
        visible(query, page, "right swipe", "left swipe", "change", "mark as read"),
        visible(query, page, "Right swipe", "Delete", "Mark as read/unread", "None"),
        visible(query, page, "Left swipe", "Delete", "Mark as read/unread", "None"),
    )
    if (rows.none { it }) {
        NoMatches()
        return
    }
    var picking by remember { mutableStateOf<String?>(null) }
    val startAction = swipeAction(settings.swipeStart, "delete")
    val endAction = swipeAction(settings.swipeEnd, "unread")
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "A delete moves it to Trash and can be undone.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SwipeActionCard("Right swipe", startAction, revealOnLeft = true) { picking = "start" }
        }
        item {
            SwipeActionCard("Left swipe", endAction, revealOnLeft = false) { picking = "end" }
        }
    }
    val which = picking
    if (which != null) {
        val start = which == "start"
        val current = if (start) startAction else endAction
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (start) "Right swipe" else "Left swipe") },
            text = {
                Column {
                    swipeChoices.forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                vm.update { saved ->
                                    if (start) saved.copy(swipeStart = value) else saved.copy(swipeEnd = value)
                                }
                                picking = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (value == current) "● $label" else label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SwipeActionCard(title: String, action: String, revealOnLeft: Boolean, onChange: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    swipeActionLabel(action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChange) {
                Text("Change", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        SwipePreviewGraphic(action, revealOnLeft)
    }
}

private val SwipeDeletePreview = Color(0xFFE46962)
private val SwipeUnreadPreview = Color(0xFF1A73E8)

@Composable
private fun SwipePreviewGraphic(action: String, revealOnLeft: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (revealOnLeft) SwipePreviewBlock(action)
        SwipePreviewSketch(Modifier.weight(1f))
        if (!revealOnLeft) SwipePreviewBlock(action)
    }
}

@Composable
private fun SwipePreviewBlock(action: String) {
    if (action == "none") return
    val delete = action == "delete"
    Box(
        Modifier
            .width(84.dp)
            .height(76.dp)
            .background(if (delete) SwipeDeletePreview else SwipeUnreadPreview),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (delete) Icons.Filled.Delete else Icons.Filled.MarkEmailUnread,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun SwipePreviewSketch(modifier: Modifier) {
    val bar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Row(modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bar),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Box(Modifier.width(108.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(bar))
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .width(72.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bar),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxGroupPicker(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Group conversations", style = MaterialTheme.typography.titleMedium)
        Text(
            "Headings in the inbox. Search stays one list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "none" to "None",
                "read" to "Read and unread",
                "date" to "Received date",
                "type" to "SMS and MMS",
                "pinned" to "Pinned",
            ).forEach { (value, label) ->
                TextButton(onClick = { onSelect(value) }) {
                    Text(if (selected == value) "● $label" else label)
                }
            }
        }
    }
}

@Composable
private fun AboutPage(query: String) {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    val license = remember {
        context.resources.openRawResource(app.messages.R.raw.apache_license).bufferedReader().use { it.readText() }
    }
    if (!visible(
            query,
            "About",
            "BlurtText",
            BuildConfig.VERSION_NAME,
            "GitHub",
            "Apache",
            "license",
            "APK",
        )
    ) {
        NoMatches()
        return
    }
    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("Close") }
            },
            title = { Text("Apache License 2.0") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(license, style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SettingsListItem(
                headlineContent = { Text("About") },
                supportingContent = {
                    Text("BlurtText ${BuildConfig.VERSION_NAME}. Type on a computer over Wi-Fi from this phone.")
                },
            )
        }
        item {
            SettingsListItem(
                headlineContent = { Text("GitHub") },
                supportingContent = { Text("https://github.com/crome1394/BlurtText") },
                leadingContent = {
                    Icon(painterResource(app.messages.R.drawable.ic_git), contentDescription = "Git")
                },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/crome1394/BlurtText")),
                    )
                },
            )
        }
        item {
            SettingsListItem(
                headlineContent = { Text("License") },
                supportingContent = { Text("Apache License 2.0") },
                modifier = Modifier.clickable { showLicense = true },
            )
        }
        item {
            SettingsListItem(
                headlineContent = { Text("Test APK") },
                supportingContent = { Text("Download the latest build from GitHub") },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/crome1394/BlurtText/releases/latest")),
                    )
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
                "Drag the handle to change the order.",
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
                    SettingsListItem(
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
                "Drag the handle to change the order.",
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
                    SettingsListItem(
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
        "trash" -> Icon(Icons.Filled.Delete, contentDescription = null)
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
            "Launcher icon. Cyan is used until you pick another.",
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
