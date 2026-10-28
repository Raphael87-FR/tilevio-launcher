package dev.tilevio.launcher

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** The platform side of the Default Home: the Home role, its preferences and the Intents that activate it. */
interface DefaultHomeAdapter {
    val isAvailable: Boolean
    val isHeld: Boolean
    var hasHandledInitialRequest: Boolean

    fun createRequestIntent(): Intent

    fun createHomeSettingsIntent(): Intent
}

/** How the user can make Tilevio the Default Home; [intent] is what the host must start. */
sealed interface DefaultHomeActivation {
    val intent: Intent

    /** Asks for the Home role; started for a result. */
    data class Request(override val intent: Intent) : DefaultHomeActivation

    /** Opens Android's Home settings, when the Home role cannot be requested. */
    data class HomeSettings(override val intent: Intent) : DefaultHomeActivation
}

class DefaultHome(private val adapter: DefaultHomeAdapter) {
    var isActive by mutableStateOf(adapter.isHeld)
        private set

    /** Reads the state again; call when returning from an activation. */
    fun refresh() {
        isActive = adapter.isHeld
    }

    fun activation(): DefaultHomeActivation =
        if (adapter.isAvailable) {
            DefaultHomeActivation.Request(adapter.createRequestIntent())
        } else {
            DefaultHomeActivation.HomeSettings(adapter.createHomeSettingsIntent())
        }

    /** Returns the activation once, on the first launch where Tilevio is not yet the Default Home. */
    fun takeInitialActivation(): DefaultHomeActivation? {
        if (adapter.isHeld || adapter.hasHandledInitialRequest) {
            return null
        }
        adapter.hasHandledInitialRequest = true
        return activation()
    }
}

/**
 * Connects [defaultHome] to the host: it is refreshed whenever the Launcher resumes, the first-launch activation is
 * started once, and the returned function starts an activation on demand.
 */
@Composable
fun rememberDefaultHomeActivator(defaultHome: DefaultHome): () -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultHome.refresh()
    }
    val activate = remember(defaultHome, context, request) {
        fun(activation: DefaultHomeActivation) {
            when (activation) {
                is DefaultHomeActivation.Request -> request.launch(activation.intent)
                is DefaultHomeActivation.HomeSettings -> context.startActivity(activation.intent)
            }
        }
    }

    DisposableEffect(lifecycleOwner, defaultHome) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                defaultHome.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(defaultHome, activate) {
        defaultHome.takeInitialActivation()?.let(activate)
    }
    return { activate(defaultHome.activation()) }
}
