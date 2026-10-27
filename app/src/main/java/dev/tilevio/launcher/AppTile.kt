package dev.tilevio.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun AppTile(
    app: App,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    upFocusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Image(
        bitmap = (app.banner ?: app.icon).asImageBitmap(),
        contentDescription = app.label,
        contentScale = if (app.banner == null) ContentScale.Inside else ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (focused) {
                    val cornerRadius = 12.dp.toPx()
                    val focusWidth = 4.dp.toPx()
                    val contrastWidth = 2.dp.toPx()
                    val contrastInset = focusWidth + contrastWidth / 2
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(focusWidth / 2, focusWidth / 2),
                        size = Size(size.width - focusWidth, size.height - focusWidth),
                        cornerRadius = CornerRadius(cornerRadius - focusWidth / 2),
                        style = Stroke(width = focusWidth),
                    )
                    drawRoundRect(
                        color = Color(0xFF171717),
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
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                val isActivationKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionUp && upFocusRequester != null) {
                    upFocusRequester.requestFocus()
                    true
                } else if (event.type == KeyEventType.KeyUp && isActivationKey) {
                    onOpen()
                    true
                } else {
                    false
                }
            }
            .focusable(),
    )
}
