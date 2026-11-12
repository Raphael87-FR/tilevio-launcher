package dev.tilevio.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class SettingsLevel(val titleRes: Int) {
    Main(R.string.settings),
    Apps(R.string.apps),
}

@Composable
internal fun SettingsPanel(
    state: LauncherState,
    level: SettingsLevel,
    restoreAppsFocus: Boolean,
    onOpenApps: () -> Unit,
    onPickWallpaperImage: () -> Unit,
    isDefaultHome: Boolean,
    onActivateDefaultHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(1f / 3)
            .fillMaxHeight()
            .background(RemoteTokens.SettingsPanelSurface),
    ) {
        BasicText(
            text = stringResource(level.titleRes),
            modifier = Modifier.padding(32.dp),
            style = TextStyle(color = Color.White, fontSize = 28.sp),
        )
        if (level == SettingsLevel.Apps) {
            AppsSettings(state)
        } else {
            MainSettings(
                state = state,
                restoreAppsFocus = restoreAppsFocus,
                onOpenApps = onOpenApps,
                onPickWallpaperImage = onPickWallpaperImage,
                isDefaultHome = isDefaultHome,
                onActivateDefaultHome = onActivateDefaultHome,
            )
        }
    }
}

@Composable
private fun ColumnScope.MainSettings(
    state: LauncherState,
    restoreAppsFocus: Boolean,
    onOpenApps: () -> Unit,
    onPickWallpaperImage: () -> Unit,
    isDefaultHome: Boolean,
    onActivateDefaultHome: () -> Unit,
) {
    val activationFocusRequester = remember { FocusRequester() }
    val appsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isDefaultHome, restoreAppsFocus) {
        val focusRequester = when {
            restoreAppsFocus -> appsFocusRequester
            !isDefaultHome -> activationFocusRequester
            else -> return@LaunchedEffect
        }
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.wallpaper),
            style = TextStyle(color = Color.White, fontSize = 20.sp),
        )
        WallpaperThumbnails(
            selection = state.wallpaper,
            requestInitialFocus = isDefaultHome && !restoreAppsFocus,
            onSelectWallpaper = state::selectWallpaper,
            onChooseImage = { state.chooseWallpaperImage(onPickWallpaperImage) },
        )
        val hiddenCount = state.hiddenApps.size
        Action(
            onClick = onOpenApps,
            focusRequester = appsFocusRequester,
            modifier = Modifier.fillMaxWidth(),
        ) { actionModifier ->
            Column(modifier = actionModifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                BasicText(
                    text = stringResource(R.string.apps),
                    style = TextStyle(color = Color.White, fontSize = 20.sp),
                )
                BasicText(
                    text = pluralStringResource(R.plurals.hidden_app_count, hiddenCount, hiddenCount),
                    style = TextStyle(color = Color.White, fontSize = 16.sp),
                )
            }
        }
        if (isDefaultHome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                BasicText(
                    text = stringResource(R.string.default_home_active),
                    style = TextStyle(color = Color.White, fontSize = 16.sp),
                )
            }
        } else {
            TextAction(
                text = stringResource(R.string.set_default_home),
                onClick = onActivateDefaultHome,
                focusRequester = activationFocusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    BasicText(
        text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(24.dp),
        style = TextStyle(color = Color.White, fontSize = 14.sp),
    )
}

@Composable
private fun AppsSettings(state: LauncherState) {
    val firstAppFocusRequester = remember { FocusRequester() }
    val hasApps = state.apps.isNotEmpty()
    LaunchedEffect(hasApps) {
        if (hasApps) {
            withFrameNanos { }
            firstAppFocusRequester.requestFocus()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(state.apps, key = { _, app -> app.key }) { index, app ->
            val isHidden = app in state.hiddenApps
            AppVisibilityOption(
                app = app,
                isHidden = isHidden,
                focusRequester = firstAppFocusRequester.takeIf { index == 0 },
                onClick = { state.setHidden(app, hidden = !isHidden) },
            )
        }
    }
}

@Composable
private fun AppVisibilityOption(
    app: App,
    isHidden: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val visibility = stringResource(if (isHidden) R.string.hidden else R.string.shown)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .remoteInteractive(
                onActivate = onClick,
                focusStyle = { focused -> controlFrame(highlighted = focused) },
                focusRequester = focusRequester,
                selected = isHidden,
                mergeDescendants = true,
            )
            .semantics { stateDescription = visibility }
            .alpha(if (isHidden) 0.6f else 1f)
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
        Image(
            painter = painterResource(if (isHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun WallpaperThumbnails(
    selection: WallpaperSelection?,
    requestInitialFocus: Boolean,
    onSelectWallpaper: (Wallpaper) -> Unit,
    onChooseImage: () -> Unit,
) {
    val wallpaperFocusRequester = remember { FocusRequester() }
    val imageLabelRes = R.string.choose_wallpaper_image
    val selectedLabelRes = when (selection) {
        is WallpaperSelection.Solid -> selection.wallpaper.labelRes
        is WallpaperSelection.Image -> imageLabelRes
        null -> null
    }
    // A stored image can receive focus before its bitmap is ready.
    val focusLabelRes = selectedLabelRes ?: imageLabelRes
    LaunchedEffect(requestInitialFocus, focusLabelRes) {
        if (requestInitialFocus) {
            withFrameNanos { }
            wallpaperFocusRequester.requestFocus()
        }
    }
    var focusedLabelRes by remember { mutableStateOf<Int?>(null) }
    val onFocusChange: (Int, Boolean) -> Unit = { labelRes, focused ->
        if (focused) {
            focusedLabelRes = labelRes
        } else if (focusedLabelRes == labelRes) {
            focusedLabelRes = null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Wallpaper.entries.forEach { wallpaper ->
                val isSelected = selectedLabelRes == wallpaper.labelRes
                WallpaperThumbnail(
                    labelRes = wallpaper.labelRes,
                    color = wallpaper.color,
                    isSelected = isSelected,
                    focusRequester = wallpaperFocusRequester.takeIf { focusLabelRes == wallpaper.labelRes },
                    onFocusChange = onFocusChange,
                    onClick = { onSelectWallpaper(wallpaper) },
                    modifier = Modifier.weight(1f),
                )
            }
            val image = selection as? WallpaperSelection.Image
            WallpaperThumbnail(
                labelRes = imageLabelRes,
                color = RemoteTokens.ControlSurface,
                isSelected = image != null,
                focusRequester = wallpaperFocusRequester.takeIf { focusLabelRes == imageLabelRes },
                onFocusChange = onFocusChange,
                onClick = onChooseImage,
                modifier = Modifier.weight(1f),
            ) {
                if (image != null) {
                    // Leave a dark edge inside the white focus outline on bright images.
                    val inset = RemoteTokens.ControlBorderFocusedWidth + 2.dp
                    Image(
                        bitmap = image.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .padding(inset)
                            .clip(RoundedCornerShape(RemoteTokens.ControlCornerRadius - inset)),
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_photo),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                    )
                }
            }
        }
        BasicText(
            text = focusedLabelRes?.let { stringResource(it) }.orEmpty(),
            style = TextStyle(color = Color.White, fontSize = 18.sp),
        )
    }
}

@Composable
private fun WallpaperThumbnail(
    labelRes: Int,
    color: Color,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onFocusChange: (Int, Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val label = stringResource(labelRes)
    Box(
        modifier = modifier
            .aspectRatio(4f / 3)
            .remoteInteractive(
                onActivate = onClick,
                focusStyle = { focused -> controlFrame(highlighted = focused, surface = color) },
                focusRequester = focusRequester,
                onFocusChange = { focused -> onFocusChange(labelRes, focused) },
                selected = isSelected,
            )
            .semantics { contentDescription = label }
            .clip(RoundedCornerShape(RemoteTokens.ControlCornerRadius)),
    ) {
        content()
        if (isSelected) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .background(Color.Black, CircleShape)
                    .padding(2.dp),
            )
        }
    }
}

@Composable
fun SettingsAction(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Action(
        onClick = onClick,
        focusRequester = focusRequester,
        hasContainer = false,
        modifier = modifier.size(48.dp),
        enabled = enabled,
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
    enabled: Boolean = true,
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
            mergeDescendants = true,
            enabled = enabled,
        ),
    )
}
