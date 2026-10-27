package dev.tilevio.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    state: LauncherState,
    onPickWallpaperImage: () -> Unit,
    onActivateHomeRole: () -> Unit,
) {
    val isDefaultHome = state.isDefaultHome
    val activationFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isDefaultHome) {
        if (!isDefaultHome) {
            withFrameNanos { }
            activationFocusRequester.requestFocus()
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BasicText(
            text = stringResource(R.string.settings),
            modifier = Modifier.padding(32.dp),
            style = TextStyle(color = Color.White, fontSize = 28.sp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                BasicText(
                    text = stringResource(R.string.wallpaper),
                    style = TextStyle(color = Color.White, fontSize = 20.sp),
                )
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2,
                ) {
                    Wallpaper.entries.forEach { wallpaper ->
                        val isSelected = state.wallpaper == WallpaperSelection.Solid(wallpaper)
                        WallpaperOption(
                            label = stringResource(wallpaper.labelRes),
                            color = wallpaper.color,
                            isSelected = isSelected,
                            requestInitialFocus = isDefaultHome && isSelected,
                            onClick = { state.selectWallpaper(wallpaper) },
                        )
                    }
                    val isImageSelected = state.wallpaper is WallpaperSelection.Image
                    WallpaperOption(
                        label = stringResource(R.string.choose_wallpaper_image),
                        color = RemoteTokens.ControlSurface,
                        isSelected = isImageSelected,
                        requestInitialFocus = isDefaultHome && isImageSelected,
                        onClick = { state.chooseWallpaperImage(onPickWallpaperImage) },
                    )
                }
            }
            item {
                BasicText(
                    text = stringResource(R.string.apps),
                    modifier = Modifier.padding(top = 16.dp),
                    style = TextStyle(color = Color.White, fontSize = 20.sp),
                )
            }
            items(state.apps, key = { app -> app.key }) { app ->
                val isHidden = app in state.hiddenApps
                AppVisibilityOption(
                    app = app,
                    isHidden = isHidden,
                    onClick = { state.setHidden(app, hidden = !isHidden) },
                )
            }
        }
        if (isDefaultHome) {
            BasicText(
                text = stringResource(R.string.default_home_active),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp),
                style = TextStyle(color = Color.White, fontSize = 20.sp),
            )
        } else {
            TextAction(
                text = stringResource(R.string.set_default_home),
                onClick = onActivateHomeRole,
                focusRequester = activationFocusRequester,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp),
            )
        }
        BasicText(
            text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(24.dp),
            style = TextStyle(color = Color.White, fontSize = 18.sp),
        )
    }
}

@Composable
private fun AppVisibilityOption(
    app: App,
    isHidden: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .remoteInteractive(
                onActivate = onClick,
                focusStyle = { focused -> controlFrame(highlighted = focused) },
                selected = isHidden,
                mergeDescendants = true,
            )
            .padding(16.dp),
    ) {
        Image(
            bitmap = app.icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        BasicText(
            text = app.label,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Color.White, fontSize = 18.sp),
        )
        BasicText(
            text = stringResource(if (isHidden) R.string.hidden else R.string.shown),
            style = TextStyle(color = Color.White, fontSize = 18.sp),
        )
    }
}

@Composable
private fun WallpaperOption(
    label: String,
    color: Color,
    isSelected: Boolean,
    requestInitialFocus: Boolean,
    onClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    BasicText(
        text = label,
        modifier = Modifier
            .remoteInteractive(
                onActivate = onClick,
                focusStyle = { focused -> controlFrame(highlighted = focused || isSelected, surface = color) },
                focusRequester = focusRequester,
                selected = isSelected,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        style = TextStyle(color = Color.White, fontSize = 18.sp),
    )
}

@Composable
fun SettingsAction(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Action(
        onClick = onClick,
        focusRequester = focusRequester,
        hasContainer = false,
        modifier = modifier.size(48.dp),
    ) { actionModifier ->
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.settings),
            modifier = actionModifier.padding(12.dp),
        )
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Action(
        onClick = onClick,
        focusRequester = focusRequester,
        modifier = modifier,
    ) { actionModifier ->
        BasicText(
            text = text,
            modifier = actionModifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = TextStyle(color = Color.White, fontSize = 20.sp),
        )
    }
}

@Composable
private fun Action(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    hasContainer: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    content(
        modifier.remoteInteractive(
            onActivate = onClick,
            focusStyle = { focused ->
                controlFrame(
                    highlighted = focused,
                    restingBorder = hasContainer,
                    surface = RemoteTokens.ControlSurface.takeIf { hasContainer },
                )
            },
            focusRequester = focusRequester,
        ),
    )
}
