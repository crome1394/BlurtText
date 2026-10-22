package app.messages.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import app.messages.data.InboxListFilter
import app.messages.data.RibbonItem
import app.messages.data.addressesMatchBlocked
import app.messages.data.footerHeightDp
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

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                HorizontalDivider()
                if (searchOpen) {
                    InboxSearchField(
                        query = query,
                        onQuery = { vm.query.value = it },
                        onClear = ::closeSearch,
                    )
                }
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
                )
            }
        },
        topBar = {
            if (!ribbonAtTop) {
                TopAppBar(
                    title = { Text(stringResource(R.string.messages_app_name)) },
                    actions = {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("LanMessaging") }, onClick = {
                                menu = false
                                vm.showSettings(SETTINGS_TAB_LAN_MESSAGING)
                            }, leadingIcon = { Icon(Icons.Filled.Computer, null) })
                            DropdownMenuItem(text = { Text("Archived") }, onClick = {
                                menu = false
                                vm.showArchive()
                            }, leadingIcon = { Icon(Icons.Filled.Archive, null) })
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
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selection.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selection.isEmpty()) {
                FloatingActionButton(onClick = { vm.showCompose() }) {
                    Icon(Icons.Filled.Edit, "New message")
                }
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
                            "Set lMessages as the default SMS app to send and receive here.",
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
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { "${it.threadId}:${it.timestamp}:${it.snippet}" }) { hit ->
                            SearchRow(hit) { vm.showThread(hit.threadId) }
                        }
                        if (results.isEmpty()) {
                            item { EmptyText("No matches") }
                        }
                    }
                }
                rows.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (listFilter == InboxListFilter.All) "No conversations" else "Nothing in this filter",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        val ribbonAnchor = if (!ribbonAtTop && selection.isNotEmpty()) {
                            rows.firstOrNull { it.threadId in selection }?.threadId
                        } else {
                            null
                        }
                        itemsIndexed(rows, key = { _, conversation -> conversation.threadId }) { index, conversation ->
                            if (conversation.threadId == ribbonAnchor) {
                                ThreadRibbon(ribbonItems, unpinSelection, ::runRibbon)
                            }
                            SwipeConversation(
                                conversation = conversation,
                                selected = conversation.threadId in selection,
                                striped = settings.stripedInbox && index % 2 == 1,
                                swipeEnabled = selection.isEmpty(),
                                startAction = startAction,
                                endAction = endAction,
                                onLongPress = { vm.startSelect(conversation.threadId) },
                                onClick = {
                                    if (selection.isEmpty()) vm.showThread(conversation.threadId)
                                    else vm.toggleSelect(conversation.threadId)
                                },
                                onAvatar = { openAvatar(conversation) },
                                onUnread = { vm.swipeUnread(conversation.threadId) },
                                onDelete = { vm.swipeDelete(conversation.threadId) },
                            )
                            HorizontalDivider()
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
private fun SearchRow(hit: SearchHit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClick(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(hit.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(formatTimestamp(hit.timestamp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(hit.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onUnread: () -> Unit,
    onDelete: () -> Unit,
) {
    var offsetPx by remember(conversation.threadId) { mutableFloatStateOf(0f) }
    var lastSwipe by remember(conversation.threadId) { mutableLongStateOf(0L) }
    val revealed = if (offsetPx > 0f) startAction else endAction
    Box(Modifier.fillMaxWidth()) {
        if (offsetPx != 0f && revealed != "none") {
            val delete = revealed == "delete"
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
                        if (delete) Icons.Filled.Delete else Icons.Filled.MarkEmailUnread,
                        contentDescription = if (delete) "Delete" else "Mark unread",
                        tint = Color.White,
                    )
                    Text(if (delete) "Delete" else "Unread", color = Color.White, fontWeight = FontWeight.SemiBold)
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
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .pointerInput(swipeEnabled, conversation.threadId, startAction, endAction) {
                    if (!swipeEnabled) return@pointerInput
                    val slop = viewConfiguration.touchSlop
                    val threshold = maxOf(size.width * 0.62f, 180.dp.toPx())
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var locked: Boolean? = null
                        var armed = false
                        // A drag toward a disabled side stays inside the full-width row, so the
                        // clickable would treat it as a tap or a long-press unless we consume it.
                        var eatDisabled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (eatDisabled || locked == true) change.consume()
                                armed = locked == true
                                break
                            }
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalX += dx
                            totalY += dy
                            if (locked == null) {
                                val towardDisabled = (totalX > 0f && startAction == "none") || (totalX < 0f && endAction == "none")
                                val pastSlop = abs(totalX) > slop * 2.5f || abs(totalY) > slop * 2.5f
                                if (change.isConsumed && abs(totalY) > slop) {
                                    locked = false
                                } else if (pastSlop && abs(totalX) > abs(totalY) * 2f && !towardDisabled) {
                                    locked = true
                                } else if (pastSlop && towardDisabled && abs(totalX) >= abs(totalY)) {
                                    locked = false
                                    eatDisabled = true
                                } else if (pastSlop && abs(totalY) > abs(totalX)) {
                                    locked = false
                                }
                            }
                            if (eatDisabled) {
                                change.consume()
                                offsetPx = 0f
                                continue
                            }
                            if (locked == false) break
                            if (locked == true) {
                                change.consume()
                                var next = offsetPx + dx
                                if (startAction == "none") next = next.coerceAtMost(0f)
                                if (endAction == "none") next = next.coerceAtLeast(0f)
                                offsetPx = next.coerceIn(-size.width.toFloat(), size.width.toFloat())
                            }
                        }
                        val dragged = offsetPx
                        offsetPx = 0f
                        val action = if (dragged > 0f) startAction else endAction
                        if (armed && action != "none" && abs(dragged) >= threshold) {
                            val now = System.currentTimeMillis()
                            if (now - lastSwipe > 700) {
                                lastSwipe = now
                                if (action == "delete") onDelete() else onUnread()
                            }
                        }
                    }
                },
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
    striped: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val unread = conversation.unread > 0
    val surface = MaterialTheme.colorScheme.surface
    val stripe = if (striped) {
        Color(surface.red * 0.90f, surface.green * 0.90f, surface.blue * 0.90f, surface.alpha)
    } else {
        surface
    }
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else stripe
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .semantics { contentDescription = "Contact details" }
                .combinedClickable(onClick = onAvatar, onLongClick = onLongPress),
        ) {
            Avatar(conversation.title, conversation.photoUri)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                )
                if (conversation.pinned) {
                    Icon(
                    Icons.Filled.PushPin,
                    "Pinned",
                    modifier = Modifier.padding(end = 6.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                }
                Text(
                    formatTimestamp(conversation.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val preview = if (conversation.draft.isNotBlank()) "Draft: ${conversation.draft}" else conversation.snippet
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview.ifBlank { " " },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (conversation.draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val badge = unreadBadge(conversation.unread)
                if (badge.isNotEmpty()) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .semantics { contentDescription = "$badge unread" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
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

private fun swipeHint(start: String, end: String): String {
    fun side(action: String, direction: String): String? = when (action) {
        "delete" -> "Swipe $direction to delete"
        "unread" -> "Swipe $direction to mark unread"
        else -> null
    }
    val parts = listOfNotNull(side(start, "right"), side(end, "left"))
    val lead = if (parts.isEmpty()) "Swipe actions are off" else parts.joinToString(". ")
    return "$lead. Scrolling will not delete a thread. A delete can be undone."
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
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                label,
                modifier = Modifier.semantics { contentDescription = label },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        items.forEach { item ->
            when (item.id) {
                "search" -> IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Filled.Search,
                        if (searchOpen) "Close search" else "Search",
                        tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                "settings" -> IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                "computer" -> IconButton(onClick = onComputer) { Icon(Icons.Filled.Computer, "LanMessaging") }
                "filter" -> IconButton(onClick = onFilter) {
                    Icon(
                        painterResource(filterGlyph()),
                        "Filter",
                        tint = if (filterActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                }
            }
        }
    }
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
            if (contacts.isNotEmpty()) HorizontalDivider()
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
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.focusRequester(focus),
        placeholder = { Text("Search messages") },
        singleLine = true,
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear search") }
            }
        },
    )
}
