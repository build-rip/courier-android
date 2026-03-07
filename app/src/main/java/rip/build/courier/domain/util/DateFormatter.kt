package rip.build.courier.domain.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateFormatter {

    private val iso8601 = DateTimeFormatter.ISO_DATE_TIME

    fun parseInstant(iso: String?): Instant? {
        if (iso == null) return null
        return try {
            ZonedDateTime.parse(iso, iso8601).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun relativeTimestamp(iso: String?): String {
        val instant = parseInstant(iso) ?: return ""
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        val zone = ZoneId.systemDefault()

        val messageDate = instant.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)

        return when {
            duration.toMinutes() < 1 -> "now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m"
            duration.toHours() < 24 && messageDate == today -> {
                instant.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a"))
            }
            messageDate == yesterday -> "Yesterday"
            duration.toDays() < 7 -> {
                instant.atZone(zone).format(DateTimeFormatter.ofPattern("EEE"))
            }
            messageDate.year == today.year -> {
                instant.atZone(zone).format(DateTimeFormatter.ofPattern("MMM d"))
            }
            else -> {
                instant.atZone(zone).format(DateTimeFormatter.ofPattern("M/d/yy"))
            }
        }
    }

    fun timeOnly(iso: String?): String {
        val instant = parseInstant(iso) ?: return ""
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    fun readReceiptLabel(iso: String?): String {
        val instant = parseInstant(iso) ?: return "Read"
        val zone = ZoneId.systemDefault()
        val readDate = instant.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)

        val suffix = when (readDate) {
            today -> instant.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a"))
            yesterday -> "Yesterday"
            else -> instant.atZone(zone).format(DateTimeFormatter.ofPattern("M/d/yy"))
        }

        return "Read $suffix"
    }

    fun shouldShowTimestamp(currentIso: String?, previousIso: String?): Boolean {
        val current = parseInstant(currentIso) ?: return true
        val previous = parseInstant(previousIso) ?: return true
        return Duration.between(previous, current).abs().toMinutes() >= 2
    }

    fun dateSeparator(iso: String?): String {
        val instant = parseInstant(iso) ?: return ""
        val zone = ZoneId.systemDefault()
        val messageDate = instant.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)

        return when (messageDate) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                if (messageDate.year == today.year) {
                    instant.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                } else {
                    instant.atZone(zone).format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                }
            }
        }
    }
}
