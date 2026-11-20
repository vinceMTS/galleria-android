package app.galleria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.galleria.home.FirstRunScreen
import app.galleria.home.HomeScreen
import app.galleria.home.HomeViewModel
import kotlinx.coroutines.launch

/** What the app shows once access is granted: first run (D-009), then home. */
@Composable
fun GalleriaRoot() {
    val container = (LocalContext.current.applicationContext as GalleriaApp).container
    val media = container.media
    val settings = container.settings
    val scope = rememberCoroutineScope()

    // Keep the index in line with MediaStore while the app is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            media.watchChanges().collect { media.sync() }
        }
    }

    val firstRunDone by settings.firstRunDone.collectAsStateWithLifecycle(initialValue = null)
    when (firstRunDone) {
        null -> Unit
        false -> FirstRunScreen(
            media = media,
            onSkip = { scope.launch { settings.setFirstRunDone() } },
            onConfirm = { choices ->
                scope.launch {
                    media.saveInclusion(choices)
                    settings.setFirstRunDone()
                }
            },
        )
        true -> {
            val vm = viewModel { HomeViewModel(media, settings) }
            val state by vm.state.collectAsStateWithLifecycle()
            HomeScreen(state, vm)
        }
    }
}
