package app.galleria.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.galleria.R

/**
 * Shows [content] only with full media access and all files access (D-041, D-042).
 * Otherwise shows a screen that explains both permissions and does not let the user go on.
 * The state is checked again every time the app comes back to the foreground, so a
 * permission revoked later brings this screen back.
 */
@Composable
fun AccessGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var media by remember { mutableStateOf(context.currentMediaAccess()) }
    var allFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    // After two denials Android stops showing the dialog: the button then opens settings.
    var mediaDialogBlocked by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        media = context.currentMediaAccess()
        allFiles = hasAllFilesAccess()
        onPauseOrDispose { }
    }

    val requestMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        media = context.currentMediaAccess()
        mediaDialogBlocked = media == MediaAccess.None && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)
    }

    if (media == MediaAccess.Full && allFiles) {
        content()
    } else {
        AccessScreen(
            media = media,
            allFiles = allFiles,
            onRequestMedia = {
                if (mediaDialogBlocked) context.openAppDetails() else requestMedia.launch(mediaPermissions)
            },
            mediaButton = if (mediaDialogBlocked) R.string.access_open_settings else R.string.access_media_button,
            onRequestAllFiles = { context.openAllFilesAccessSettings(activity) },
        )
    }
}

@Composable
private fun AccessScreen(
    media: MediaAccess,
    allFiles: Boolean,
    onRequestMedia: () -> Unit,
    @StringRes mediaButton: Int,
    onRequestAllFiles: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.access_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.access_intro), style = MaterialTheme.typography.bodyLarge)

        PermissionCard(
            title = R.string.access_media_title,
            body = R.string.access_media_body,
            warning = if (media == MediaAccess.Partial) R.string.access_media_partial else null,
            granted = media == MediaAccess.Full,
            button = mediaButton,
            onClick = onRequestMedia,
        )
        PermissionCard(
            title = R.string.access_files_title,
            body = R.string.access_files_body,
            warning = null,
            granted = allFiles,
            button = R.string.access_files_button,
            onClick = onRequestAllFiles,
        )
    }
}

@Composable
private fun PermissionCard(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes warning: Int?,
    granted: Boolean,
    @StringRes button: Int,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            if (warning != null) {
                Text(
                    stringResource(warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (granted) {
                Text(
                    "✓ " + stringResource(R.string.access_granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(onClick = onClick) { Text(stringResource(button)) }
            }
        }
    }
}

private fun Context.openAppDetails() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    )
}

private fun Context.openAllFilesAccessSettings(activity: Activity?) {
    val context = activity ?: this
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null))
        )
    } catch (_: ActivityNotFoundException) {
        // Some builds only have the general list of apps with all files access.
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}
