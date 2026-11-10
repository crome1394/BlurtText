package app.messages.ui

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lantext.sms.GifSearch
import app.messages.sms.KlipyGifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

private data class GifReaction(val query: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val gifReactions = listOf(
    GifReaction("heart", Icons.Filled.Favorite),
    GifReaction("thumbs up", Icons.Filled.ThumbUp),
    GifReaction("thumbs down", Icons.Filled.ThumbDown),
    GifReaction("party", Icons.Filled.Celebration),
    GifReaction("smile", Icons.Filled.Mood),
    GifReaction("sad", Icons.Filled.SentimentDissatisfied),
)

@Composable
fun GifPicker(
    hasKey: Boolean,
    searchGifs: suspend (String) -> List<KlipyGifs.Hit>,
    attachGif: suspend (KlipyGifs.Hit) -> String,
    onAttached: (String) -> Unit,
    onError: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<KlipyGifs.Hit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    val cache = remember { HashMap<String, ByteArray>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(hasKey, query) {
        if (!hasKey) {
            hits = emptyList()
            loading = false
            note = null
            return@LaunchedEffect
        }
        if (query.isNotBlank()) delay(350)
        loading = true
        val result = withContext(Dispatchers.IO) { runCatching { searchGifs(query) } }
        loading = false
        result.onSuccess {
            hits = it
            note = if (it.isEmpty()) "No GIFs found" else null
        }.onFailure {
            hits = emptyList()
            note = it.message ?: "KLIPY search failed"
        }
    }
    Column(modifier.fillMaxSize()) {
        if (!hasKey) {
            Text(
                "Add a KLIPY API key in Settings",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("Open Settings")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            enabled = hasKey && picking == null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text("Search KLIPY") },
            singleLine = true,
        )
        if (hasKey) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                gifReactions.forEach { reaction ->
                    IconButton(onClick = { query = reaction.query }, enabled = picking == null) {
                        Icon(reaction.icon, reaction.query)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                !hasKey -> Unit
                loading && hits.isEmpty() -> CircularProgressIndicator()
                note != null && hits.isEmpty() -> Text(
                    note.orEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(hits, key = { it.id }) { hit ->
                        GifTile(
                            url = hit.previewUrl,
                            cache = cache,
                            busy = picking != null,
                            onClick = {
                                if (picking != null) return@GifTile
                                picking = hit.id
                                scope.launch {
                                    val saved = withContext(Dispatchers.IO) { runCatching { attachGif(hit) } }
                                    picking = null
                                    saved.onSuccess(onAttached).onFailure {
                                        onError(it.message ?: "Could not add that GIF")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        Text(
            "Powered by KLIPY",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GifTile(url: String, cache: MutableMap<String, ByteArray>, busy: Boolean, onClick: () -> Unit) {
    val bytes by produceState<ByteArray?>(cache[url], url) {
        cache[url]?.let {
            value = it
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { KlipyGifs.download(url, GifSearch.MAX_BYTES) }.getOrNull()
        }
        if (loaded != null) cache[url] = loaded
        value = loaded
    }
    Box(
        Modifier
            .height(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val data = bytes
        if (data != null) {
            AnimatedGif(data, Modifier.fillMaxSize(), "GIF", crop = true)
        } else {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun AnimatedGif(bytes: ByteArray, modifier: Modifier, description: String, crop: Boolean = false) {
    if (!KlipyGifs.isGif(bytes) || Build.VERSION.SDK_INT < 28) {
        val still = remember(bytes) { stillGif(bytes) }
        if (still != null) {
            Image(still, description, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
        } else {
            Box(modifier)
        }
        return
    }
    key(bytes.size, bytes.contentHashCode()) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                ImageView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    contentDescription = description
                    scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    val drawable = runCatching { decodeGif(context, bytes) }.getOrNull()
                    if (drawable != null) {
                        setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.apply {
                            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            start()
                        }
                    }
                }
            },
            onRelease = { view ->
                (view.drawable as? AnimatedImageDrawable)?.stop()
                view.setImageDrawable(null)
            },
        )
    }
}

private fun stillGif(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

@RequiresApi(28)
private fun decodeGif(context: android.content.Context, bytes: ByteArray): android.graphics.drawable.Drawable {
    val source = if (Build.VERSION.SDK_INT >= 31) {
        ImageDecoder.createSource(ByteBuffer.wrap(bytes))
    } else {
        val file = java.io.File.createTempFile("gifview", ".gif", context.cacheDir)
        file.writeBytes(bytes)
        ImageDecoder.createSource(file).also { file.deleteOnExit() }
    }
    return ImageDecoder.decodeDrawable(source)
}
