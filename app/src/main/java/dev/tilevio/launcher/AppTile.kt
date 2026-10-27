package dev.tilevio.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale

@Composable
fun AppTile(
    app: App,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    upFocusRequester: FocusRequester? = null,
) {
    Image(
        bitmap = (app.banner ?: app.icon).asImageBitmap(),
        contentDescription = app.label,
        contentScale = if (app.banner == null) ContentScale.Inside else ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(RemoteTokens.TileCornerRadius))
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionUp && upFocusRequester != null) {
                    upFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            .remoteInteractive(
                onActivate = onOpen,
                focusStyle = { focused -> tileFocusOutline(focused) },
                focusRequester = focusRequester,
                onFocusChange = { focused -> if (focused) onFocused() },
            ),
    )
}
