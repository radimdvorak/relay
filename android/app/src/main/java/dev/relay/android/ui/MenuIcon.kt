package dev.relay.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The drawer's ☰: two lines, the lower one shorter. Drawn like Tabler's outlines, which have no such icon. */
val MenuIcon: ImageVector = ImageVector.Builder("Menu", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
        moveTo(4f, 9f)
        horizontalLineTo(20f)
        moveTo(4f, 15.5f)
        horizontalLineTo(14f)
    }
}.build()
