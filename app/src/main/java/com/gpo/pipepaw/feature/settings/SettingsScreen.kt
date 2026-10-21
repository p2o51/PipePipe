package com.gpo.pipepaw.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gpo.pipepaw.BuildConfig
import com.gpo.pipepaw.core.util.CrashLog
import com.gpo.pipepaw.ui.components.rememberToast
import com.gpo.pipepaw.core.settings.AppSettings
import com.gpo.pipepaw.ui.components.FameMode
import com.gpo.pipepaw.ui.components.FameScope
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols

/** 设置 as a pushed page (the 我的 tab shows the same list in place). */
@Composable
fun SettingsScreen() {
    SettingsList(title = "设置", showBack = true)
}

/**
 * Every setting the skeleton has. Labels only, no explanations (Paw copy rule); a summary
 * appears only where the choice is not self-evident.
 */
@Composable
fun SettingsList(title: String, showBack: Boolean = false, bottom: Dp = 0.dp) {
    val context = LocalContext.current
    val toast = rememberToast()
    val themeMode by AppSettings.themeMode.state.collectAsState()
    val dynamic by AppSettings.dynamicColor.state.collectAsState()
    val cover by AppSettings.coverColor.state.collectAsState()
    val hideShorts by AppSettings.hideShorts.state.collectAsState()
    val autoPlay by AppSettings.autoPlay.state.collectAsState()
    val backgroundPlay by AppSettings.backgroundPlay.state.collectAsState()
    val autoPip by AppSettings.autoPip.state.collectAsState()
    val height by AppSettings.preferredHeight.state.collectAsState()
    val fame by FameMode.scopeKey.state.collectAsState()

    SettingsScaffold(title = title, showBack = showBack) {
        SettingsGroup("外观") {
            item {
                SettingsToggleItem(
                    title = "深浅",
                    icon = PiliSymbols.DarkMode,
                    choices = listOf(Choice(0, "跟随系统"), Choice(1, "浅色"), Choice(2, "深色")),
                    selected = themeMode,
                    onSelect = { AppSettings.set(AppSettings.themeMode, it) },
                )
            }
            item { SettingsSwitchItem("壁纸配色", dynamic, { AppSettings.set(AppSettings.dynamicColor, it) }, icon = PiliSymbols.Palette) }
            item { SettingsSwitchItem("封面取色", cover, { AppSettings.set(AppSettings.coverColor, it) }, icon = PiliSymbols.Image) }
        }
        SettingsGroup("内容") {
            item { SettingsSwitchItem("隐藏 Shorts", hideShorts, { AppSettings.set(AppSettings.hideShorts, it) }, icon = PiliSymbols.Video) }
            item {
                SettingsToggleItem(
                    title = "名声模式",
                    icon = YoinSymbols.VisibilityOff,
                    choices = FameScope.entries.filter { it != FameScope.Self }.map { Choice(it.key, it.label) },
                    selected = FameScope.of(fame).let { if (it == FameScope.Self) FameScope.Off else it }.key,
                    onSelect = { FameMode.select(FameScope.of(it)) },
                )
            }
        }
        SettingsGroup("播放") {
            item { SettingsSwitchItem("自动播放", autoPlay, { AppSettings.set(AppSettings.autoPlay, it) }, icon = YoinSymbols.PlayCircle) }
            item { SettingsSwitchItem("后台播放", backgroundPlay, { AppSettings.set(AppSettings.backgroundPlay, it) }, icon = YoinSymbols.VolumeUp) }
            item { SettingsSwitchItem("自动画中画", autoPip, { AppSettings.set(AppSettings.autoPip, it) }, icon = PiliSymbols.Pip) }
            item {
                SettingsChoiceItem(
                    title = "默认画质",
                    icon = PiliSymbols.Hd,
                    choices = listOf(2160, 1440, 1080, 720, 480, 360).map { Choice(it, "${it}p") } + Choice(0, "最高"),
                    selected = height,
                    onSelect = { AppSettings.set(AppSettings.preferredHeight, it) },
                )
            }
        }
        SettingsGroup("关于") {
            item { SettingsItem(title = "PipePaw", icon = YoinSymbols.Info, summary = BuildConfig.VERSION_NAME) }
            val crash = CrashLog.read()
            if (crash != null) {
                item {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    SettingsItem(
                        title = "上次崩溃",
                        icon = YoinSymbols.Info,
                        summary = crash.lineSequence().drop(2).firstOrNull { it.isNotBlank() }?.take(80),
                        onClick = {
                            clipboard?.setPrimaryClip(ClipData.newPlainText("PipePaw crash", crash))
                            toast("已复制崩溃日志")
                        },
                    )
                }
            }
            item {
                SettingsLinkItem(
                    icon = PiliSymbols.Link,
                    title = "PipePipe",
                    summary = "GPL-3.0",
                    onClick = { openTab(context, "https://github.com/InfinityLoop1308/PipePipe") },
                )
            }
        }
        Spacer(Modifier.height(bottom))
    }
}

private fun openTab(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}
