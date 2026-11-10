package app.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.data.Conversation
import app.messages.data.formatTimestamp
import app.messages.data.trashSections

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(vm: MessagesViewModel) {
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(trashed) {
        selected = selected.filter { id -> trashed.any { it.conversation.threadId == id } }.toSet()
    }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    val canvas = MaterialTheme.colorScheme.mailCanvas()
    val ink = MaterialTheme.colorScheme.mailInk()
    val sections = trashSections(trashed, System.currentTimeMillis())
    val ordered = sections.flatMap { it.items }
    val allIds = ordered.map { it.conversation.threadId }.toSet()
    Scaffold(
        containerColor = canvas,
        contentColor = ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = canvas,
                    scrolledContainerColor = canvas,
                    navigationIconContentColor = ink,
                    titleContentColor = ink,
                    actionIconContentColor = ink,
                ),
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { confirm = allIds },
                        enabled = trashed.isNotEmpty(),
                    ) { Text("Delete all") }
                    TextButton(
                        onClick = {
                            vm.restoreTrash(allIds)
                            selected = emptySet()
                        },
                        enabled = trashed.isNotEmpty(),
                    ) { Text("Restore all") }
                }
            }
            if (trashed.isEmpty()) {
                item {
                    Text(
                        "Trash is empty",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = ink,
                    )
                }
            }
            val anchor = ordered.firstOrNull { it.conversation.threadId in selected }?.conversation?.threadId
            sections.forEach { section ->
                item(key = "deleted:${section.title}") {
                    Text(
                        section.title,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                    )
                }
                items(section.items, key = { it.conversation.threadId }) { entry ->
                    val conversation = entry.conversation
                    if (conversation.threadId == anchor) {
                        TrashRibbon(
                            onDelete = { confirm = selected },
                            onRestore = {
                                vm.restoreTrash(selected)
                                selected = emptySet()
                            },
                        )
                    }
                    TrashRow(
                        conversation = conversation,
                        selected = conversation.threadId in selected,
                        onClick = {
                            if (selected.isEmpty()) vm.openTrashedThread(conversation.threadId)
                            else selected = toggle(selected, conversation.threadId)
                        },
                        onLongPress = { selected = selected + conversation.threadId },
                    )
                }
            }
        }
    }
    val pending = confirm
    if (pending != null) {
        val everything = pending.size > 1 && pending == allIds
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (everything) "Delete all?" else "Delete permanently?") },
            text = {
                Text(
                    if (pending.size > 1) {
                        "These conversations are removed permanently."
                    } else {
                        "This conversation is removed permanently."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    vm.deleteFromTrash(pending)
                    selected = emptySet()
                }) { Text(if (everything) "Delete all" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TrashRibbon(onDelete: () -> Unit, onRestore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete")
        }
        TextButton(onClick = onRestore) {
            Icon(Icons.Filled.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Restore")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val card = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.mailCard()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(card)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                conversation.title.ifBlank { "Conversation" },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.mailInk(),
            )
            Text(
                formatTimestamp(conversation.timestamp),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            conversation.snippet.ifBlank { " " },
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun toggle(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id
