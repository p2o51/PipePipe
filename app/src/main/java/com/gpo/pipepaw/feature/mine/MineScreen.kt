package com.gpo.pipepaw.feature.mine

import androidx.compose.runtime.Composable
import com.gpo.pipepaw.feature.settings.SettingsList
import com.gpo.pipepaw.ui.components.LocalBottomBarInset

/**
 * 我的: for now the settings themselves. History, watch later and playlists (all local) move
 * in above them as they land; 设置 then becomes its own page.
 */
@Composable
fun MineScreen() {
    SettingsList(title = "我的", bottom = LocalBottomBarInset.current)
}
