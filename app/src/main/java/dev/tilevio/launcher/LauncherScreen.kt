package dev.tilevio.launcher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.OnNewIntentProvider
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.DateTimeException
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun LauncherScreen(dependencies: LauncherDependencies) {
    val scope = rememberCoroutineScope()
    val state = remember(dependencies, scope) { LauncherState(dependencies, scope) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var pickedWallpaperImage by remember { mutableStateOf<Uri?>(null) }

    val defaultHome = dependencies.defaultHome
    val activateDefaultHome = rememberDefaultHomeActivator(defaultHome)
    val pickWallpaperImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickedWallpaperImage = uri
    }

    BackHandler {
        isSettingsVisible = false
    }
    CloseSettingsOnNewIntent { isSettingsVisible = false }
    DisposableEffect(state) {
        onDispose(state.watchCatalog())
    }
    val visibleApps = state.visibleApps
    val gridState = rememberLazyGridState()
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val settingsFocusRequester = remember { FocusRequester() }
    var focusedAppKey by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(visibleApps, isSettingsVisible) {
        if (!isSettingsVisible) {
            if (visibleApps.isNotEmpty()) {
                val matchingIndex = visibleApps.indexOfFirst { app -> app.key == focusedAppKey }
                val targetIndex = matchingIndex
                    .takeIf { index -> index >= 0 }
                    ?: focusedIndex.coerceIn(visibleApps.indices)
                gridState.scrollToItem(targetIndex)
                withFrameNanos { }
                focusRequesters[visibleApps[targetIndex].key]?.requestFocus()
            } else {
                withFrameNanos { }
                settingsFocusRequester.requestFocus()
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(pickedWallpaperImage) {
            val uri = pickedWallpaperImage ?: return@LaunchedEffect
            state.importWallpaperImage(uri = uri, width = constraints.maxWidth, height = constraints.maxHeight)
            pickedWallpaperImage = null
        }
        when (val currentWallpaper = state.wallpaper) {
            is WallpaperSelection.Solid -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(currentWallpaper.wallpaper.color),
            )
            is WallpaperSelection.Image -> Image(
                bitmap = currentWallpaper.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            null -> Unit
        }
        if (isSettingsVisible) {
            SettingsScreen(
                state = state,
                onPickWallpaperImage = { pickWallpaperImage.launch(arrayOf("image/*")) },
                isDefaultHome = defaultHome.isActive,
                onActivateDefaultHome = activateDefaultHome,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeClock(modifier = Modifier.padding(end = 16.dp))
                    SettingsAction(
                        onClick = { isSettingsVisible = true },
                        focusRequester = settingsFocusRequester,
                        modifier = Modifier.focusProperties {
                            visibleApps.getOrNull(focusedIndex)?.let { app ->
                                down = focusRequesters.getOrPut(app.key) { FocusRequester() }
                            }
                        },
                    )
                }
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    if (state.apps.isEmpty()) {
                        EmptyCatalog(R.string.empty_catalog)
                    } else if (visibleApps.isEmpty()) {
                        EmptyCatalog(R.string.all_apps_hidden)
                    } else {
                        val columnCount = 5
                        val rowCount = (visibleApps.size + columnCount - 1) / columnCount
                        val tileWidth = (maxWidth - 64.dp - 96.dp) / columnCount
                        val tileHeight = tileWidth * 9f / 16f
                        val gridHeight = tileHeight * rowCount + 24.dp * (rowCount - 1)
                        val verticalPadding = ((maxHeight - gridHeight) / 2).coerceAtLeast(32.dp)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnCount),
                            modifier = Modifier.fillMaxSize(),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 32.dp,
                                top = verticalPadding,
                                end = 32.dp,
                                bottom = verticalPadding,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            itemsIndexed(
                                items = visibleApps,
                                key = { _, app -> app.key },
                            ) { index, app ->
                                AppTile(
                                    app = app,
                                    focusRequester = focusRequesters.getOrPut(app.key) { FocusRequester() },
                                    upFocusRequester = settingsFocusRequester.takeIf { index == 0 },
                                    onFocused = {
                                        focusedAppKey = app.key
                                        focusedIndex = index
                                    },
                                    onOpen = { state.launch(app) },
                                )
                            }
                        }
                    }
                }
            }
        }

        state.message?.let { message ->
            BasicText(
                text = when (message) {
                    is LauncherMessage.LaunchFailure -> stringResource(R.string.launch_failure, message.appLabel)
                    LauncherMessage.WallpaperImportFailure -> stringResource(R.string.wallpaper_import_failure)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(Color(0xFF333333), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                style = TextStyle(color = Color.White, fontSize = 18.sp),
            )
        }
    }
}

@Composable
private fun CloseSettingsOnNewIntent(onNewIntent: () -> Unit) {
    val activity = LocalActivity.current as? OnNewIntentProvider ?: return
    val currentOnNewIntent by rememberUpdatedState(onNewIntent)
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { currentOnNewIntent() }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

@Composable
private fun HomeClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentTime by remember { mutableStateOf(currentWallTimeMillis()) }
    var isVisible by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner, isVisible) {
        if (isVisible) {
            while (true) {
                currentTime = currentWallTimeMillis()
                delay(60_000 - currentTime % 60_000)
            }
        }
    }

    BasicText(
        text = DateFormat.getTimeFormat(context).format(Date(currentTime)),
        modifier = modifier,
        style = TextStyle(
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
    )
}

private fun currentWallTimeMillis(): Long {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return System.currentTimeMillis()
    }
    return try {
        SystemClock.currentNetworkTimeClock().millis()
    } catch (_: DateTimeException) {
        System.currentTimeMillis()
    }
}

@Composable
private fun EmptyCatalog(messageRes: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        BasicText(
            text = stringResource(messageRes),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            style = TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
