package com.lumaos.pro.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.lumaos.pro.launcher.ui.ThemedUiIcons
import com.lumaos.pro.launcher.ui.theme.ThemeId
import kotlin.math.roundToInt

/** Shares drawable artwork with the View launcher while reacting to Compose theme changes. */
@Composable
internal fun themedIconPainter(res: Int): Painter {
    val theme = LocalLumaTheme.current.id
    if (theme == ThemeId.SIGNATURE) return painterResource(res)
    val context = LocalContext.current
    val drawable = remember(context, res, theme) { ThemedUiIcons.drawable(context, res, theme) }
        ?: return painterResource(res)
    return remember(drawable) {
        object : Painter() {
            override val intrinsicSize = Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            override fun applyAlpha(alpha: Float): Boolean {
                drawable.alpha = (255 * alpha).roundToInt()
                return true
            }
            override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
                drawable.colorFilter = colorFilter?.asAndroidColorFilter()
                return true
            }
            override fun DrawScope.onDraw() {
                drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                drawIntoCanvas { drawable.draw(it.nativeCanvas) }
            }
        }
    }
}
