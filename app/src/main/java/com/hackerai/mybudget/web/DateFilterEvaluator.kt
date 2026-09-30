package com.hackerai.mybudget.web

import com.hackerai.mybudget.data.DateUtils
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object DateFilterEvaluator {

    fun evaluateDateFilter(
        expDateMillis: Long,
        dateFilter: String?,
        fromDateStr: String? = null,
        toDateStr: String? = null,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        val filter = dateFilter?.uppercase()?.trim() ?: "ALL"
        if (filter == "ALL" || filter == "ALL TILL DATE" || filter == "ALL_TILL_DATE" || filter.isBlank()) {
            return true
        }

        val zone = now.zone
        val todayLocalDate = now.toLocalDate()

        val (startMillis, endMillis) = when (filter) {
            "TODAY" -> {
                val start = todayLocalDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = todayLocalDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }
            "THIS_WEEK", "THIS WEEK" -> {
                // Monday is the first day of the week (ISO-8601 value 1 = Monday)
                val dayOfWeek = todayLocalDate.dayOfWeek.value
                val monday = todayLocalDate.minusDays((dayOfWeek - 1).toLong())
                val sunday = monday.plusDays(6)
                val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = sunday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }
            "THIS_MONTH", "THIS MONTH" -> {
                val monthStart = todayLocalDate.withDayOfMonth(1)
                val nextMonthStart = monthStart.plusMonths(1)
                val start = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = nextMonthStart.atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }
            "THIS_YEAR", "THIS YEAR" -> {
                val yearStart = todayLocalDate.withDayOfYear(1)
                val nextYearStart = yearStart.plusYears(1)
                val start = yearStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = nextYearStart.atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }
            "CUSTOM", "CUSTOM DURATION" -> {
                if (fromDateStr.isNullOrBlank() || toDateStr.isNullOrBlank()) return false
                val fromMillis = DateUtils.parseDateToMillis(fromDateStr)
                val toMillis = DateUtils.parseDateToMillis(toDateStr)
                if (fromMillis <= 0L || toMillis <= 0L) return false
                if (fromMillis > toMillis) return false
                // Inclusive To Date: end of the To Date day (23:59:59.999)
                val endInclusive = toMillis + (24 * 60 * 60 * 1000 - 1)
                fromMillis to endInclusive
            }
            else -> return true
        }

        return expDateMillis in startMillis..endMillis
    }
}
