package app.messages.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import app.messages.data.MessageLinks
import app.messages.data.VerificationCodes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.data.ChatMessage
import app.messages.data.Delivery
import app.messages.data.ScheduledMessage
import app.messages.data.SimLine
import app.messages.data.chosenSub
import app.messages.data.composeStartText
import app.messages.data.draftKey
import app.messages.data.messageCaption
import app.messages.data.messageParty
import app.messages.data.mimeForPath
import app.messages.data.preferredSub
import app.messages.data.simCaption
import app.messages.data.simDetail
import app.messages.sms.BubbleReaction
import app.messages.sms.KlipyGifs
import app.messages.sms.TapbackSymbol
import app.messages.sms.reactionDescription
import app.messages.sms.reactionLayout
import app.messages.sms.tapbackBody
import app.messages.sms.tapbackSymbols
import app.messages.data.formatFullStamp
import app.messages.data.formatMessageStamp
import app.messages.data.formatTimestamp
import app.messages.data.sameRecipients
import app.messages.data.schedulePickerSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(vm: MessagesViewModel, threadId: Long) {
    val context = LocalContext.current
    val messages by vm.messages(threadId).collectAsStateWithLifecycle(emptyList())
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val sims by vm.sims.collectAsStateWithLifecycle()
    var pickedSub by remember(threadId) { mutableStateOf<Int?>(null) }
    val activeSub = chosenSub(sims, pickedSub ?: preferredSub(settings.defaultSubId, vm.systemSmsSub()))
    val conversation = vm.conversation(threadId)
    val addresses = conversation?.addresses?.takeIf { it.isNotEmpty() } ?: remember(threadId) { vm.addresses(threadId) }
    var text by remember(threadId) { mutableStateOf(vm.draft(draftKey(threadId))) }
    val latestText by rememberUpdatedState(text)
    var attachment by remember(threadId) { mutableStateOf(vm.attachment(draftKey(threadId))) }
    val latestAttachment by rememberUpdatedState(attachment)
    var menu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var preview by remember { mutableStateOf<Long?>(null) }
    var scheduling by remember { mutableStateOf(false) }
    var editingId by remember(threadId) { mutableStateOf<String?>(null) }
    var schedulingReplace by remember(threadId) { mutableStateOf<String?>(null) }
    val scheduled = settings.scheduled.filter { sameRecipients(it.addresses, addresses) }
    val listState = rememberLazyListState()
    val ascending = remember(messages) {
        val ordered = app.lantext.sms.orderEchoes(
            messages,
            { it.key },
            { it.threadId.toString() },
            { it.body },
            { it.timestamp },
            { it.incoming },
        )
        val reactions = reactionLayout(ordered)
        ordered.filter { it.key !in reactions.hiddenKeys }.map { message ->
            message to reactions.byKey[message.key].orEmpty()
        }
    }
    var stickToBottom by remember(threadId) { mutableStateOf(true) }
    var chooseNumber by remember { mutableStateOf<String?>(null) }
    var askLocation by remember { mutableStateOf(false) }
    var locationLink by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val number = chooseNumber
        chooseNumber = null
        if (uri != null && !number.isNullOrBlank()) vm.addNumberToContact(uri, number)
    }
    val writeContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = chooseNumber
        if (granted && !number.isNullOrBlank()) pickContact.launch(null)
        else {
            chooseNumber = null
            if (!granted) vm.note("Contacts permission is needed to choose a contact")
        }
    }
    fun startLocation() {
        requestDeviceLocation(context) { link ->
            scope.launch {
                if (link == null) vm.note("Location is unavailable")
                else {
                    locationLink = link
                    askLocation = true
                }
            }
        }
    }
    val fineLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLocation() else vm.note("Location permission is needed to send where you are")
    }

    val shareContact = rememberContactShare(load = vm::contactCard, onError = vm::note) { card ->
        text = if (text.isBlank()) card else text.trimEnd() + "\n" + card
    }
    DisposableEffect(threadId) {
        vm.setActiveThread(threadId)
        onDispose {
            vm.saveComposer(draftKey(threadId), latestText, latestAttachment)
            vm.clearActiveThread()
        }
    }
    LaunchedEffect(listState, threadId) {
        var dragged = false
        var ownScroll = false
        snapshotFlow {
            val info = listState.layoutInfo
            val count = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()
            ThreadTail(count, last?.size ?: 0, listState.tailGap(), listState.isScrollInProgress)
        }.distinctUntilChanged().collect { tail ->
            if (tail.scrolling) {
                if (!ownScroll) dragged = true
                return@collect
            }
            if (ownScroll) {
                ownScroll = false
            } else if (dragged) {
                stickToBottom = tail.gap < 120
                dragged = false
            }
            if (!stickToBottom || tail.gap <= 2) return@collect
            ownScroll = true
            var stalled = 0
            repeat(8) {
                if (!stickToBottom || listState.tailGap() <= 2) return@collect
                val before = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                listState.revealTail()
                if (listState.tailGap() <= 2) return@collect
                val after = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                if (before == after) {
                    stalled++
                    if (stalled >= 2) return@collect
                    delay(40)
                } else {
                    stalled = 0
                }
            }
        }
    }

    fun sendNow() {
        if (addresses.isEmpty() || sending) return
        val body = text
        val path = attachment
        text = ""
        attachment = null
        stickToBottom = true
        vm.send(addresses, body, path, activeSub, threadId)
    }

    val threadTitle = conversation?.title ?: addresses.joinToString().ifBlank { "Conversation" }
    val dial = addresses.firstOrNull().orEmpty()
    var contactCard by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contactGroup by remember { mutableStateOf(false) }
    fun showContact(number: String, title: String) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) { vm.contactUri(number) }
            val opened = uri != null && runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
            if (!opened) contactCard = title.ifBlank { number } to number
        }
    }
    fun openThreadContact() {
        val numbers = addresses.filter { it.isNotBlank() }
        when {
            numbers.size > 1 -> contactGroup = true
            numbers.size == 1 -> showContact(numbers.first(), threadTitle)
            else -> vm.note("This conversation has no phone number")
        }
    }
    val page = MaterialTheme.colorScheme.mailCanvas()
    val ink = MaterialTheme.colorScheme.mailInk()
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = page,
        contentColor = ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = page,
                    scrolledContainerColor = page,
                    navigationIconContentColor = ink,
                    titleContentColor = ink,
                    actionIconContentColor = ink,
                ),
                title = {},
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (conversation?.pinned == true) "Unpin" else "Pin") }, onClick = {
                            menu = false
                            vm.togglePin(threadId)
                        }, leadingIcon = { Icon(Icons.Filled.PushPin, null) })
                        DropdownMenuItem(text = { Text("Send my location") }, onClick = {
                            menu = false
                            if (addresses.isEmpty()) {
                                vm.note("This conversation has no phone number")
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) startLocation() else fineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }, leadingIcon = { Icon(Icons.Filled.LocationOn, null) })
                        DropdownMenuItem(text = { Text("Send contact") }, onClick = {
                            menu = false
                            shareContact()
                        }, leadingIcon = { Icon(Icons.Filled.Person, null) })
                        DropdownMenuItem(text = { Text("Archive") }, onClick = {
                            menu = false
                            vm.archive(setOf(threadId), true)
                            vm.pop()
                        }, leadingIcon = { Icon(Icons.Filled.Archive, null) })
                        DropdownMenuItem(text = { Text("Mark unread") }, onClick = {
                            menu = false
                            vm.markUnread(setOf(threadId))
                            vm.pop()
                        })
                        DropdownMenuItem(text = { Text("Block") }, onClick = {
                            menu = false
                            addresses.forEach { vm.blockNumber(it) }
                            vm.archive(setOf(threadId), true)
                            vm.pop()
                        }, leadingIcon = { Icon(Icons.Filled.Block, null) })
                        if (dial.isNotBlank()) {
                            DropdownMenuItem(text = { Text("Add contact") }, onClick = {
                                menu = false
                                context.startActivity(
                                    Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, dial)
                                    },
                                )
                            }, leadingIcon = { Icon(Icons.Filled.PersonAdd, null) })
                        }
                        DropdownMenuItem(text = { Text("Choose a contact") }, onClick = {
                            menu = false
                            val number = addresses.firstOrNull().orEmpty()
                            if (number.isBlank()) {
                                vm.note("This conversation has no phone number")
                            } else {
                                chooseNumber = number
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_CONTACTS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) pickContact.launch(null) else writeContacts.launch(Manifest.permission.WRITE_CONTACTS)
                            }
                        }, leadingIcon = { Icon(Icons.Filled.PersonAdd, null) })
                        DropdownMenuItem(text = { Text("Export text") }, onClick = {
                            menu = false
                            val share = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, vm.exportThreadText(threadId))
                            context.startActivity(Intent.createChooser(share, "Export conversation"))
                        }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                        DropdownMenuItem(text = { Text("Delete conversation") }, onClick = {
                            menu = false
                            vm.swipeDelete(threadId)
                            vm.pop()
                        }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                    }
                },
            )
        },
        bottomBar = {
            Column {
                ScheduledStrip(
                    items = scheduled,
                    editingId = editingId,
                    draft = text,
                    onEdit = { item ->
                        editingId = item.id
                        text = item.body
                    },
                    onSave = { item ->
                        if (item.sendAt <= System.currentTimeMillis()) {
                            schedulingReplace = item.id
                            scheduling = true
                        } else {
                            vm.schedule(addresses, text, item.sendAt, activeSub, item.id)
                            text = ""
                            editingId = null
                        }
                    },
                    onChangeTime = { item ->
                        editingId = item.id
                        if (text.isBlank()) text = item.body
                        schedulingReplace = item.id
                        scheduling = true
                    },
                    onCancel = { item ->
                        vm.cancelSchedule(item.id)
                        if (editingId == item.id) editingId = null
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(end = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        vm.archive(setOf(threadId), true)
                        vm.pop()
                    }) { Icon(Icons.Filled.Archive, "Archive") }
                    IconButton(onClick = {
                        vm.swipeDelete(threadId)
                        vm.pop()
                    }) { Icon(Icons.Filled.Delete, "Delete") }
                    IconButton(onClick = {
                        vm.markUnread(setOf(threadId))
                        vm.pop()
                    }) { Icon(Icons.Filled.MarkEmailUnread, "Mark unread") }
                    if (dial.isNotBlank() && !dial.contains('@')) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dial")))
                        }) { Icon(Icons.Filled.Call, "Call") }
                    }
                }
                Composer(
                    text = text,
                    onText = { text = it },
                    attachment = attachment,
                    onAttachment = { path ->
                        val previous = attachment
                        if (previous != null && previous != path) vm.releaseAttachment(previous)
                        attachment = path
                        vm.saveComposer(draftKey(threadId), text, path)
                    },
                    copyAttachment = vm::copyAttachment,
                    takeVideo = vm::takeVideo,
                    hasGifKey = settings.gifApiKey.isNotBlank(),
                    searchGifs = vm::searchGifs,
                    attachGif = vm::attachGif,
                    onOpenSettings = vm::showSettings,
                    onLocation = {
                        if (addresses.isEmpty()) {
                            vm.note("This conversation has no phone number")
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startLocation() else fineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    onContact = shareContact,
                    onSchedule = {
                        schedulingReplace = editingId
                        scheduling = true
                    },
                    sending = sending,
                    enterSends = settings.enterSends,
                    sims = sims,
                    subId = activeSub,
                    onSub = { pickedSub = it },
                    onSend = { sendNow() },
                    scheduleEnabled = text.isNotBlank() && attachment == null,
                    onError = vm::note,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = ::openThreadContact)
                .semantics { contentDescription = "Contact details" }
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                threadTitle,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = ink,
            )
            if (addresses.isNotEmpty()) {
                Text(
                    addresses.joinToString(),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.mailCard(),
            contentColor = ink,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(ascending, key = { _, row -> row.first.key }) { index, row ->
                val message = row.first
                val reactions = row.second
                val previous = ascending.getOrNull(index - 1)?.first
                if (previous == null || message.timestamp - previous.timestamp > 30 * 60 * 1000) {
                    Text(
                        formatMessageStamp(message.timestamp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                val caption = messageCaption(
                    incoming = message.incoming,
                    party = messageParty(message.incoming, message.senderName, message.address),
                    time = formatMessageStamp(message.timestamp),
                    mms = message.mmsId != null,
                    sim = simDetail(message.subId, sims),
                    showDirection = settings.detailDirection,
                    showParty = settings.detailParty,
                    showTime = settings.detailTime,
                    showType = settings.detailType,
                    showSim = settings.detailSim,
                )
                val indicator = if (settings.showSimIndicator) simCaption(message.subId, sims) else ""
                MessageBubble(
                    message = message,
                    reactions = reactions,
                    group = addresses.size > 1,
                    indicator = indicator,
                    caption = caption,
                    loadPart = vm::loadPart,
                    onLongPress = { selected = message },
                    onRetry = { if (message.status == Delivery.FAILED && message.attachments.isEmpty()) vm.retry(message) },
                    onImage = { preview = it },
                    onOpenLink = { openMessageLink(context, it) { vm.note(it) } },
                    onCopyCode = { code ->
                        val copied = copyVerificationCode(context, code)
                        vm.note(if (copied) "Code copied" else "Could not copy that code")
                    },
                    onOpenPart = { part, mime ->
                        val uri = vm.stagePart(part, mime) ?: return@MessageBubble
                        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(view) }
                    },
                )
            }
        }
        }
        }
    }

    selected?.let { message ->
        val marks = ascending.firstOrNull { it.first.key == message.key }?.second.orEmpty()
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(if (message.incoming) message.senderName else "You") },
            text = {
                Column {
                    TapbackRow(marks) { symbol, remove ->
                        val body = tapbackBody(message, symbol, remove)
                        selected = null
                        when {
                            body == null -> vm.note("Nothing to react to")
                            addresses.isEmpty() -> vm.note("No recipients on this conversation")
                            else -> vm.react(addresses, body, activeSub, threadId)
                        }
                    }
                    Text(formatFullStamp(message.timestamp))
                    if (message.status.isNotBlank()) Text(message.status)
                    if (message.address.isNotBlank()) Text(message.address, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        selected = null
                        vm.markUnread(setOf(threadId))
                        vm.pop()
                    }) { Text("Mark as unread") }
                    CopyCodeButton(message.body) { code ->
                        val copied = copyVerificationCode(context, code)
                        vm.note(if (copied) "Code copied" else "Could not copy that code")
                        selected = null
                    }
                    val links = MessageLinks.find(message.body)
                    if (links.size == 1) {
                        TextButton(onClick = {
                            openMessageLink(context, links.first().url) { vm.note(it) }
                            selected = null
                        }) { Text("Open link") }
                    } else {
                        links.take(4).forEach { link ->
                            TextButton(onClick = {
                                openMessageLink(context, link.url) { vm.note(it) }
                                selected = null
                            }) { Text("Open ${MessageLinks.host(link.url)}") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Close") }
            },
            dismissButton = {
                Row {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.body))
                        selected = null
                    }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                    IconButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message.body)
                        context.startActivity(Intent.createChooser(share, "Share"))
                        selected = null
                    }) { Icon(Icons.Filled.Share, "Share") }
                    IconButton(onClick = {
                        vm.deleteMessage(message)
                        selected = null
                    }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            },
        )
    }

    contactCard?.let { (title, number) ->
        AlertDialog(
            onDismissRequest = { contactCard = null },
            title = { Text(title.ifBlank { "Contact" }) },
            text = {
                Column {
                    if (number.isNotBlank()) Text(number)
                    if (number.isNotBlank()) {
                        TextButton(onClick = {
                            contactCard = null
                            context.startActivity(
                                Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                    putExtra(ContactsContract.Intents.Insert.PHONE, number)
                                },
                            )
                        }) { Text("Add contact") }
                        TextButton(onClick = {
                            contactCard = null
                            chooseNumber = number
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_CONTACTS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) pickContact.launch(null) else writeContacts.launch(Manifest.permission.WRITE_CONTACTS)
                        }) { Text("Choose a contact") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { contactCard = null }) { Text("Close") } },
        )
    }
    if (contactGroup) {
        AlertDialog(
            onDismissRequest = { contactGroup = false },
            title = { Text(threadTitle) },
            text = {
                Column {
                    addresses.filter { it.isNotBlank() }.forEach { number ->
                        TextButton(
                            onClick = {
                                contactGroup = false
                                showContact(number, number)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(number) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { contactGroup = false }) { Text("Close") } },
        )
    }

    preview?.let { partId ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { preview = null },
            ) {
                PartImage(
                    partId,
                    vm::loadPart,
                    Modifier.fillMaxSize().systemBarsPadding(),
                    zoom = true,
                )
                IconButton(
                    onClick = { preview = null },
                    modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding(),
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
            }
        }
    }

    if (scheduling) {
        ScheduleDialog(
            onDismiss = {
                scheduling = false
                schedulingReplace = null
            },
            onConfirm = { at ->
                scheduling = false
                if (addresses.isNotEmpty() && text.isNotBlank()) {
                    vm.schedule(addresses, text, at, activeSub, schedulingReplace)
                    text = ""
                    editingId = null
                }
                schedulingReplace = null
            },
        )
    }
    if (askLocation && locationLink != null) {
        AlertDialog(
            onDismissRequest = { askLocation = false },
            title = { Text("Add your location?") },
            text = { Text("The map link is added to this message. Send it when you are ready.\n\n${locationLink.orEmpty()}") },
            confirmButton = {
                TextButton(onClick = {
                    val link = locationLink
                    askLocation = false
                    if (!link.isNullOrBlank()) {
                        text = if (text.isBlank()) link else text.trimEnd() + "\n" + link
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { askLocation = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(vm: MessagesViewModel, initialAddresses: List<String>, initialBody: String, initialAttachment: String?) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val sims by vm.sims.collectAsStateWithLifecycle()
    var pickedSub by remember { mutableStateOf<Int?>(null) }
    val activeSub = chosenSub(sims, pickedSub ?: preferredSub(settings.defaultSubId, vm.systemSmsSub()))
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var recipients by remember { mutableStateOf(initialAddresses) }
    var entry by remember { mutableStateOf("") }
    val blankCompose = initialBody.isBlank() && initialAddresses.isEmpty() && initialAttachment == null
    var text by remember { mutableStateOf(composeStartText(initialBody, initialAddresses, vm.draft(draftKey(initialAddresses)))) }
    val latestText by rememberUpdatedState(text)
    val latestRecipients by rememberUpdatedState(recipients)
    var attachment by remember {
        mutableStateOf(if (blankCompose) null else initialAttachment ?: vm.attachment(draftKey(initialAddresses)))
    }
    val latestAttachment by rememberUpdatedState(attachment)
    var scheduling by remember { mutableStateOf(false) }
    var askLocation by remember { mutableStateOf(false) }
    var locationLink by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf<String?>(null) }
    var schedulingReplace by remember { mutableStateOf<String?>(null) }
    val pendingAddresses = (recipients + splitRecipients(entry)).distinct()
    val scheduled = settings.scheduled.filter { sameRecipients(it.addresses, pendingAddresses) }
    val toFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (blankCompose) {
            val key = draftKey(emptyList())
            val stale = vm.attachment(key)
            vm.saveComposer(key, "", null)
            if (!stale.isNullOrBlank()) vm.releaseAttachment(stale)
        }
        delay(150)
        runCatching { toFocus.requestFocus() }
        keyboard?.show()
    }
    val shareContact = rememberContactShare(load = vm::contactCard, onError = vm::note) { card ->
        text = if (text.isBlank()) card else text.trimEnd() + "\n" + card
    }
    fun startLocation() {
        requestDeviceLocation(context) { link ->
            scope.launch {
                if (link == null) vm.note("Location is unavailable")
                else {
                    locationLink = link
                    askLocation = true
                }
            }
        }
    }
    val fineLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLocation() else vm.note("Location permission is needed to send where you are")
    }
    DisposableEffect(Unit) {
        onDispose {
            if (latestRecipients.isEmpty()) {
                val dropped = latestAttachment
                vm.saveComposer(draftKey(emptyList()), "", null)
                if (!dropped.isNullOrBlank()) vm.releaseAttachment(dropped)
            } else {
                vm.saveComposer(draftKey(latestRecipients), latestText, latestAttachment)
                if (initialAddresses.isEmpty()) vm.saveComposer(draftKey(emptyList()), "", null)
            }
        }
    }
    fun sendNow() {
        val addresses = (recipients + splitRecipients(entry)).distinct()
        if (addresses.isEmpty() || sending) return
        val body = text
        val path = attachment
        text = ""
        attachment = null
        vm.send(addresses, body, path, activeSub)
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
                        .semantics { contentDescription = "Drag to resize new message" },
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
                        "New message",
                        modifier = Modifier
                            .weight(1f)
                            .dragToResize(spanPx, fraction)
                            .padding(start = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = vm::pop) { Icon(Icons.Filled.Close, "Close") }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    OutlinedTextField(
                        value = entry,
                        onValueChange = {
                            entry = it
                            vm.recipientQuery.value = it
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(toFocus),
                        label = { Text("To") },
                        singleLine = true,
                        supportingText = {
                            if (recipients.isNotEmpty()) Text(recipients.joinToString())
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val added = splitRecipients(entry)
                            if (added.isNotEmpty()) {
                                recipients = (recipients + added).distinct()
                                entry = ""
                                vm.recipientQuery.value = ""
                            }
                        }),
                    )
                    if (entry.isNotBlank()) {
                        suggestions.forEach { hit ->
                            TextButton(onClick = {
                                recipients = (recipients + hit.number).distinct()
                                entry = ""
                                vm.recipientQuery.value = ""
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(hit.name.ifBlank { hit.number }, fontWeight = FontWeight.Medium)
                                    if (hit.name.isNotBlank()) Text(hit.number, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (recipients.size > 1) {
                        Text(
                            "More than one person sends as a picture message, so mobile data has to be on.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    recipients.forEach { number ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(number, modifier = Modifier.weight(1f))
                            IconButton(onClick = { recipients = recipients - number }) { Icon(Icons.Filled.Close, "Remove") }
                        }
                    }
                }
                ScheduledStrip(
                    items = scheduled,
                    editingId = editingId,
                    draft = text,
                    onEdit = { item ->
                        editingId = item.id
                        text = item.body
                    },
                    onSave = { item ->
                        if (item.sendAt <= System.currentTimeMillis()) {
                            schedulingReplace = item.id
                            scheduling = true
                        } else {
                            vm.schedule(pendingAddresses, text, item.sendAt, activeSub, item.id)
                            text = ""
                            editingId = null
                        }
                    },
                    onChangeTime = { item ->
                        editingId = item.id
                        if (text.isBlank()) text = item.body
                        schedulingReplace = item.id
                        scheduling = true
                    },
                    onCancel = { item ->
                        vm.cancelSchedule(item.id)
                        if (editingId == item.id) editingId = null
                    },
                )
                Composer(
                    text = text,
                    onText = { text = it },
                    attachment = attachment,
                    onAttachment = { path ->
                        val previous = attachment
                        if (previous != null && previous != path) vm.releaseAttachment(previous)
                        attachment = path
                        vm.saveComposer(draftKey(pendingAddresses), text, path)
                    },
                    copyAttachment = vm::copyAttachment,
                    takeVideo = vm::takeVideo,
                    hasGifKey = settings.gifApiKey.isNotBlank(),
                    searchGifs = vm::searchGifs,
                    attachGif = vm::attachGif,
                    onOpenSettings = vm::showSettings,
                    onLocation = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) startLocation() else fineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    onContact = shareContact,
                    onSchedule = {
                        schedulingReplace = editingId
                        scheduling = true
                    },
                    sending = sending,
                    enterSends = settings.enterSends,
                    sims = sims,
                    subId = activeSub,
                    onSub = { pickedSub = it },
                    onSend = { sendNow() },
                    scheduleEnabled = text.isNotBlank() && attachment == null && (recipients.isNotEmpty() || entry.isNotBlank()),
                    onError = vm::note,
                )
            }
        }
    }
    if (scheduling) {
        ScheduleDialog(
            onDismiss = {
                scheduling = false
                schedulingReplace = null
            },
            onConfirm = { at ->
                val addresses = (recipients + splitRecipients(entry)).distinct()
                scheduling = false
                if (addresses.isNotEmpty() && text.isNotBlank()) {
                    vm.schedule(addresses, text, at, activeSub, schedulingReplace)
                    text = ""
                    editingId = null
                }
                schedulingReplace = null
            },
        )
    }
    if (askLocation && locationLink != null) {
        AlertDialog(
            onDismissRequest = { askLocation = false },
            title = { Text("Add your location?") },
            text = { Text("The map link is added to this message. Send it when you are ready.\n\n${locationLink.orEmpty()}") },
            confirmButton = {
                TextButton(onClick = {
                    val link = locationLink
                    askLocation = false
                    if (!link.isNullOrBlank()) {
                        text = if (text.isBlank()) link else text.trimEnd() + "\n" + link
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { askLocation = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScheduledStrip(
    items: List<ScheduledMessage>,
    editingId: String?,
    draft: String,
    onEdit: (ScheduledMessage) -> Unit,
    onSave: (ScheduledMessage) -> Unit,
    onChangeTime: (ScheduledMessage) -> Unit,
    onCancel: (ScheduledMessage) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        items.forEach { item ->
            val editing = item.id == editingId
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Scheduled ${formatTimestamp(item.sendAt)}", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (editing && draft.isNotBlank()) draft else item.body,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row {
                        if (editing) {
                            TextButton(onClick = { onSave(item) }, enabled = draft.isNotBlank()) { Text("Save") }
                        } else {
                            TextButton(onClick = { onEdit(item) }) { Text("Edit") }
                        }
                        TextButton(onClick = { onChangeTime(item) }) { Text("Change time") }
                        TextButton(onClick = { onCancel(item) }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    attachment: String?,
    onAttachment: (String?) -> Unit,
    copyAttachment: (Uri) -> String,
    takeVideo: (Uri) -> String,
    hasGifKey: Boolean,
    searchGifs: suspend (String) -> List<KlipyGifs.Hit>,
    attachGif: suspend (KlipyGifs.Hit) -> String,
    onOpenSettings: () -> Unit,
    onSchedule: () -> Unit,
    sending: Boolean,
    enterSends: Boolean,
    sims: List<SimLine>,
    subId: Int?,
    onSub: (Int) -> Unit,
    onSend: () -> Unit,
    scheduleEnabled: Boolean,
    onError: (String) -> Unit,
    onLocation: () -> Unit,
    onContact: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachNow = rememberUpdatedState(onAttachment)
    val reportError = rememberUpdatedState(onError)
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    var plus by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(ComposerPanel.None) }
    var emojiCategory by remember { mutableIntStateOf(0) }
    var emojiQuery by remember { mutableStateOf("") }
    val panelHeight = remember { mutableStateOf(COMPOSER_PANEL_START_DP.dp) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var shotRequest by remember { mutableIntStateOf(0) }
    var awaitCamera by remember { mutableStateOf(false) }
    val simScroll = rememberScrollState()
    LaunchedEffect(text) {
        if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { copyAttachment(uri) } }
            result.onSuccess { attachNow.value(it) }
                .onFailure { reportError.value(it.message ?: "Could not attach that picture") }
        }
    }
    val photoFallback = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { copyAttachment(uri) } }
            result.onSuccess { attachNow.value(it) }
                .onFailure { reportError.value(it.message ?: "Could not attach that picture") }
        }
    }
    fun openPhotos() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (runCatching { photos.launch(request) }.isFailure) {
            if (runCatching { photoFallback.launch("image/*") }.isFailure) {
                onError("No photo picker is available")
            }
        }
    }
    val videos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { takeVideo(uri) } }
            result.onSuccess { attachNow.value(it) }
                .onFailure { reportError.value(it.message ?: "Could not attach that video") }
        }
    }
    val videoFallback = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { takeVideo(uri) } }
            result.onSuccess { attachNow.value(it) }
                .onFailure { reportError.value(it.message ?: "Could not attach that video") }
        }
    }
    fun openVideos() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        if (runCatching { videos.launch(request) }.isFailure) {
            if (runCatching { videoFallback.launch("video/*") }.isFailure) {
                onError("No video picker is available")
            }
        }
    }
    val camera = rememberLauncherForActivityResult(remember { CapturePhoto() }) { ok ->
        val uri = cameraUri
        if (ok && uri != null) {
            runCatching { onAttachment(copyAttachment(uri)) }
                .onFailure { onError(it.message ?: "Could not attach that picture") }
        }
    }
    fun queueShot() {
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
            if (!file.createNewFile() && !file.exists()) {
                onError("Could not prepare a photo file")
                return
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            cameraUri = uri
            shotRequest += 1
        } catch (t: Throwable) {
            onError(t.message ?: "Could not open the camera")
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!awaitCamera) return@rememberLauncherForActivityResult
        awaitCamera = false
        if (!granted) onError("Camera permission is needed to take a picture")
        queueShot()
    }
    LaunchedEffect(shotRequest) {
        if (shotRequest == 0) return@LaunchedEffect
        val uri = cameraUri ?: return@LaunchedEffect
        delay(80)
        if (!CapturePhoto.hasCamera(context)) {
            onError("No camera app is available")
            return@LaunchedEffect
        }
        runCatching { camera.launch(uri) }
            .onFailure { onError(it.message ?: "No camera app is available") }
    }
    fun insertAtCursor(glyph: String) {
        val start = minOf(field.selection.start, field.selection.end).coerceIn(0, field.text.length)
        val end = maxOf(field.selection.start, field.selection.end).coerceIn(0, field.text.length)
        val next = field.text.replaceRange(start, end, glyph)
        val cursor = start + glyph.length
        field = TextFieldValue(next, TextRange(cursor))
        onText(next)
    }
    fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            queueShot()
        } else {
            awaitCamera = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        color = MaterialTheme.colorScheme.mailCanvas(),
        contentColor = MaterialTheme.colorScheme.mailInk(),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            if (sims.size > 1 && subId != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(simScroll).padding(bottom = 6.dp),
                ) {
                    sims.forEach { sim ->
                        val label = simDetail(sim.id, sims)
                        TextButton(onClick = { onSub(sim.id) }) {
                            Text(if (sim.id == subId) "● $label" else label, maxLines = 1)
                        }
                    }
                }
            }
            attachment?.let { path ->
                val kind = mimeForPath(path)
                val video = kind.startsWith("video/")
                val gif = kind == "image/gif"
                val image = kind.startsWith("image/")
                val label = when {
                    video -> "Video ready to send"
                    gif -> "GIF ready to send"
                    image -> "Photo ready to send"
                    else -> "Attachment ready to send"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        FileImage(
                            path,
                            Modifier.padding(vertical = 8.dp).size(64.dp).clip(RoundedCornerShape(8.dp)),
                            when {
                                video -> "Attached video"
                                gif -> "Attached GIF"
                                image -> "Attached photo"
                                else -> "Attachment"
                            },
                        )
                        Text(
                            label,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { onAttachment(null) }) {
                            Icon(
                                Icons.Filled.Close,
                                when {
                                    video -> "Remove video"
                                    gif -> "Remove GIF"
                                    image -> "Remove photo"
                                    else -> "Remove attachment"
                                },
                            )
                        }
                    }
                }
            }
            if (panel != ComposerPanel.None) {
                val screenDp = LocalConfiguration.current.screenHeightDp.toFloat()
                val maxDp = (screenDp * COMPOSER_PANEL_MAX_FRACTION).coerceAtLeast(COMPOSER_PANEL_MIN_DP)
                val minDp = COMPOSER_PANEL_MIN_DP.coerceAtMost(maxDp)
                val shown = panelHeight.value.coerceIn(minDp.dp, maxDp.dp)
                val density = LocalDensity.current.density
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .height(shown),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .dragComposerPanel(density, minDp, maxDp, panelHeight)
                                .padding(top = 10.dp, bottom = 4.dp)
                                .semantics { contentDescription = "Drag to resize" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp, 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (panel == ComposerPanel.Emoji) "Emoji" else "GIFs",
                                modifier = Modifier
                                    .weight(1f)
                                    .dragComposerPanel(density, minDp, maxDp, panelHeight)
                                    .padding(start = 16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = { panel = ComposerPanel.None }) {
                                Icon(Icons.Filled.Close, if (panel == ComposerPanel.Emoji) "Close emoji" else "Close GIFs")
                            }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            if (panel == ComposerPanel.Emoji) {
                                Column(Modifier.fillMaxSize()) {
                                    OutlinedTextField(
                                        value = emojiQuery,
                                        onValueChange = { emojiQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .semantics { contentDescription = "Search emoji" },
                                        placeholder = { Text("Search emoji") },
                                        singleLine = true,
                                    )
                                    if (emojiQuery.isBlank()) {
                                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                                            EmojiCatalog.forEachIndexed { index, category ->
                                                TextButton(onClick = { emojiCategory = index }) {
                                                    Text(if (index == emojiCategory) "● ${category.name}" else category.name)
                                                }
                                            }
                                        }
                                    }
                                    val results = emojiResults(emojiQuery, emojiCategory)
                                    if (results.isEmpty()) {
                                        Text(
                                            "No emoji found",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(44.dp),
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                        ) {
                                            items(results) { glyph ->
                                                TextButton(
                                                    onClick = { insertAtCursor(glyph) },
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Text(glyph, style = MaterialTheme.typography.titleLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                GifPicker(
                                    hasKey = hasGifKey,
                                    searchGifs = searchGifs,
                                    attachGif = attachGif,
                                    onAttached = { path ->
                                        onAttachment(path)
                                        panel = ComposerPanel.None
                                    },
                                    onError = onError,
                                    onOpenSettings = onOpenSettings,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            TextButton(onClick = { panel = ComposerPanel.Emoji }) {
                                Text(if (panel == ComposerPanel.Emoji) "● Emoji" else "Emoji")
                            }
                            TextButton(onClick = {
                                panel = ComposerPanel.Gifs
                                keyboard?.hide()
                            }) {
                                Text(if (panel == ComposerPanel.Gifs) "● GIFs" else "GIFs")
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable { plus = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            "Add",
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(expanded = plus, onDismissRequest = { plus = false }) {
                        DropdownMenuItem(
                            text = { Text("Gallery") },
                            onClick = {
                                plus = false
                                openPhotos()
                            },
                            leadingIcon = { Icon(Icons.Filled.Image, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Video") },
                            onClick = {
                                plus = false
                                openVideos()
                            },
                            leadingIcon = { Icon(Icons.Filled.Videocam, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Camera") },
                            onClick = {
                                plus = false
                                openCamera()
                            },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Emoji") },
                            onClick = {
                                plus = false
                                panel = if (panel == ComposerPanel.Emoji) ComposerPanel.None else ComposerPanel.Emoji
                                if (panel == ComposerPanel.Emoji) keyboard?.hide()
                            },
                            leadingIcon = { Icon(Icons.Filled.EmojiEmotions, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("GIF") },
                            onClick = {
                                plus = false
                                panel = if (panel == ComposerPanel.Gifs) ComposerPanel.None else ComposerPanel.Gifs
                                if (panel == ComposerPanel.Gifs) keyboard?.hide()
                            },
                            leadingIcon = { Icon(Icons.Filled.Gif, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("My location") },
                            onClick = {
                                plus = false
                                onLocation()
                            },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Contact") },
                            onClick = {
                                plus = false
                                onContact()
                            },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                        )
                    }
                }
                OutlinedTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onText(it.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (enterSends && event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Message") },
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = if (enterSends) ImeAction.Send else ImeAction.Default),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                IconButton(onClick = onSchedule, enabled = scheduleEnabled) { Icon(Icons.Filled.Schedule, "Schedule") }
                FilledIconButton(onClick = onSend, enabled = !sending && (text.isNotBlank() || attachment != null)) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

@Composable
private fun TapbackRow(
    marks: List<BubbleReaction>,
    onPick: (TapbackSymbol, Boolean) -> Unit,
) {
    val mine = marks.filter { "You" in it.names }.map { it.symbol }.toSet()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val gap = 6.dp
        val each = ((maxWidth - gap * (tapbackSymbols.size - 1)) / tapbackSymbols.size).coerceAtMost(48.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            tapbackSymbols.forEach { symbol ->
                val on = symbol.symbol in mine
                val description = if (on) "Remove ${symbol.label}" else symbol.label
                Surface(
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(each)
                        .semantics { contentDescription = description }
                        .clickable { onPick(symbol, on) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(symbol.symbol, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

private data class ThreadTail(val count: Int, val lastSize: Int, val gap: Int, val scrolling: Boolean)

private fun LazyListState.tailGap(): Int {
    val info = layoutInfo
    val count = info.totalItemsCount
    if (count == 0) return 0
    val last = info.visibleItemsInfo.lastOrNull() ?: return Int.MAX_VALUE
    if (last.index != count - 1) return Int.MAX_VALUE
    return (last.offset + last.size - info.viewportEndOffset).coerceAtLeast(0)
}

private suspend fun LazyListState.revealTail() {
    val count = layoutInfo.totalItemsCount
    if (count <= 0) return
    scrollToItem(count - 1)
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return
    if (last.index != info.totalItemsCount - 1) return
    val overflow = last.offset + last.size - info.viewportEndOffset
    if (overflow > 1) scroll { scrollBy(overflow.toFloat()) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    reactions: List<app.messages.sms.BubbleReaction>,
    group: Boolean,
    indicator: String,
    caption: String,
    loadPart: suspend (Long) -> ByteArray?,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onImage: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenPart: (Long, String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.incoming) Alignment.Start else Alignment.End,
    ) {
        if (group && message.incoming) {
            Text(message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        val media = message.attachments.any { it.mime.startsWith("image/") || it.mime.startsWith("video/") }
        Box {
        Surface(
            color = if (message.incoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.incoming) 4.dp else 18.dp,
                bottomEnd = if (message.incoming) 18.dp else 4.dp,
            ),
            modifier = Modifier
                .then(if (media) Modifier.fillMaxWidth(0.85f) else Modifier.widthIn(max = 320.dp))
                .combinedClickable(onClick = onRetry, onLongClick = onLongPress),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                message.attachments.forEach { part ->
                    when {
                        part.mime.startsWith("image/") -> PartImage(
                            part.id,
                            loadPart,
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onImage(part.id) },
                                    onLongClick = onLongPress,
                                ),
                        )
                        part.mime.startsWith("video/") -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onOpenPart(part.id, part.mime) },
                                    onLongClick = onLongPress,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            VideoStill(part.id, part.mime, loadPart, Modifier.matchParentSize())
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PlayArrow, "Play video", tint = Color.White)
                            }
                        }
                        else -> TextButton(onClick = { onOpenPart(part.id, part.mime) }) {
                            Text(if (part.mime.startsWith("audio/")) "Audio" else "Attachment")
                        }
                    }
                }
                if (message.body.isNotBlank()) {
                    val linkColor = if (message.incoming) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    MessageText(message.body, linkColor, onOpenLink)
                    CopyCodeButton(message.body, onCopyCode)
                    val links = MessageLinks.find(message.body)
                    if (links.size == 1) {
                        TextButton(
                            onClick = { onOpenLink(links.first().url) },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                        ) { Text("Open link") }
                    } else {
                        links.take(4).forEach { link ->
                            TextButton(
                                onClick = { onOpenLink(link.url) },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                            ) { Text("Open ${MessageLinks.host(link.url)}") }
                        }
                    }
                }
                if (!message.incoming && message.status.isNotBlank()) {
                    Text(
                        if (message.status == Delivery.FAILED) "Not sent. Tap to retry" else message.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.status == Delivery.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
            if (reactions.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(if (message.incoming) Alignment.BottomEnd else Alignment.BottomStart)
                        .offset(y = 10.dp)
                        .semantics { contentDescription = reactionDescription(reactions) },
                ) {
                    Text(
                        reactions.joinToString("  ") { mark ->
                            if (mark.names.size > 1) "${mark.symbol} ${mark.names.size}" else mark.symbol
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        if (reactions.isNotEmpty()) Spacer(Modifier.height(12.dp))
        if (indicator.isNotBlank()) {
            Text(
                indicator,
                modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        if (caption.isNotBlank()) {
            Text(
                caption,
                modifier = Modifier.padding(top = 1.dp, start = 6.dp, end = 6.dp).widthIn(max = 320.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessageText(text: String, linkColor: Color, onOpen: (String) -> Unit) {
    val links = MessageLinks.find(text)
    if (links.isEmpty()) {
        Text(text)
        return
    }
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (link in links) {
            append(text.substring(cursor, link.start))
            withLink(
                LinkAnnotation.Clickable(
                    tag = link.url,
                    styles = TextLinkStyles(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                        ),
                    ),
                    linkInteractionListener = { onOpen(link.url) },
                ),
            ) {
                append(text.substring(link.start, link.end))
            }
            cursor = link.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    Text(annotated)
}

@Composable
private fun CopyCodeButton(body: String, onCopy: (String) -> Unit) {
    val code = VerificationCodes.find(body) ?: return
    TextButton(
        onClick = { onCopy(code) },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
    ) { Text("Copy code") }
}

private fun copyVerificationCode(context: android.content.Context, code: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    // A sensitive clip is hidden from the paste chip and from the app the code is pasted into.
    val clip = ClipData.newPlainText("Verification code", code)
    return runCatching { clipboard.setPrimaryClip(clip) }.isSuccess
}

private fun openMessageLink(context: android.content.Context, raw: String, onError: (String) -> Unit) {
    val href = MessageLinks.href(raw)
    if (!href.startsWith("https://") && !href.startsWith("http://")) {
        onError("That link cannot be opened")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(href))
    if (runCatching { context.startActivity(intent) }.isFailure) onError("No app can open that link")
}

@Composable
private fun PartImage(
    partId: Long,
    loadPart: suspend (Long) -> ByteArray?,
    modifier: Modifier = Modifier,
    zoom: Boolean = false,
) {
    val loaded by produceState<LoadedPart?>(null, partId) {
        value = withContext(Dispatchers.IO) {
            val bytes = loadPart(partId) ?: return@withContext null
            val ratio = imageRatio(bytes)
            if (KlipyGifs.isGif(bytes)) LoadedPart(bytes, null, ratio) else LoadedPart(bytes, decodeStill(bytes), ratio)
        }
    }
    val part = loaded
    // A loose height lets a small GIF stay at its pixel size. Lock the bubble to the picture's shape.
    val frame = if (zoom) modifier else modifier.aspectRatio((part?.ratio ?: 1.5f).coerceIn(0.75f, 1.9f))
    when {
        part == null -> Box(frame, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Info, contentDescription = null)
        }
        KlipyGifs.isGif(part.bytes) -> AnimatedGif(part.bytes, frame, "GIF")
        part.still != null -> Image(part.still, "Picture", frame, contentScale = ContentScale.Fit)
        else -> Box(frame, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Info, contentDescription = null)
        }
    }
}

private class LoadedPart(
    val bytes: ByteArray,
    val still: androidx.compose.ui.graphics.ImageBitmap?,
    val ratio: Float,
)

private fun imageRatio(bytes: ByteArray): Float {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return 1.5f
    return width.toFloat() / height.toFloat()
}

private fun decodeStill(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    while (longEdge / (sample * 2) >= 1080 && longEdge > 0) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

@Composable
private fun VideoStill(partId: Long, mime: String, loadPart: suspend (Long) -> ByteArray?, modifier: Modifier) {
    val cache = LocalContext.current.cacheDir
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, partId) {
        value = withContext(Dispatchers.IO) {
            val bytes = loadPart(partId) ?: return@withContext null
            val ext = if (mime.contains("3gpp")) "3gp" else "mp4"
            val file = File.createTempFile("vframe-$partId", ".$ext", cache)
            try {
                file.writeBytes(bytes)
                previewFrame(file.absolutePath, true)
            } catch (_: Throwable) {
                null
            } finally {
                file.delete()
            }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image, "Video", modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun FileImage(path: String, modifier: Modifier, description: String) {
    val video = mimeForPath(path).startsWith("video/")
    if (mimeForPath(path) == "image/gif") {
        val bytes by produceState<ByteArray?>(null, path) {
            value = withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() }
        }
        val data = bytes
        if (data != null && KlipyGifs.isGif(data)) {
            AnimatedGif(data, modifier, description, crop = true)
        } else {
            Box(modifier, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AttachFile, description)
            }
        }
    } else {
        StillFile(path, video, modifier, description)
    }
}

@Composable
private fun StillFile(path: String, video: Boolean, modifier: Modifier, description: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { previewFrame(path, video) }
    }
    val image = bitmap
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, description, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(if (video) Icons.Filled.Videocam else Icons.Filled.AttachFile, description)
        }
        if (video) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
        }
    }
}

private enum class ComposerPanel { None, Emoji, Gifs }

internal const val COMPOSER_PANEL_MIN_DP = 280f
internal const val COMPOSER_PANEL_START_DP = 360f
internal const val COMPOSER_PANEL_MAX_FRACTION = 0.62f

/** Finger moving down (positive [dragPx]) makes the card shorter. [density] is pixels per dp. */
internal fun composerPanelHeightDp(
    currentDp: Float,
    dragPx: Float,
    density: Float,
    minDp: Float,
    maxDp: Float,
): Float {
    val floor = minOf(minDp, maxDp)
    val cap = maxOf(minDp, maxDp)
    if (density <= 0f) return currentDp.coerceIn(floor, cap)
    return (currentDp - dragPx / density).coerceIn(floor, cap)
}

private fun Modifier.dragComposerPanel(
    density: Float,
    minDp: Float,
    maxDp: Float,
    height: MutableState<Dp>,
): Modifier = pointerInput(density, minDp, maxDp) {
    detectVerticalDragGestures { _, dragAmount ->
        height.value = composerPanelHeightDp(
            height.value.value,
            dragAmount,
            density,
            minDp,
            maxDp,
        ).dp
    }
}

private fun previewFrame(path: String, video: Boolean): androidx.compose.ui.graphics.ImageBitmap? {
    if (!video) return BitmapFactory.decodeFile(path)?.asImageBitmap()
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val frame = if (Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256)
        } else {
            retriever.getFrameAtTime(0)
        }
        frame?.asImageBitmap()
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun rememberContactShare(
    load: (Uri) -> String?,
    onError: (String) -> Unit,
    onCard: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onCardState = rememberUpdatedState(onCard)
    var waiting by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val card = withContext(Dispatchers.IO) { runCatching { load(uri) }.getOrNull() }
            if (card.isNullOrBlank()) onError("Could not read that contact") else onCardState.value(card)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!waiting) return@rememberLauncherForActivityResult
        waiting = false
        if (granted) picker.launch(null) else onError("Contacts permission is needed to send a contact")
    }
    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) picker.launch(null) else {
            waiting = true
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    val seed = remember { schedulePickerSeed() }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = seed.dateUtcMillis)
    val timeState = rememberTimePickerState(initialHour = seed.hour, initialMinute = seed.minute)
    if (dateMillis == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { dateMillis = dateState.selectedDateMillis }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Time") },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val day = dateMillis ?: return@TextButton
                    val zone = ZoneId.systemDefault()
                    val local = Instant.ofEpochMilli(day).atZone(ZoneId.of("UTC")).toLocalDate()
                    val at = local.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli()
                    onConfirm(at)
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

private fun requestDeviceLocation(context: android.content.Context, onResult: (String?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null) {
        onResult(null)
        return
    }
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }
    fun link(location: android.location.Location?): String? {
        location ?: return null
        return "https://maps.google.com/?q=${location.latitude},${location.longitude}"
    }
    fun lastKnown(): String? {
        for (provider in providers) {
            val found = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (found != null) return link(found)
        }
        return null
    }
    if (Build.VERSION.SDK_INT < 30) {
        onResult(lastKnown())
        return
    }
    fun tryProvider(index: Int) {
        if (index >= providers.size) {
            onResult(lastKnown())
            return
        }
        val provider = providers[index]
        val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        if (!enabled) {
            tryProvider(index + 1)
            return
        }
        try {
            manager.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { location ->
                val maps = link(location)
                if (maps != null) onResult(maps) else tryProvider(index + 1)
            }
        } catch (_: Exception) {
            tryProvider(index + 1)
        }
    }
    tryProvider(0)
}

private fun splitRecipients(raw: String): List<String> =
    raw.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
