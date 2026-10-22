package app.messages.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.messages.data.Persisted

data class ThemeChoice(
    val id: String,
    val label: String,
    val swatch: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
)

private fun scheme(
    dark: Boolean,
    primary: Long,
    strong: Long,
    bg: Long,
    ink: Long,
    muted: Long,
    line: Long,
    incoming: Long,
    outgoing: Long,
    outgoingInk: Long,
    danger: Long,
): ColorScheme {
    val args = ColorSet(
        primary = Color(primary),
        strong = Color(strong),
        bg = Color(bg),
        ink = Color(ink),
        muted = Color(muted),
        line = Color(line),
        incoming = Color(incoming),
        outgoing = Color(outgoing),
        outgoingInk = Color(outgoingInk),
        danger = Color(danger),
    )
    return if (dark) args.dark() else args.light()
}

private data class ColorSet(
    val primary: Color,
    val strong: Color,
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val incoming: Color,
    val outgoing: Color,
    val outgoingInk: Color,
    val danger: Color,
) {
    fun light() = lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = outgoing,
        onPrimaryContainer = outgoingInk,
        secondary = strong,
        secondaryContainer = incoming,
        background = bg,
        surface = bg,
        onSurface = ink,
        onSurfaceVariant = muted,
        surfaceVariant = incoming,
        outline = line,
        error = danger,
    )

    fun dark() = darkColorScheme(
        primary = primary,
        onPrimary = outgoingInk,
        primaryContainer = outgoing,
        onPrimaryContainer = outgoingInk,
        secondary = strong,
        secondaryContainer = incoming,
        background = bg,
        surface = bg,
        onSurface = ink,
        onSurfaceVariant = muted,
        surfaceVariant = incoming,
        outline = line,
        error = danger,
    )
}

