package dev.tilevio.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
            .border(
                width = if (focused) 4.dp else 2.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = shape,
            )
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
