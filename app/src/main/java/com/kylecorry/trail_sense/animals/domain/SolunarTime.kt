package com.kylecorry.trail_sense.animals.domain

import java.time.Duration
import java.time.LocalTime

data class SolunarTime(val startTime: LocalTime, val endTime: LocalTime, val period: SolunarPeriod, val heightened: Boolean = false){
    val duration: Duration = Duration.between(startTime, endTime)
}