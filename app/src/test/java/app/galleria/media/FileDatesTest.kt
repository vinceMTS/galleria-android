package app.galleria.media

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileDatesTest {

    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s)

    @Test fun whatsAppImageHasDayOnly() =
        assertEquals(at(2025, 3, 12), FileDates.dateFromFileName("IMG-20250312-WA0001.jpg"))

    @Test fun whatsAppVideo() =
        assertEquals(at(2025, 3, 12), FileDates.dateFromFileName("VID-20250312-WA0007.mp4"))

    @Test fun cameraImage() =
        assertEquals(at(2025, 3, 12, 15, 30, 0), FileDates.dateFromFileName("IMG_20250312_153000.jpg"))

    @Test fun cameraVideo() =
        assertEquals(at(2024, 12, 31, 23, 59, 58), FileDates.dateFromFileName("VID_20241231_235958.mp4"))

    @Test fun screenshotCompact() =
        assertEquals(at(2025, 3, 12, 15, 30, 0), FileDates.dateFromFileName("Screenshot_20250312-153000.png"))

    @Test fun screenshotWithDashesAndApp() =
        assertEquals(
            at(2025, 3, 12, 15, 30, 0),
            FileDates.dateFromFileName("Screenshot_2025-03-12-15-30-00-123_com.whatsapp.jpg"),
        )

    @Test fun pixelNameWithMilliseconds() =
        assertEquals(at(2025, 3, 12, 15, 30, 0), FileDates.dateFromFileName("PXL_20250312_153000123.jpg"))

    @Test fun fullPathUsesFileNameOnly() =
        assertEquals(
            at(2025, 3, 12),
            FileDates.dateFromFileName("/storage/emulated/0/2019/IMG-20250312-WA0001.jpg"),
        )

    @Test fun impossibleTimeKeepsTheDay() =
        assertEquals(at(2025, 3, 12), FileDates.dateFromFileName("IMG_20250312_999999.jpg"))

    @Test fun noDate() = assertNull(FileDates.dateFromFileName("holiday.jpg"))

    @Test fun shortCameraCounterIsNotADate() = assertNull(FileDates.dateFromFileName("IMG_1234.JPG"))

    @Test fun epochMillisNameIsNotADate() =
        assertNull(FileDates.dateFromFileName("FB_IMG_1710253800000.jpg"))

    @Test fun invalidMonth() = assertNull(FileDates.dateFromFileName("IMG-20251399-WA0001.jpg"))

    @Test fun invalidDay() = assertNull(FileDates.dateFromFileName("IMG-20250230-WA0001.jpg"))

    @Test fun dateGluedToMoreDigitsIsNotADate() =
        assertNull(FileDates.dateFromFileName("202503121.jpg"))

    @Test fun yearOutOfRange() = assertNull(FileDates.dateFromFileName("IMG-18000312-WA0001.jpg"))

    // resolve(): date taken → name → modified (D-029)

    private val zone = ZoneId.of("Europe/Rome")

    @Test fun resolvePrefersDateTaken() =
        assertEquals(1_000L, FileDates.resolve(1_000L, "IMG-20250312-WA0001.jpg", 2_000L, zone))

    @Test fun resolveFallsBackToName() =
        assertEquals(
            at(2025, 3, 12).atZone(zone).toInstant().toEpochMilli(),
            FileDates.resolve(null, "IMG-20250312-WA0001.jpg", 2_000L, zone),
        )

    @Test fun resolveTreatsZeroDateTakenAsMissing() =
        assertEquals(
            at(2025, 3, 12).atZone(zone).toInstant().toEpochMilli(),
            FileDates.resolve(0L, "IMG-20250312-WA0001.jpg", 2_000L, zone),
        )

    @Test fun resolveFallsBackToModified() =
        assertEquals(2_000L, FileDates.resolve(null, "holiday.jpg", 2_000L, zone))
}
