package com.kylecorry.trail_sense.animals.domain

import com.kylecorry.trail_sense.astronomy.domain.moon.AltitudeMoonTimesCalculator
import com.kylecorry.trail_sense.astronomy.domain.moon.MoonStateCalculator
import com.kylecorry.trail_sense.shared.Coordinate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object SolunarCalculator {

    fun calculate(location: Coordinate, date: LocalDate = LocalDate.now()): List<SolunarTime> {
        val todayMoon = AltitudeMoonTimesCalculator().calculate(location, date)
        val tomorrowMoon = AltitudeMoonTimesCalculator().calculate(location, date.plusDays(1))
        val yesterdayMoon = AltitudeMoonTimesCalculator().calculate(location, date.minusDays(1))
        val isMoonUp = MoonStateCalculator().isUp(todayMoon, LocalTime.MIN)

        val solunarTimes = mutableListOf<SolunarTime>()

        // Minor times
        if (todayMoon.up != null){
            val times = clampToDate(todayMoon.up, todayMoon.up.plusHours(1), date)
            solunarTimes.add(SolunarTime(times.first, times.second, SolunarPeriod.Minor, false))
        }

        if (todayMoon.down != null){
            val times = clampToDate(todayMoon.down, todayMoon.down.plusHours(1), date)
            solunarTimes.add(SolunarTime(times.first, times.second, SolunarPeriod.Minor, false))
        }

        // Major times
        if (isMoonUp){
            solunarTimes.addAll(listOfNotNull(
                getMajorTime(yesterdayMoon.up, todayMoon.down, date),
                getMajorTime(todayMoon.down, todayMoon.up ?: tomorrowMoon.up, date),
                getMajorTime(todayMoon.up ?: tomorrowMoon.up, tomorrowMoon.down, date)
            ))
        } else {
            solunarTimes.addAll(listOfNotNull(
                getMajorTime(yesterdayMoon.down, todayMoon.up, date),
                getMajorTime(todayMoon.up, todayMoon.down ?: tomorrowMoon.down, date),
                getMajorTime(todayMoon.down ?: tomorrowMoon.down, tomorrowMoon.up, date)
            ))
        }

        return solunarTimes.sortedBy { it.startTime }
    }

    fun getSolunarTime(times: List<SolunarTime>, current: LocalTime = LocalTime.now()): SolunarTime? {
        return times.firstOrNull {
            current.isAfter(it.startTime) && current.isBefore(it.endTime)
        }
    }

    private fun getMajorTime(moonTime1: LocalDateTime?, moonTime2: LocalDateTime?, date: LocalDate): SolunarTime? {
        if (moonTime1 == null || moonTime2 == null){
            return null
        }
        val start = middle(moonTime1, moonTime2)
        val end = start.plusHours(2)
        if (isOnDay(start, end, date)){
            val clamped = clampToDate(start, end, date)
            return SolunarTime(clamped.first, clamped.second, SolunarPeriod.Major, false)
        }
        return null
    }

    private fun clampToDate(start: LocalDateTime, end: LocalDateTime, date: LocalDate): Pair<LocalTime, LocalTime> {
        val startTime = if (start.toLocalDate() != date) LocalTime.MIN else start.toLocalTime()
        val endTime = if (end.toLocalDate() != date) LocalTime.MAX else end.toLocalTime()
        return Pair(startTime, endTime)
    }

    private fun isOnDay(start: LocalDateTime, end: LocalDateTime, date: LocalDate): Boolean {
        return start.toLocalDate() == date || end.toLocalDate() == date
    }

    private fun middle(start: LocalDateTime, end: LocalDateTime): LocalDateTime {
        return start.plus(Duration.between(start, end).dividedBy(2))
    }

}