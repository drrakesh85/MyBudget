package com.hackerai.mybudget.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    private val zoneId = ZoneId.systemDefault()

    private val dateFormatterPatterns = listOf(
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d-M-yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d-M-yy", Locale.US),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d/M/yy", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
    )

    fun parseDateToMillis(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val trimmed = dateStr.trim()

        for (formatter in dateFormatterPatterns) {
            try {
                return LocalDate.parse(trimmed, formatter)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return 0L
    }

    fun formatDateHeader(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val trimmed = dateStr.trim()

        for (formatter in dateFormatterPatterns) {
            try {
                val date = LocalDate.parse(trimmed, formatter)
                return date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy EEE", Locale.US)).uppercase()
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return trimmed
    }
}
