package dev.tilevio.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isHomeRoleHeld: Boolean,
    selectedWallpaper: Wallpaper,
    onWallpaperSelected: (Wallpaper) -> Unit,
    onActivateHomeRole: () -> Unit,
) {
    val activationFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isHomeRoleHeld) {
        if (!isHomeRoleHeld) {
            withFrameNanos { }
            activationFocusRequester.requestFocus()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        BasicText(
            text = stringResource(R.string.settings),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp),
            style = TextStyle(color = Color.White, fontSize = 28.sp),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = stringResource(R.string.wallpaper),
                style = TextStyle(color = Color.White, fontSize = 20.sp),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2,
            ) {
                Wallpaper.entries.forEach { wallpaper ->
                    WallpaperOption(
                        wallpaper = wallpaper,
                        isSelected = wallpaper == selectedWallpaper,
                        requestInitialFocus = isHomeRoleHeld && wallpaper == selectedWallpaper,
                        onClick = { onWallpaperSelected(wallpaper) },
                    )
                }
            }
        }
        if (isHomeRoleHeld) {
            BasicText(
                text = stringResource(R.string.default_home_active),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
                style = TextStyle(color = Color.White, fontSize = 20.sp),
            )
        } else {
            TextAction(
                text = stringResource(R.string.set_default_home),
                onClick = onActivateHomeRole,
                focusRequester = activationFocusRequester,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )
        }
        BasicText(
            text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            style = TextStyle(color = Color.White, fontSize = 18.sp),
        )
    }
}

@Composable
private fun WallpaperOption(
    wallpaper: Wallpaper,
    isSelected: Boolean,
    requestInitialFocus: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    BasicText(
        text = stringResource(wallpaper.labelRes),
        modifier = Modifier
            .border(
                width = if (focused || isSelected) 3.dp else 1.dp,
                color = if (focused || isSelected) Color.White else Color(0xFF666666),
                shape = shape,
            )
            .background(wallpaper.color, shape)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> focused = state.isFocused }
            .onRemoteActivation(onClick)
            .focusable()
            .semantics {
                selected = isSelected
                onClick {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        style = TextStyle(color = Color.White, fontSize = 18.sp),
    )
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicText(
        text = text,
        modifier = modifier
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color(0xFF666666),
                shape = shape,
            )
            .background(Color(0xFF292929), shape)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> focused = state.isFocused }
            .onRemoteActivation(onClick)
            .focusable()
            .semantics {
                onClick {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        style = TextStyle(color = Color.White, fontSize = 20.sp),
    )
}

private fun Modifier.onRemoteActivation(onClick: () -> Unit): Modifier = onKeyEvent { event ->
    val isActivationKey = event.key == Key.DirectionCenter ||
        event.key == Key.Enter ||
        event.key == Key.NumPadEnter
    if (event.type == KeyEventType.KeyUp && isActivationKey) {
        onClick()
        true
    } else {
        false
    }
}
