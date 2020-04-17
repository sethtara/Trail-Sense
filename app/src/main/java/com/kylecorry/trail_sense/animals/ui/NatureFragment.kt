package com.kylecorry.trail_sense.animals.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.animals.domain.SolunarCalculator
import com.kylecorry.trail_sense.animals.domain.SolunarPeriod
import com.kylecorry.trail_sense.animals.domain.SolunarTime
import com.kylecorry.trail_sense.astronomy.ui.DayTimeChartView
import com.kylecorry.trail_sense.shared.sensors.GPS
import com.kylecorry.trail_sense.shared.sensors.IGPS
import com.kylecorry.trail_sense.shared.toDisplayFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import kotlin.concurrent.fixedRateTimer

class NatureFragment : Fragment() {

    private lateinit var solunarChart: DayTimeChartView
    private lateinit var activityTxt: TextView
    private lateinit var todayMajorTxt: TextView
    private lateinit var todayMinorTxt: TextView
    private lateinit var tomorrowMajorTxt: TextView
    private lateinit var tomorrowMinorTxt: TextView

    private lateinit var gps: IGPS
    private lateinit var timer: Timer
    private lateinit var handler: Handler

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_nature, container, false)
        solunarChart = view.findViewById(R.id.solunar_chart)
        activityTxt = view.findViewById(R.id.activity_indicator)
        todayMajorTxt = view.findViewById(R.id.solunar_major_times)
        todayMinorTxt = view.findViewById(R.id.solunar_minor_times)
        tomorrowMajorTxt = view.findViewById(R.id.solunar_major_times_tomorrow)
        tomorrowMinorTxt = view.findViewById(R.id.solunar_minor_times_tomorrow)

        gps = GPS(context!!)
        return view
    }

    override fun onResume() {
        super.onResume()
        gps.start(this::onLocationUpdate)
        handler = Handler(Looper.getMainLooper())
        timer = fixedRateTimer(period = 1000 * 60) {
            handler.post { updateSolunarUI() }
        }
        updateSolunarUI()
    }

    override fun onPause() {
        super.onPause()
        gps.stop(this::onLocationUpdate)
        timer.cancel()
    }

    private fun onLocationUpdate(): Boolean {
        updateSolunarUI()
        return false
    }

    private fun updateSolunarUI(){
        val todayTimes = SolunarCalculator.calculate(gps.location)
        val tomorrowTimes = SolunarCalculator.calculate(gps.location, LocalDate.now().plusDays(1))
        val current = SolunarCalculator.getSolunarTime(todayTimes, LocalTime.now())
        setCurrentSolunar(current)
        setSolunarForecast(todayTimes, tomorrowTimes)
        val colors = mutableListOf<Int>()
        for (time in todayTimes){
            colors.add(R.color.night)
            colors.add(if (time.period == SolunarPeriod.Major) R.color.colorPrimary else R.color.colorAccent)
        }
        solunarChart.setColors(colors.map { resources.getColor(it, null) })
        solunarChart.display(todayTimes.flatMap { listOf(it.startTime, it.endTime) }, LocalTime.now())
    }

    private fun setSolunarForecast(today: List<SolunarTime>, tomorrow: List<SolunarTime>){
        todayMajorTxt.text = today.filter { it.period == SolunarPeriod.Major }.joinToString("\n") {
            "${it.startTime.toDisplayFormat(context!!)} - ${it.endTime.toDisplayFormat(context!!)}"
        }
        todayMinorTxt.text = today.filter { it.period == SolunarPeriod.Minor }.joinToString("\n") {
            "${it.startTime.toDisplayFormat(context!!)} - ${it.endTime.toDisplayFormat(context!!)}"
        }
        tomorrowMajorTxt.text = tomorrow.filter { it.period == SolunarPeriod.Major }.joinToString("\n") {
            "${it.startTime.toDisplayFormat(context!!)} - ${it.endTime.toDisplayFormat(context!!)}"
        }
        tomorrowMinorTxt.text = tomorrow.filter { it.period == SolunarPeriod.Minor }.joinToString("\n") {
            "${it.startTime.toDisplayFormat(context!!)} - ${it.endTime.toDisplayFormat(context!!)}"
        }
    }

    private fun setCurrentSolunar(current: SolunarTime?) {
        if (current == null){
            solunarChart.setCursorImageColor(resources.getColor(R.color.night, null))
            activityTxt.text = getString(R.string.animal_activity_low)
            return
        }

        when (current.period){
            SolunarPeriod.Minor -> {
                solunarChart.setCursorImageColor(resources.getColor(R.color.colorAccent, null))
                activityTxt.text = getString(R.string.animal_activity_moderate)
            }
            SolunarPeriod.Major -> {
                solunarChart.setCursorImageColor(resources.getColor(R.color.colorPrimary, null))
                activityTxt.text = getString(R.string.animal_activity_high)
            }
        }

    }
}
