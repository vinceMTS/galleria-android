package app.galleria.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailAspectTest {
    @Test fun squareIsOne() = assertEquals(1f, thumbnailAspect(1000, 1000))
    @Test fun portraitScreenshot() = assertEquals(2400f / 1080f, thumbnailAspect(1080, 2400), 0.001f)
    @Test fun landscapeSameAsPortrait() = assertEquals(thumbnailAspect(3000, 4000), thumbnailAspect(4000, 3000))
    @Test fun panoramaIsCapped() = assertEquals(4f, thumbnailAspect(12000, 1000))
    @Test fun unknownIsOne() {
        assertEquals(1f, thumbnailAspect(null, 100))
        assertEquals(1f, thumbnailAspect(0, 0))
    }
}

class SharpEnoughTest {
    @org.junit.Test fun cameraPreviewIsTooSmall() = org.junit.Assert.assertFalse(isSharpEnough(108, 192, 248))
    @org.junit.Test fun coveringThumbnailIsFine() = org.junit.Assert.assertTrue(isSharpEnough(270, 600, 248))
    @org.junit.Test fun smallToleranceIsAccepted() = org.junit.Assert.assertTrue(isSharpEnough(230, 400, 248))
}
