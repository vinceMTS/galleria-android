package app.galleria.media

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The date of a file (D-029), in this order:
 * date taken (MediaStore `DATE_TAKEN` / EXIF) → date in the file name → last modified.
 *
 * Pure logic, no Android dependencies, so it is tested on the PC (D-031).
 */
object FileDates {

    // A date like 20250312, 2025-03-12 or 2025_03_12 not glued to other digits,
    // optionally followed by a time like 153000, -15-30-00 or _15.30.00.
    private val dateInName = Regex(
        """(?<!\d)(19[89]\d|20\d{2})[-_.]?(\d{2})[-_.]?(\d{2})""" +
            """(?:[-_ T.]?(\d{2})[-_.:]?(\d{2})[-_.:]?(\d{2}))?"""
    )

    /**
     * Reads the date from names such as `IMG-20250312-WA0001`, `Screenshot_20250312-153000`,
     * `IMG_20250312_153000`, `VID_20250312_153000`, `PXL_20250312_153000123`.
     * WhatsApp-style names carry only the day: the time is then midnight.
     * Returns null when the name has no valid date.
     */
    fun dateFromFileName(fileName: String): LocalDateTime? {
        val name = fileName.substringAfterLast('/')
        for (match in dateInName.findAll(name)) {
            val (year, month, day, hour, minute, second) = match.destructured
            val hasTime = hour.isNotEmpty()
            // "202503121" is a run of digits, not a date.
            if (!hasTime && name.getOrNull(match.range.last + 1)?.isDigit() == true) continue
            val date = try {
                LocalDateTime.of(
                    year.toInt(), month.toInt(), day.toInt(),
                    if (hasTime) hour.toInt() else 0,
                    if (hasTime) minute.toInt() else 0,
                    if (hasTime) second.toInt() else 0,
                )
            } catch (_: DateTimeException) {
                // A valid day with an impossible time (e.g. 20250312_999999): keep the day.
                if (hasTime) dayOnly(year, month, day) else null
            }
            if (date != null) return date
        }
        return null
    }

    /**
     * @param dateTakenMillis MediaStore `DATE_TAKEN` in milliseconds, null or ≤ 0 if missing.
     * @param dateModifiedMillis last modified, in milliseconds (MediaStore `DATE_MODIFIED` is in seconds).
     * @return the file's date in epoch milliseconds.
     */
    fun resolve(
        dateTakenMillis: Long?,
        fileName: String,
        dateModifiedMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (dateTakenMillis != null && dateTakenMillis > 0) return dateTakenMillis
        dateFromFileName(fileName)?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        return dateModifiedMillis
    }

    private fun dayOnly(year: String, month: String, day: String): LocalDateTime? = try {
        LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), 0, 0)
    } catch (_: DateTimeException) {
        null
    }
}
