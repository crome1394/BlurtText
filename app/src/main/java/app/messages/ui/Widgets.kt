package app.messages.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.messages.data.initials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import kotlin.math.abs

private val AvatarColors = listOf(
    Color(0xFF1F4B6E),
    Color(0xFF3E6B4F),
    Color(0xFF8A4B2F),
    Color(0xFF5C4B8A),
    Color(0xFF8A3D55),
    Color(0xFF2F6D6A),
    Color(0xFF6B5A2F),
)

fun avatarColor(title: String): Color = AvatarColors[abs(title.hashCode()) % AvatarColors.size]

@Composable
fun Avatar(title: String, photoUri: String?, size: Dp = 44.dp) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photoUri) {
        value = if (photoUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(title)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                initials(title),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
