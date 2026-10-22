package app.messages.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.messages.R

data class LauncherPreset(
    val id: String,
    val label: String,
    val foreground: Int,
    val background: Int,
    val alias: String,
)

object LauncherIcons {
    val presets: List<LauncherPreset> = listOf(
        LauncherPreset("original", "Original", R.drawable.ic_messages_foreground, R.color.messages_launcher_background, "app.messages.LauncherOriginal"),
        LauncherPreset("black", "Black", R.drawable.ic_sms_icon_black, R.color.launcher_bg_light, "app.messages.LauncherBlack"),
        LauncherPreset("cyan", "Cyan", R.drawable.ic_sms_icon_cyan, R.color.launcher_bg_dark, "app.messages.LauncherCyan"),
        LauncherPreset("dark_blue", "Dark blue", R.drawable.ic_sms_icon_dark_blue, R.color.launcher_bg_dark, "app.messages.LauncherDarkBlue"),
        LauncherPreset("dark_purple", "Dark purple", R.drawable.ic_sms_icon_dark_purple, R.color.launcher_bg_dark, "app.messages.LauncherDarkPurple"),
        LauncherPreset("dark_red", "Dark red", R.drawable.ic_sms_icon_dark_red, R.color.launcher_bg_dark, "app.messages.LauncherDarkRed"),
        LauncherPreset("green", "Green", R.drawable.ic_sms_icon_green, R.color.launcher_bg_dark, "app.messages.LauncherGreen"),
        LauncherPreset("light_blue", "Light blue", R.drawable.ic_sms_icon_light_blue, R.color.launcher_bg_dark, "app.messages.LauncherLightBlue"),
        LauncherPreset("orange", "Orange", R.drawable.ic_sms_icon_orange, R.color.launcher_bg_dark, "app.messages.LauncherOrange"),
        LauncherPreset("purple", "Purple", R.drawable.ic_sms_icon_purple, R.color.launcher_bg_dark, "app.messages.LauncherPurple"),
        LauncherPreset("red", "Red", R.drawable.ic_sms_icon_red, R.color.launcher_bg_dark, "app.messages.LauncherRed"),
        LauncherPreset("white", "White", R.drawable.ic_sms_icon_white, R.color.launcher_bg_dark, "app.messages.LauncherWhite"),
        LauncherPreset("yellow", "Yellow", R.drawable.ic_sms_icon_yellow, R.color.launcher_bg_dark, "app.messages.LauncherYellow"),
    )

    fun canonical(raw: String): String = presets.firstOrNull { it.id == raw }?.id ?: "original"

    /** Enables the chosen launcher alias first, then disables the others. No-ops when it already matches. */
    fun apply(context: Context, raw: String) {
        val id = canonical(raw)
        val pm = context.packageManager
        val enabled = presets.associate { preset ->
            preset.id to componentEnabled(pm, ComponentName(context, preset.alias), preset.id == "cyan")
        }
        if (enabled.all { (presetId, on) -> on == (presetId == id) }) return
        val flag = PackageManager.DONT_KILL_APP
        pm.setComponentEnabledSetting(
            ComponentName(context, presets.first { it.id == id }.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            flag,
        )
        presets.filter { it.id != id }.forEach { preset ->
            pm.setComponentEnabledSetting(
                ComponentName(context, preset.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                flag,
            )
        }
    }

    private fun componentEnabled(pm: PackageManager, component: ComponentName, manifestDefault: Boolean): Boolean {
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> manifestDefault
        }
    }
}
