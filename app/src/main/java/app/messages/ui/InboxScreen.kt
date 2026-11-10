package app.messages.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.R
import app.messages.data.Conversation
import app.messages.data.InboxListEntry
import app.messages.data.InboxListFilter
import app.messages.data.inboxEntries
import app.messages.data.RibbonItem
import app.messages.data.addressesMatchBlocked
import app.messages.data.footerButtonDp
import app.messages.data.footerGlyphDp
import app.messages.data.footerHeightDp
import app.messages.data.swipeCommitPx
import app.messages.data.swipeReadUnreadLabel
import app.messages.data.swipeRevealReady
import app.messages.data.resolvedFooter
import app.messages.data.SearchHit
import app.messages.data.formatTimestamp
import app.messages.data.unreadBadge
import app.messages.data.resolvedRibbon
import app.messages.data.ribbonLabel
import app.messages.data.ribbonTopCount
import app.messages.data.swipeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(vm: MessagesViewModel, onRequestRole: () -> Unit) {
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val listFilter by vm.listFilter.collectAsStateWithLifecycle()
    val filterThreadId by vm.filterThreadId.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val isDefault by vm.isDefault.collectAsStateWithLifecycle()
    val error by vm.loadError.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchOpen = searching || query.isNotBlank()
    val unreadMessages = inbox.sumOf { it.unread }
    val footerLabel = inboxFooterLabel(selection.size, unreadMessages)
    val rows = if (query.trim().length >= 2) {
        emptyList()
    } else {
        filteredInbox(inbox, archived, blocked, listFilter, filterThreadId)
    }
    val filterLabel = filterCaption(listFilter, inbox, archived, filterThreadId)
    val footerItems = resolvedFooter(settings.footer).filter { it.shown }
    val footerHeight = footerHeightDp(settings.footerHeight)
    val ribbonItems = resolvedRibbon(settings.ribbon).filter { it.shown }
    val topAt = ribbonTopCount(settings.ribbonTopAt)
    val ribbonAtTop = selection.isNotEmpty() && selection.size >= topAt
    val unpinSelection = selection.isNotEmpty() && selection.all { id -> inbox.any { it.threadId == id && it.pinned } }
    val startAction = swipeAction(settings.swipeStart, "unread")
    val endAction = swipeAction(settings.swipeEnd, "delete")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var person by remember { mutableStateOf<PersonCard?>(null) }
    var group by remember { mutableStateOf<Conversation?>(null) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val number = pendingNumber
        pendingNumber = null
        if (uri != null && !number.isNullOrBlank()) vm.addNumberToContact(uri, number)
    }
    val writeContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingNumber
        if (granted && !number.isNullOrBlank()) contactPicker.launch(null)
        else {
            pendingNumber = null
            if (!granted) vm.note("Contacts permission is needed to choose a contact")
        }
    }
    fun chooseContact(number: String) {
        if (number.isBlank()) {
            vm.note("This conversation has no phone number")
            return
        }
        pendingNumber = number
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) contactPicker.launch(null) else writeContacts.launch(Manifest.permission.WRITE_CONTACTS)
    }
    fun addContact(number: String) {
        if (number.isBlank()) return
        context.startActivity(
            Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
            },
        )
    }
    fun openPerson(number: String, title: String) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) { vm.contactUri(number) }
            if (uri == null) {
                person = PersonCard(title.ifBlank { number }, number)
                return@launch
            }
            val opened = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
            if (!opened) person = PersonCard(title.ifBlank { number }, number)
        }
    }
    fun openAvatar(conversation: Conversation) {
        val numbers = conversation.addresses.filter { it.isNotBlank() }
        when {
            numbers.size > 1 -> group = conversation
            numbers.size == 1 -> openPerson(numbers.first(), conversation.title)
            else -> person = PersonCard(conversation.title.ifBlank { "Contact" }, "")
        }
    }
    fun closeSearch() {
        vm.query.value = ""
        searching = false
        keyboard?.hide()
    }
    fun runRibbon(id: String) {
        when (id) {
            "read" -> vm.markRead(selection)
            "unread" -> vm.markUnread(selection)
            "delete" -> vm.deleteSelection()
            "block" -> confirmBlock = true
            "archive" -> vm.archive(selection, true)
            "pin" -> vm.pinSelection(selection, pinned = !unpinSelection)
        }
    }
    BackHandler(enabled = filterOpen || selection.isNotEmpty() || searchOpen) {
        when {
            filterOpen -> filterOpen = false
            selection.isNotEmpty() -> vm.clearSelection()
            else -> closeSearch()
        }
    }

    val canvas = MaterialTheme.colorScheme.mailCanvas()
    val ink = MaterialTheme.colorScheme.mailInk()
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = canvas,
        scrolledContainerColor = canvas,
        navigationIconContentColor = ink,
        titleContentColor = ink,
        actionIconContentColor = ink,
    )
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = canvas,
        contentColor = ink,
        bottomBar = {
            Column(Modifier.background(canvas)) {
                HorizontalDivider()
                if (filterOpen) {
                    InboxFilterMenu(
                        inbox = inbox,
                        archived = archived,
                        active = listFilter,
                        contactId = filterThreadId,
                        onAll = {
                            filterOpen = false
                            vm.setListFilter(InboxListFilter.All)
                        },
                        onFilter = { filter ->
                            filterOpen = false
                            vm.setListFilter(filter)
                        },
                        onContact = { threadId ->
                            filterOpen = false
                            vm.filterContact(threadId)
                        },
                    )
                }
                InboxFooter(
                    label = footerLabel,
                    filterCaption = filterLabel,
                    height = footerHeight,
                    items = footerItems,
                    searchOpen = searchOpen,
                    filterActive = listFilter != InboxListFilter.All,
                    onSearch = {
                        if (searchOpen) closeSearch() else searching = true
                    },
                    onSettings = vm::showSettings,
                    onComputer = { vm.showSettings(SETTINGS_TAB_LAN_MESSAGING) },
                    onFilter = { filterOpen = !filterOpen },
                    onTrash = vm::showTrash,
                )
            }
        },
        topBar = {
            if (!ribbonAtTop) {
                TopAppBar(
                    colors = barColors,
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Menu, "Menu") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("BlurtText") }, onClick = {
                                    menu = false
                                    vm.showSettings(SETTINGS_TAB_LAN_MESSAGING)
                                }, leadingIcon = { Icon(Icons.Filled.Computer, null) })
                                DropdownMenuItem(text = { Text("Archived") }, onClick = {
                                    menu = false
                                    vm.showArchive()
                                }, leadingIcon = { Icon(Icons.Filled.Archive, null) })
                                DropdownMenuItem(text = { Text("Trash") }, onClick = {
                                    menu = false
                                    vm.showTrash()
                                }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                                DropdownMenuItem(text = { Text("Blocked") }, onClick = {
                                    menu = false
                                    vm.showBlocked()
                                }, leadingIcon = { Icon(Icons.Filled.Block, null) })
                                DropdownMenuItem(text = { Text("Scheduled") }, onClick = {
                                    menu = false
                                    vm.showScheduled()
                                })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = {
                                    menu = false
                                    vm.showSettings()
                                }, leadingIcon = { Icon(Icons.Filled.Settings, null) })
                            }
                        }
                    },
                    title = {
                        if (searchOpen) {
                            InboxSearchField(
                                query = query,
                                onQuery = { vm.query.value = it },
                                onClear = ::closeSearch,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Surface(
                                onClick = { searching = true },
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.mailSearch(),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "Search messages",
                                        modifier = Modifier.padding(start = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    colors = barColors,
                    title = { Text("${selection.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selection.isEmpty() && !searchOpen) {
                ExtendedFloatingActionButton(
                    onClick = { vm.showCompose() },
                    icon = { Icon(Icons.Filled.Edit, "New message") },
                    text = { Text("Compose") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ribbonAtTop) {
                ThreadRibbon(ribbonItems, unpinSelection, ::runRibbon)
            }
            if (!isDefault) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Set BlurtText as the default SMS app to send and receive here.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onRequestRole) { Text("Set default") }
                    }
                }
            }
            if (!settings.swipeHintDismissed && query.isBlank()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            swipeHint(startAction, endAction),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { vm.update { it.copy(swipeHintDismissed = true) } }) { Text("Got it") }
                    }
                }
            }
            when {
                error != null && inbox.isEmpty() && query.isBlank() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(error ?: "", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                query.trim().length >= 2 -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    ) {
                        items(results, key = { "${it.threadId}:${it.timestamp}:${it.snippet}" }) { hit ->
                            SearchRow(hit) { vm.showThread(hit.threadId) }
                        }
                        if (results.isEmpty()) {
                            item { EmptyText("No matches") }
                        }
                    }
                }
                rows.isEmpty() -> {
                    Column(Modifier.fillMaxSize()) {
                        InboxHeading(listFilter, filterLabel)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (listFilter == InboxListFilter.All) "No conversations" else "Nothing in this filter",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        item { InboxHeading(listFilter, filterLabel) }
                        val ribbonAnchor = if (!ribbonAtTop && selection.isNotEmpty()) {
                            rows.firstOrNull { it.threadId in selection }?.threadId
                        } else {
                            null
                        }
                        val entries = inboxEntries(rows, settings.inboxGroup, System.currentTimeMillis())
                        items(entries, key = { entry ->
                            when (entry) {
                                is InboxListEntry.Heading -> "section:${entry.title}"
                                is InboxListEntry.Row -> entry.conversation.threadId
                            }
                        }) { entry ->
                            when (entry) {
                                is InboxListEntry.Heading -> InboxSectionHeading(entry.title)
                                is InboxListEntry.Row -> {
                                    val conversation = entry.conversation
                                    if (conversation.threadId == ribbonAnchor) {
                                        ThreadRibbon(ribbonItems, unpinSelection, ::runRibbon)
                                    }
                                    SwipeConversation(
                                        conversation = conversation,
                                        selected = conversation.threadId in selection,
                                        striped = settings.stripedInbox && entry.stripe % 2 == 1,
                                        swipeEnabled = selection.isEmpty(),
                                        startAction = startAction,
                                        endAction = endAction,
                                        onLongPress = { vm.startSelect(conversation.threadId) },
                                        onClick = {
                                            if (selection.isEmpty()) vm.showThread(conversation.threadId)
                                            else vm.toggleSelect(conversation.threadId)
                                        },
                                        onAvatar = { openAvatar(conversation) },
                                        onPin = { vm.togglePin(conversation.threadId) },
                                        onUnread = { vm.swipeUnread(conversation.threadId) },
                                        onRead = { vm.markRead(setOf(conversation.threadId)) },
                                        onDelete = { vm.swipeDelete(conversation.threadId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block selected numbers?") },
            text = { Text("New texts from them are archived and not notified. You can unblock them later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    vm.blockSelection()
                }) { Text("Block") }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } },
        )
    }
    person?.let { card ->
        AlertDialog(
            onDismissRequest = { person = null },
            title = { Text(card.title.ifBlank { "Contact" }) },
            text = {
                Column {
                    if (card.number.isNotBlank()) Text(card.number)
                    if (card.number.isNotBlank()) {
                        TextButton(onClick = {
                            val number = card.number
                            person = null
                            addContact(number)
                        }) { Text("Add contact") }
                        TextButton(onClick = {
                            val number = card.number
                            person = null
                            chooseContact(number)
                        }) { Text("Choose a contact") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { person = null }) { Text("Close") } },
        )
    }
    group?.let { conversation ->
        AlertDialog(
            onDismissRequest = { group = null },
            title = { Text(conversation.title) },
            text = {
                Column {
                    conversation.addresses.filter { it.isNotBlank() }.forEach { number ->
                        TextButton(
                            onClick = {
                                group = null
                                openPerson(number, number)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(number) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { group = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InboxSectionHeading(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.mailInk(),
    )
}

@Composable
private fun InboxHeading(filter: InboxListFilter, filterLabel: String) {
    val title = if (filter == InboxListFilter.All) "Inbox" else filterLabel.ifBlank { "Inbox" }
    Text(
        title,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.mailInk(),
    )
}

private val ThreadCardShape = RoundedCornerShape(20.dp)

@Composable
private fun SearchRow(hit: SearchHit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .shadow(8.dp, ThreadCardShape)
            .clip(ThreadCardShape)
            .background(MaterialTheme.colorScheme.mailCard())
            .combinedClick(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                hit.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.mailInk(),
            )
            Text(formatTimestamp(hit.timestamp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            hit.snippet,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeConversation(
    conversation: Conversation,
    selected: Boolean,
    striped: Boolean,
    swipeEnabled: Boolean,
    startAction: String,
    endAction: String,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onAvatar: () -> Unit,
    onPin: () -> Unit,
    onUnread: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    var offsetPx by remember(conversation.threadId) { mutableFloatStateOf(0f) }
    var lastSwipe by remember(conversation.threadId) { mutableLongStateOf(0L) }
    val onDeleteNow by rememberUpdatedState(onDelete)
    val onUnreadNow by rememberUpdatedState(onUnread)
    val onReadNow by rememberUpdatedState(onRead)
    val isUnread by rememberUpdatedState(conversation.unread > 0)
    val view = LocalView.current
    val revealed = if (offsetPx > 0f) startAction else endAction
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .shadow(8.dp, ThreadCardShape)
            .clip(ThreadCardShape),
    ) {
        if (offsetPx != 0f && revealed != "none") {
            val delete = revealed == "delete"
            val mailLabel = swipeReadUnreadLabel(isUnread)
            val color = if (delete) Color(0xFFB3261E) else Color(0xFF1B6B4A)
            Box(
                Modifier
                    .matchParentSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (offsetPx < 0f) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        when {
                            delete -> Icons.Filled.Delete
                            isUnread -> Icons.Filled.MarkEmailRead
                            else -> Icons.Filled.MarkEmailUnread
                        },
                        contentDescription = if (delete) "Delete" else mailLabel,
                        tint = Color.White,
                    )
                    Text(if (delete) "Delete" else mailLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        ConversationRow(
            conversation = conversation,
            selected = selected,
            striped = striped,
            onClick = onClick,
            onLongPress = onLongPress,
            onAvatar = onAvatar,
            onPin = onPin,
            modifier = Modifier
                .pointerInput(swipeEnabled, conversation.threadId, startAction, endAction) {
                    if (!swipeEnabled) return@pointerInput
                    val slop = viewConfiguration.touchSlop
                    // The row used to move with the finger while the measured distance stayed
                    // near zero, so the color showed and the release never counted.
                    fun clamped(travelX: Float): Float {
                        var next = travelX
                        if (startAction == "none") next = next.coerceAtMost(0f)
                        if (endAction == "none") next = next.coerceAtLeast(0f)
                        val width = size.width.toFloat()
                        return if (width > 0f) next.coerceIn(-width, width) else next
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val originX = down.position.x
                        val originY = down.position.y
                        var locked: Boolean? = null
                        // A drag toward a disabled side stays inside the full-width row, so the
                        // clickable would treat it as a tap or a long-press unless we consume it.
                        var eatDisabled = false
                        var travel = 0f
                        var released = false
                        var passed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val travelX = change.position.x - originX
                                val travelY = change.position.y - originY
                                if (!change.pressed) {
                                    if (locked == true) {
                                        travel = clamped(travelX)
                                        released = true
                                        change.consume()
                                    } else if (eatDisabled) {
                                        change.consume()
                                    }
                                    break
                                }
                                if (locked == null) {
                                    val towardDisabled = (travelX > 0f && startAction == "none") || (travelX < 0f && endAction == "none")
                                    val pastSlop = abs(travelX) > slop * 3f || abs(travelY) > slop * 3f
                                    if (change.isConsumed && abs(travelY) > slop) {
                                        locked = false
                                    } else if (
                                        !towardDisabled && swipeRevealReady(
                                            elapsedMs = change.uptimeMillis - down.uptimeMillis,
                                            travelX = travelX,
                                            travelY = travelY,
                                            slop = slop,
                                        )
                                    ) {
                                        locked = true
                                    } else if (pastSlop && towardDisabled && abs(travelX) >= abs(travelY)) {
                                        locked = false
                                        eatDisabled = true
                                    } else if (pastSlop && abs(travelY) > abs(travelX)) {
                                        locked = false
                                    }
                                }
                                if (eatDisabled) {
                                    change.consume()
                                    travel = 0f
                                    offsetPx = 0f
                                    continue
                                }
                                if (locked == false) break
                                if (locked == true) {
                                    change.consume()
                                    travel = clamped(travelX)
                                    offsetPx = travel
                                    val widthNow = size.width.toFloat()
                                    val line = if (widthNow > 0f) swipeCommitPx(widthNow) else 80.dp.toPx()
                                    val past = abs(travel) >= line
                                    if (past && !passed) view.performHapticFeedback(swipeThresholdHaptic())
                                    passed = past
                                }
                            }
                        } finally {
                            val dragged = if (released) travel else 0f
                            val width = size.width.toFloat()
                            // Read the width when the finger lifts. A threshold captured on the
                            // first frame can see a width of 0 and then ignore every swipe.
                            val threshold = if (width > 0f) swipeCommitPx(width) else 80.dp.toPx()
                            offsetPx = 0f
                            val action = if (dragged > 0f) startAction else endAction
                            if (released && action != "none" && abs(dragged) >= threshold) {
                                val now = System.currentTimeMillis()
                                if (now - lastSwipe > 700) {
                                    lastSwipe = now
                                    view.performHapticFeedback(swipeCommitHaptic())
                                    when {
                                        action == "delete" -> onDeleteNow()
                                        isUnread -> onReadNow()
                                        else -> onUnreadNow()
                                    }
                                }
                            }
                        }
                    }
                }
                .offset { IntOffset(offsetPx.roundToInt(), 0) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAvatar: () -> Unit,
    onPin: () -> Unit,
    striped: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val unread = conversation.unread > 0
    val card = if (striped) MaterialTheme.colorScheme.mailCardStripe() else MaterialTheme.colorScheme.mailCard()
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else card
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .semantics { contentDescription = "Contact details" }
                .combinedClickable(onClick = onAvatar, onLongClick = onLongPress),
        ) {
            Avatar(conversation.title, conversation.photoUri, size = 48.dp)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.mailInk(),
                )
                Text(
                    formatTimestamp(conversation.timestamp),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unread) {
                    val badge = unreadBadge(conversation.unread)
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .semantics { contentDescription = if (badge.isEmpty()) "Unread" else "$badge unread" },
                    )
                }
            }
            val preview = if (conversation.draft.isNotBlank()) "Draft: ${conversation.draft}" else conversation.snippet
            Text(
                preview.ifBlank { " " },
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                color = if (conversation.draft.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else if (unread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        val pinned = conversation.pinned
        Box(
            Modifier
                .align(Alignment.Bottom)
                .size(32.dp)
                .clickable(onClick = onPin)
                .semantics { contentDescription = if (pinned) "Unpin" else "Pin" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.OutlinedPushPin,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClick(onClick: () -> Unit): Modifier =
    combinedClickable(onClick = onClick)

@Composable
private fun ThreadRibbon(
    actions: List<RibbonItem>,
    unpin: Boolean,
    onAction: (String) -> Unit,
) {
    if (actions.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            val label = ribbonLabel(action.id, unpin && action.id == "pin")
            val glyph = ribbonGlyph(action.id)
            TextButton(onClick = { onAction(action.id) }) {
                if (glyph != null) {
                    Icon(
                        painterResource(glyph),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(label)
            }
        }
    }
}

@Composable
internal fun ribbonGlyph(id: String): Int? {
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    return when (id) {
        "read" -> if (light) R.drawable.ic_ribbon_read_black else R.drawable.ic_ribbon_read_white
        "unread" -> if (light) R.drawable.ic_ribbon_unread_black else R.drawable.ic_ribbon_unread_white
        "delete" -> if (light) R.drawable.ic_ribbon_delete_black else R.drawable.ic_ribbon_delete_white
        "block" -> if (light) R.drawable.ic_ribbon_block_black else R.drawable.ic_ribbon_block_white
        "pin" -> if (light) R.drawable.ic_ribbon_pin_black else R.drawable.ic_ribbon_pin_white
        "archive" -> if (light) R.drawable.ic_ribbon_archive_black else R.drawable.ic_ribbon_archive_white
        else -> null
    }
}

@Composable
internal fun filterGlyph(): Int {
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    return if (light) R.drawable.ic_footer_filter_black else R.drawable.ic_footer_filter_white
}

private fun swipeThresholdHaptic(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    else HapticFeedbackConstants.CLOCK_TICK

private fun swipeCommitHaptic(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
    else HapticFeedbackConstants.LONG_PRESS

private fun swipeHint(start: String, end: String): String {
    fun side(action: String, direction: String): String? = when (action) {
        "delete" -> "Swipe $direction to delete"
        "unread" -> "Swipe $direction to mark read or unread"
        else -> null
    }
    val parts = listOfNotNull(side(start, "right"), side(end, "left"))
    val lead = if (parts.isEmpty()) "Swipe actions are off" else parts.joinToString(". ")
    return "$lead. Scrolling will not delete a thread. A delete moves it to Trash and can be undone."
}

private data class PersonCard(val title: String, val number: String)

private fun inboxFooterLabel(selected: Int, unread: Int): String {
    if (selected > 0) return if (selected == 1) "1 selected" else "$selected selected"
    return if (unread == 1) "1 unread" else "$unread unread"
}

@Composable
private fun InboxFooter(
    label: String,
    filterCaption: String,
    height: Int,
    items: List<RibbonItem>,
    searchOpen: Boolean,
    filterActive: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onComputer: () -> Unit,
    onFilter: () -> Unit,
    onTrash: () -> Unit,
) {
    val button = footerButtonDp(height)
    val glyph = footerGlyphDp(height)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 8.dp, end = 8.dp),
        ) {
            Text(
                label,
                modifier = Modifier.semantics { contentDescription = label },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.mailInk(),
            )
            if (filterCaption.isNotEmpty()) {
                Text(
                    filterCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            items.forEach { item ->
                when (item.id) {
                    "search" -> FooterAction(button, onSearch) {
                        Icon(
                            Icons.Filled.Search,
                            if (searchOpen) "Close search" else "Search",
                            modifier = Modifier.size(glyph.dp),
                            tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    "settings" -> FooterAction(button, onSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            "Settings",
                            modifier = Modifier.size(glyph.dp),
                            tint = MaterialTheme.colorScheme.mailInk(),
                        )
                    }
                    "computer" -> FooterAction(button, onComputer) {
                        Icon(
                            Icons.Filled.Computer,
                            "BlurtText",
                            modifier = Modifier.size(glyph.dp),
                            tint = MaterialTheme.colorScheme.mailInk(),
                        )
                    }
                    "filter" -> FooterAction(button, onFilter) {
                        Icon(
                            painterResource(filterGlyph()),
                            "Filter",
                            modifier = Modifier.size(glyph.dp),
                            tint = if (filterActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    }
                    "trash" -> FooterAction(button, onTrash) {
                        Icon(
                            Icons.Filled.Delete,
                            "Trash",
                            modifier = Modifier.size(glyph.dp),
                            tint = MaterialTheme.colorScheme.mailInk(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FooterAction(
    button: Int,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(top = 2.dp).size(button.dp),
    ) { icon() }
}

@Composable
private fun InboxFilterMenu(
    inbox: List<Conversation>,
    archived: List<Conversation>,
    active: InboxListFilter,
    contactId: Long,
    onAll: () -> Unit,
    onFilter: (InboxListFilter) -> Unit,
    onContact: (Long) -> Unit,
) {
    val contacts = remember(inbox, archived) {
        (inbox + archived)
            .distinctBy { it.threadId }
            .sortedBy { it.title.lowercase() }
            .take(40)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            FilterChoice("All", active == InboxListFilter.All, onAll)
            FilterChoice("Unread", active == InboxListFilter.Unread) { onFilter(InboxListFilter.Unread) }
            FilterChoice("Read", active == InboxListFilter.Read) { onFilter(InboxListFilter.Read) }
            FilterChoice("Archived", active == InboxListFilter.Archived) { onFilter(InboxListFilter.Archived) }
            FilterChoice("Blocked", active == InboxListFilter.Blocked) { onFilter(InboxListFilter.Blocked) }
            if (contacts.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Contacts",
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            contacts.forEach { conversation ->
                FilterChoice(
                    conversation.title.ifBlank { "Contact" },
                    active == InboxListFilter.Contact && conversation.threadId == contactId,
                ) { onContact(conversation.threadId) }
            }
        }
    }
}

@Composable
private fun FilterChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (selected) "● $label" else label,
            modifier = Modifier.fillMaxWidth(),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun filteredInbox(
    inbox: List<Conversation>,
    archived: List<Conversation>,
    blocked: List<String>,
    filter: InboxListFilter,
    contactId: Long,
): List<Conversation> {
    if (filter == InboxListFilter.Contact && contactId > 0) {
        return (inbox + archived).filter { it.threadId == contactId }
    }
    return when (filter) {
        InboxListFilter.Unread -> inbox.filter { it.unread > 0 }
        InboxListFilter.Read -> inbox.filter { it.unread == 0 }
        InboxListFilter.Archived -> archived
        InboxListFilter.Blocked -> (inbox + archived).filter { addressesMatchBlocked(it.addresses, blocked) }
        else -> inbox
    }
}

private fun filterCaption(
    filter: InboxListFilter,
    inbox: List<Conversation>,
    archived: List<Conversation>,
    contactId: Long,
): String = when (filter) {
    InboxListFilter.All -> ""
    InboxListFilter.Unread -> "Unread"
    InboxListFilter.Read -> "Read"
    InboxListFilter.Archived -> "Archived"
    InboxListFilter.Blocked -> "Blocked"
    InboxListFilter.Contact -> {
        (inbox + archived).firstOrNull { it.threadId == contactId }?.title?.ifBlank { "Contact" } ?: "Contact"
    }
}

@Composable
private fun InboxSearchField(
    query: String,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150)
        focus.requestFocus()
        keyboard?.show()
    }
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.focusRequester(focus).height(52.dp),
        placeholder = { Text("Search messages") },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear search") }
        },
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.mailSearch(),
            unfocusedContainerColor = MaterialTheme.colorScheme.mailSearch(),
        ),
    )
}
