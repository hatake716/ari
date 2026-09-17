package io.github.hatake716.ari

import kotlin.math.floor

/** A repeating, non-leap year, starting May 1. Derived from saved biological time. */
data class SeasonDate(val month: Int, val dayOfMonth: Int) {
    val season: String get() = when(month) {
        3, 4, 5 -> "春"
        6, 7, 8 -> "夏"
        9, 10, 11 -> "秋"
        else -> "冬"
    }
    val label get() = "${month}月${dayOfMonth}日"
    val asset get() = "seasons/month-${month.toString().padStart(2, '0')}.webp"
}

object SeasonCalendar {
    private val monthLengths = intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
    const val START_DAY = 120 // May 1, zero-based.

    fun date(elapsedDays: Double): SeasonDate {
        require(elapsedDays.isFinite() && elapsedDays >= 0)
        var day = ((floor(elapsedDays) % 365).toInt() + START_DAY) % 365
        monthLengths.forEachIndexed { index, length ->
            if (day < length) return SeasonDate(index + 1, day + 1)
            day -= length
        }
        error("Calendar day outside year")
    }
}