val ThemeChoices: List<ThemeChoice> = listOf(
    ThemeChoice(
        "fern",
        "Fern",
        Color(0xFF156B57),
        scheme(false, 0xFF156B57, 0xFF1F8A70, 0xFFF3F6F4, 0xFF14201A, 0xFF5B6B63, 0xFFD8E0DB, 0xFFE8EEEA, 0xFF1F8A70, 0xFFFFFFFF, 0xFFB42318),
        scheme(true, 0xFF1F8A70, 0xFF34C49A, 0xFF101412, 0xFFE6EDE8, 0xFF9AADA3, 0xFF2B3330, 0xFF242B27, 0xFF1F8A70, 0xFFFFFFFF, 0xFFF97066),
    ),
    ThemeChoice(
        "ocean",
        "Ocean",
        Color(0xFF0B6E99),
        scheme(false, 0xFF0B6E99, 0xFF0E7490, 0xFFF0F6FA, 0xFF0E2433, 0xFF4D6B7A, 0xFFCFE0EA, 0xFFE4EEF4, 0xFF0E7490, 0xFFFFFFFF, 0xFFB42318),
        scheme(true, 0xFF38BDF8, 0xFF7DD3FC, 0xFF0B1520, 0xFFE6F1F7, 0xFF8AA8B8, 0xFF243848, 0xFF1A2C3A, 0xFF0369A1, 0xFFF0F9FF, 0xFFF97066),
    ),
    ThemeChoice(
        "dusk",
        "Dusk",
        Color(0xFF5B3CC4),
        scheme(false, 0xFF5B3CC4, 0xFF6D28D9, 0xFFF6F3FB, 0xFF1C1430, 0xFF6B6280, 0xFFDDD4EE, 0xFFEEE8F8, 0xFF5B3CC4, 0xFFFFFFFF, 0xFFB42318),
        scheme(true, 0xFFA78BFA, 0xFFC4B5FD, 0xFF16121F, 0xFFF0EAF8, 0xFFB0A3C9, 0xFF3A3148, 0xFF2B2438, 0xFF6D28D9, 0xFFF5F3FF, 0xFFF97066),
    ),
    ThemeChoice(
        "ember",
        "Ember",
        Color(0xFFC2410C),
        scheme(false, 0xFFC2410C, 0xFFEA580C, 0xFFFBF6F1, 0xFF2A140C, 0xFF7A5A4A, 0xFFEAD9CC, 0xFFF3E8DE, 0xFFC2410C, 0xFFFFFFFF, 0xFFB42318),
        scheme(true, 0xFFFB923C, 0xFFFDBA74, 0xFF1A120E, 0xFFF6EBE3, 0xFFC4A494, 0xFF3D2C24, 0xFF33241C, 0xFFC2410C, 0xFFFFF7ED, 0xFFF97066),
    ),
    ThemeChoice(
        "slate",
        "Slate",
        Color(0xFF334155),
        scheme(false, 0xFF334155, 0xFF475569, 0xFFF4F5F7, 0xFF0F172A, 0xFF64748B, 0xFFD8DEE8, 0xFFE8ECF1, 0xFF334155, 0xFFFFFFFF, 0xFFB42318),
        scheme(true, 0xFF94A3B8, 0xFFCBD5E1, 0xFF0F1419, 0xFFE8EDF3, 0xFF94A3B8, 0xFF2C3642, 0xFF243040, 0xFF475569, 0xFFF8FAFC, 0xFFF97066),
    ),
    ThemeChoice(
        "sakura",
        "Sakura",
        Color(0xFFBE185D),
        scheme(false, 0xFFBE185D, 0xFFDB2777, 0xFFFDF6F8, 0xFF3B1020, 0xFF8A5A6C, 0xFFF0D5DE, 0xFFF8E8EE, 0xFFBE185D, 0xFFFFFFFF, 0xFFB42318),
        scheme(true, 0xFFF472B6, 0xFFF9A8D4, 0xFF1A1114, 0xFFFCE8EF, 0xFFD4A3B4, 0xFF3D2A31, 0xFF332028, 0xFFBE185D, 0xFFFFF1F5, 0xFFF97066),
    ),
    ThemeChoice(
        "meadow",
        "Meadow",
        Color(0xFF3F7D20),
        scheme(false, 0xFF3F7D20, 0xFF4D9A28, 0xFFF4FAF2, 0xFF142414, 0xFF587058, 0xFFD4E4D0, 0xFFE6F0E2, 0xFF3F7D20, 0xFFFFFFFF, 0xFFB42318),
        scheme(true, 0xFF86EFAC, 0xFFBBF7D0, 0xFF101610, 0xFFE8F3E6, 0xFF9AB396, 0xFF2C382C, 0xFF243024, 0xFF3F7D20, 0xFFF0FDF4, 0xFFF97066),
    ),
    ThemeChoice(
        "nord",
        "Nord",
        Color(0xFF5E81AC),
        scheme(false, 0xFF5E81AC, 0xFF81A1C1, 0xFFECEFF4, 0xFF2E3440, 0xFF4C566A, 0xFFD8DEE9, 0xFFE5E9F0, 0xFF5E81AC, 0xFFECEFF4, 0xFFBF616A),
        scheme(true, 0xFF88C0D0, 0xFF8FBCBB, 0xFF2E3440, 0xFFECEFF4, 0xFFA3B0C2, 0xFF4C566A, 0xFF434C5E, 0xFF5E81AC, 0xFFECEFF4, 0xFFBF616A),
    ),
    ThemeChoice(
        "contrast",
        "Contrast",
        Color(0xFF000000),
        scheme(false, 0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFF000000, 0xFF333333, 0xFF000000, 0xFFE6E6E6, 0xFF000000, 0xFFFFFFFF, 0xFF9B0000),
        scheme(true, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF, 0xFFC8C8C8, 0xFFFFFFFF, 0xFF1A1A1A, 0xFFFFFFFF, 0xFF000000, 0xFFFF6B6B),
    ),
)

fun resolvedPalette(id: String): String = ThemeChoices.find { it.id == id }?.id ?: "fern"

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = scheme.primary,
            uncheckedThumbColor = scheme.onSurface,
            uncheckedTrackColor = scheme.surfaceVariant,
            uncheckedBorderColor = scheme.onSurface,
        ),
    )
}

@Composable
fun MessagesTheme(settings: Persisted, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val colors = colorsFor(settings, context, systemDark)
    val base = LocalDensity.current
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalDensity provides Density(base.density, base.fontScale * settings.fontScale),
        ) {
            content()
        }
    }
}

private fun colorsFor(settings: Persisted, context: Context, systemDark: Boolean): ColorScheme {
    val dark = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    val choice = ThemeChoices.first { it.id == resolvedPalette(settings.palette) }
    return if (dark) choice.dark else choice.light
}
