package app.galleria.home

import app.galleria.data.FolderVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRulesTest {
    private fun excluded(path: String) = assertTrue(path, FolderRules.isMessagingOrDownload(path))
    private fun included(path: String) = assertFalse(path, FolderRules.isMessagingOrDownload(path))

    @Test fun whatsAppModernPath() = excluded("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/")
    @Test fun whatsAppVideo() = excluded("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/")
    @Test fun whatsAppLegacyPath() = excluded("WhatsApp/Media/WhatsApp Images/")
    @Test fun whatsAppBusiness() = excluded("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/")
    @Test fun telegramPictures() = excluded("Pictures/Telegram/")
    @Test fun telegramAndroidMedia() = excluded("Android/media/org.telegram.messenger/Telegram/Telegram Images/")
    @Test fun signal() = excluded("Pictures/Signal/")
    @Test fun instagram() = excluded("Pictures/Instagram/")
    @Test fun messenger() = excluded("Pictures/Messenger/")
    @Test fun download() = excluded("Download/")
    @Test fun downloadSubfolder() = excluded("Download/Holiday/")
    @Test fun caseIsIgnored() = excluded("pictures/TELEGRAM/")

    @Test fun camera() = included("DCIM/Camera/")
    @Test fun screenshotsStayIn() = included("Pictures/Screenshots/")
    @Test fun dcimScreenshots() = included("DCIM/Screenshots/")
    @Test fun userFolder() = included("Pictures/Holiday 2025/")
    @Test fun nameOnlyContainingAKnownWord() = included("Pictures/My Signals/")

    @Test fun defaultVisibility() {
        assertEquals(FolderVisibility.ExcludedFromAll, FolderRules.defaultVisibility("Pictures/Telegram/"))
        assertEquals(FolderVisibility.Normal, FolderRules.defaultVisibility("DCIM/Camera/"))
    }
}
