package dev.tilevio.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LauncherScreen(dependencies: LauncherDependencies) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    val apps by produceState(initialValue = emptyList(), dependencies.catalog, refreshVersion) {
        value = withContext(Dispatchers.IO) { dependencies.catalog.loadApps() }
    }
    val gridState = rememberLazyGridState()
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var focusedAppKey by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) {
            val matchingIndex = apps.indexOfFirst { app -> app.key == focusedAppKey }
            val targetIndex = matchingIndex
                .takeIf { index -> index >= 0 }
                ?: focusedIndex.coerceIn(apps.indices)
            gridState.scrollToItem(targetIndex)
            withFrameNanos { }
            focusRequesters[apps[targetIndex].key]?.requestFocus()
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            message = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF171717)),
    ) {
        if (apps.isEmpty()) {
            EmptyCatalog()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                contentPadding = PaddingValues(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(
                    items = apps,
                    key = { _, app -> app.key },
                ) { index, app ->
                    val launchFailureMessage = stringResource(R.string.launch_failure, app.label)
                    AppTile(
                        app = app,
                        focusRequester = focusRequesters.getOrPut(app.key) { FocusRequester() },
                        onFocused = {
                            focusedAppKey = app.key
                            focusedIndex = index
                        },
                        onOpen = {
                            if (!dependencies.appLauncher.launch(app)) {
                                message = launchFailureMessage
                                refreshVersion++
                            }
                        },
                    )
                }
            }
        }

        message?.let { text ->
            BasicText(
                text = text,
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
private fun EmptyCatalog() {
    Box(modifier = Modifier.fillMaxSize()) {
        BasicText(
            text = stringResource(R.string.empty_catalog),
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
