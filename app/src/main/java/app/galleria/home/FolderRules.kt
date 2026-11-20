package app.galleria.home

import app.galleria.data.FolderVisibility

/**
 * The default for a folder seen for the first time (D-009): folders of messaging apps and
 * downloads start out of Recents and All, everything else (screenshots included) starts in.
 * A fixed list of known paths, no guessing, so the behaviour is predictable.
 */
object FolderRules {

    /** Folder names, matched against every segment of the path, ignoring case. */
    private val knownNames = setOf(
        "whatsapp", "whatsapp business",
        "telegram",
        "signal",
        "instagram",
        "messenger",
        "download", "downloads",
    )

    /** App package folders, e.g. `Android/media/com.whatsapp/…`. */
    private val knownPackages = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "org.thoughtcrime.securesms",
        "com.instagram.android",
        "com.facebook.orca",
    )

    fun isMessagingOrDownload(relativePath: String): Boolean =
        relativePath.split('/')
            .map { it.trim().lowercase() }
            .any { it in knownNames || it in knownPackages }

    fun defaultVisibility(relativePath: String): FolderVisibility =
        if (isMessagingOrDownload(relativePath)) FolderVisibility.ExcludedFromAll else FolderVisibility.Normal
}
