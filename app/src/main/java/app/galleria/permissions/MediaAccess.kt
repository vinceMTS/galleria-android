package app.galleria.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment

/** How much of the user's photos and videos the app may read (D-041). */
enum class MediaAccess {
    /** All photos and all videos. The only state in which the app goes on. */
    Full,

    /** "Only some photos" was chosen, or only one of photos/videos was granted. */
    Partial,

    /** Nothing granted yet, or denied. */
    None;

    companion object {
        fun of(images: Boolean, videos: Boolean, userSelected: Boolean): MediaAccess = when {
            images && videos -> Full
            images || videos || userSelected -> Partial
            else -> None
        }
    }
}

val mediaPermissions = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
)

fun Context.currentMediaAccess(): MediaAccess {
    fun granted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return MediaAccess.of(
        images = granted(Manifest.permission.READ_MEDIA_IMAGES),
        videos = granted(Manifest.permission.READ_MEDIA_VIDEO),
        userSelected = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
    )
}

/** "All files access" (D-042), granted from a system settings page. */
fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()
