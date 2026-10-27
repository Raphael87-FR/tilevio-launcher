package dev.tilevio.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Design-system tokens from `.interface-design/system.md`. */
internal object RemoteTokens {
    val TileBorderFocused = Color.White
    val TileBorderFocusContrast = Color(0xFF171717)
    val TileCornerRadius = 12.dp
    val TileFocusOutlineWidth = 4.dp
    val TileFocusContrastWidth = 2.dp

    val ControlBorderResting = Color(0xFF666666)
    val ControlBorderFocused = Color.White
    val ControlSurface = Color(0xFF292929)
    val ControlBorderRestingWidth = 1.dp
    val ControlBorderFocusedWidth = 3.dp
    val ControlCornerRadius = 8.dp
}

private val activationKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Makes an element operable by remote control: focusable, activated on KeyUp of an
 * activation key, and exposed to accessibility as clickable. [focusStyle] is applied
 * before focus handling, so it can draw around or behind the element's content.
 */
internal fun Modifier.remoteInteractive(
    onActivate: () -> Unit,
    focusStyle: Modifier.(focused: Boolean) -> Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChange: (focused: Boolean) -> Unit = {},
    selected: Boolean? = null,
    mergeDescendants: Boolean = false,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .focusStyle(focused)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { state ->
            focused = state.isFocused
            onFocusChange(state.isFocused)
        }
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key in activationKeys) {
                onActivate()
                true
            } else {
                false
            }
        }
        .focusable()
        .semantics(mergeDescendants = mergeDescendants) {
            if (selected != null) this.selected = selected
            onClick {
                onActivate()
                true
            }
        }
}

/** White outline with an inner contrast edge, drawn over the content. */
internal fun Modifier.tileFocusOutline(focused: Boolean): Modifier = drawWithContent {
    drawContent()
    if (focused) {
        val cornerRadius = RemoteTokens.TileCornerRadius.toPx()
        val focusWidth = RemoteTokens.TileFocusOutlineWidth.toPx()
        val contrastWidth = RemoteTokens.TileFocusContrastWidth.toPx()
        val contrastInset = focusWidth + contrastWidth / 2
        drawRoundRect(
            color = RemoteTokens.TileBorderFocused,
            topLeft = Offset(focusWidth / 2, focusWidth / 2),
            size = Size(size.width - focusWidth, size.height - focusWidth),
            cornerRadius = CornerRadius(cornerRadius - focusWidth / 2),
            style = Stroke(width = focusWidth),
        )
        drawRoundRect(
            color = RemoteTokens.TileBorderFocusContrast,
            topLeft = Offset(contrastInset, contrastInset),
            size = Size(
                size.width - 2 * contrastInset,
                size.height - 2 * contrastInset,
            ),
            cornerRadius = CornerRadius(cornerRadius - contrastInset),
            style = Stroke(width = contrastWidth),
        )
    }
}

/**
 * Settings control frame: a border that thickens and turns white when [highlighted],
 * over an optional [surface].
 */
internal fun Modifier.controlFrame(
    highlighted: Boolean,
    restingBorder: Boolean = true,
    surface: Color? = RemoteTokens.ControlSurface,
): Modifier {
    val shape = RoundedCornerShape(RemoteTokens.ControlCornerRadius)
    val borderModifier = if (restingBorder || highlighted) {
        Modifier.border(
            width = if (highlighted) {
                RemoteTokens.ControlBorderFocusedWidth
            } else {
                RemoteTokens.ControlBorderRestingWidth
            },
            color = if (highlighted) {
                RemoteTokens.ControlBorderFocused
            } else {
                RemoteTokens.ControlBorderResting
            },
            shape = shape,
        )
    } else {
        Modifier
    }
    val surfaceModifier = if (surface != null) Modifier.background(surface, shape) else Modifier
    return this.then(borderModifier).then(surfaceModifier)
}
