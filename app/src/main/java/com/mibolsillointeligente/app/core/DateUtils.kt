package com.mibolsillointeligente.app.core

import com.mibolsillointeligente.app.domain.model.PeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class DateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

object DateUtils {
    private val mexicoLocale: Locale = Locale.forLanguageTag("es-MX")
    private val shortFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", mexicoLocale)
    private val compactFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", mexicoLocale)
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun formatShort(epochDay: Long): String {
        return LocalDate.ofEpochDay(epochDay).format(shortFormatter)
    }

    fun formatCompact(epochDay: Long): String {
        return LocalDate.ofEpochDay(epochDay).format(compactFormatter)
    }

    fun formatIso(epochDay: Long): String {
        return LocalDate.ofEpochDay(epochDay).format(isoFormatter)
    }

    fun formatRangeLabel(range: DateRange): String {
        return "${range.start.format(shortFormatter)} - ${range.end.format(shortFormatter)}"
    }

    /**
     * Calcula el rango de fechas para el periodo seleccionado tomando en cuenta
     * los ciclos de pago habituales en México (semanal, quincenal 1-15 / 16-fin, mensual).
     */
    fun getPeriodRange(
        periodType: PeriodType,
        referenceDate: LocalDate = LocalDate.now(),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ): DateRange {
        return when (periodType) {
            PeriodType.WEEKLY -> {
                val start = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val end = start.plusDays(6)
                DateRange(start, end)
            }
            PeriodType.BIWEEKLY -> {
                val ym = YearMonth.from(referenceDate)
                if (referenceDate.dayOfMonth <= 15) {
                    DateRange(ym.atDay(1), ym.atDay(15))
                } else {
                    DateRange(ym.atDay(16), ym.atEndOfMonth())
                }
            }
            PeriodType.MONTHLY -> {
                val ym = YearMonth.from(referenceDate)
                DateRange(ym.atDay(1), ym.atEndOfMonth())
            }
            PeriodType.CUSTOM -> {
                val start = customStart ?: referenceDate.withDayOfMonth(1)
                val end = customEnd ?: referenceDate
                if (end.isBefore(start)) DateRange(end, start) else DateRange(start, end)
            }
        }
    }

    fun getPreviousPeriodRange(
        periodType: PeriodType,
        currentRange: DateRange
    ): DateRange {
        return when (periodType) {
            PeriodType.WEEKLY -> DateRange(currentRange.start.minusWeeks(1), currentRange.end.minusWeeks(1))
            PeriodType.BIWEEKLY -> {
                val prevRef = currentRange.start.minusDays(1)
                getPeriodRange(PeriodType.BIWEEKLY, prevRef)
            }
            PeriodType.MONTHLY -> {
                val prevYm = YearMonth.from(currentRange.start).minusMonths(1)
                DateRange(prevYm.atDay(1), prevYm.atEndOfMonth())
            }
            PeriodType.CUSTOM -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(currentRange.start, currentRange.end) + 1
                DateRange(currentRange.start.minusDays(days), currentRange.start.minusDays(1))
            }
        }
    }
}
