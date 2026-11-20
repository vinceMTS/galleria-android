package app.galleria

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.galleria.folder.AllScreen
import app.galleria.folder.AllViewModel
import app.galleria.folder.FolderGridScreen
import app.galleria.folder.FolderGridViewModel
import app.galleria.home.FirstRunScreen
import app.galleria.home.HomeNavigation
import app.galleria.home.HomeScreen
import app.galleria.home.HomeViewModel
import kotlinx.coroutines.launch

/** The screens of the app (D-032: Navigation 3). The viewer will be a layer above the grids. */
sealed interface Screen {
    data object Home : Screen
    data object All : Screen
    data class Folder(val folderKey: String, val name: String) : Screen
}

private const val SEP = "\u0000"

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.All -> "all"
    is Screen.Folder -> listOf("folder", folderKey, name).joinToString(SEP)
}

private fun decodeScreen(s: String): Screen {
    val parts = s.split(SEP)
    return when (parts[0]) {
        "all" -> Screen.All
        "folder" -> Screen.Folder(parts[1], parts[2])
        else -> Screen.Home
    }
}

/** The back stack survives rotation and the app being killed in the background. */
private val backStackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<Screen>().apply { addAll(saved.map(::decodeScreen)) } },
)

/** What the app shows once access is granted: first run (D-009), then home and the grids. */
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
        true -> MainNavigation(container)
    }
}

@Composable
private fun MainNavigation(container: AppContainer) {
    val context = LocalContext.current
    val backStack = rememberSaveable(saver = backStackSaver) { mutableStateListOf<Screen>(Screen.Home) }
    // Photos and videos open in the viewer (topics 04 and 05), not written yet.
    val openMedia = { _: Any -> Toast.makeText(context, R.string.viewer_not_yet, Toast.LENGTH_SHORT).show() }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Screen.Home> {
                val vm = viewModel { HomeViewModel(container.media, container.settings) }
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state, vm,
                    HomeNavigation(
                        openFolder = { backStack.add(Screen.Folder(it.folderKey, it.folderName)) },
                        openAll = { backStack.add(Screen.All) },
                        openMedia = openMedia,
                    ),
                )
            }
            entry<Screen.All> {
                val vm = viewModel { AllViewModel(container.media, container.settings) }
                AllScreen(vm, onOpen = openMedia)
            }
            entry<Screen.Folder> { screen ->
                val vm = viewModel { FolderGridViewModel(container.media, container.settings, screen.folderKey) }
                FolderGridScreen(screen.name, vm, onOpen = openMedia)
            }
        },
    )
}
