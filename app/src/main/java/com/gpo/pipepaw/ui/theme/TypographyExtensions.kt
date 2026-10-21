package com.gpo.pipepaw.ui.theme

import androidx.compose.ui.text.TextStyle

internal fun TextStyle.withTabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
