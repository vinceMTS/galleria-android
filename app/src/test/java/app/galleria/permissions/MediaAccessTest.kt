package app.galleria.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessTest {
    @Test fun photosAndVideosIsFull() =
        assertEquals(MediaAccess.Full, MediaAccess.of(images = true, videos = true, userSelected = false))

    @Test fun fullEvenIfUserSelectedIsAlsoGranted() =
        assertEquals(MediaAccess.Full, MediaAccess.of(images = true, videos = true, userSelected = true))

    @Test fun onlySomePhotosIsPartial() =
        assertEquals(MediaAccess.Partial, MediaAccess.of(images = false, videos = false, userSelected = true))

    @Test fun photosWithoutVideosIsPartial() =
        assertEquals(MediaAccess.Partial, MediaAccess.of(images = true, videos = false, userSelected = false))

    @Test fun nothingIsNone() =
        assertEquals(MediaAccess.None, MediaAccess.of(images = false, videos = false, userSelected = false))
}
