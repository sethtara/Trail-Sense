package com.kylecorry.trail_sense.navigation.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.navigation.domain.Beacon
import com.kylecorry.trail_sense.navigation.infrastructure.BeaconDB
import com.kylecorry.trail_sense.navigation.infrastructure.GeoUriParser
import com.kylecorry.trail_sense.navigation.infrastructure.LocationSharesheet
import com.kylecorry.trail_sense.navigation.infrastructure.NavigationPreferences
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.sensors.*
import com.kylecorry.trail_sense.shared.switchToFragment
import kotlin.math.ceil

class NavigatorFragment(
    private val initialDestination: Beacon? = null,
    private val createBeacon: GeoUriParser.NamedCoordinate? = null
) : Fragment() {

    private lateinit var compass: ICompass
    private lateinit var gps: IGPS
    private lateinit var orientation: DeviceOrientation
    private lateinit var altimeter: IAltimeter

    // TODO: Extract ruler
    private var isRulerSetup = false
    private var areRulerTextViewsAligned = false

    private lateinit var roundCompass: ICompassView
    private lateinit var linearCompass: ICompassView
    private lateinit var userPrefs: UserPreferences

    private lateinit var navigationVM: NavigationViewModel

    private lateinit var locationTxt: TextView
    private lateinit var altitudeTxt: TextView
    private lateinit var azimuthTxt: TextView
    private lateinit var navigationTxt: TextView
    private lateinit var directionTxt: TextView
    private lateinit var beaconBtn: FloatingActionButton
    private lateinit var ruler: ConstraintLayout

    private lateinit var visibleCompass: ICompassView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_navigator, container, false)

        // Get views
        locationTxt = view.findViewById(R.id.location)
        altitudeTxt = view.findViewById(R.id.altitude)
        azimuthTxt = view.findViewById(R.id.compass_azimuth)
        navigationTxt = view.findViewById(R.id.navigation)
        directionTxt = view.findViewById(R.id.compass_direction)
        beaconBtn = view.findViewById(R.id.beaconBtn)
        ruler = view.findViewById(R.id.ruler)


        userPrefs = UserPreferences(requireContext())

        compass = if (userPrefs.navigation.useExperimentalCompass) {
            Compass(requireContext())
        } else {
            LegacyCompass(requireContext())
        }
        orientation = DeviceOrientation(requireContext())
        gps = GPS(requireContext())

        if (createBeacon != null) {
            switchToFragment(
                PlaceBeaconFragment(BeaconDB(requireContext()), gps, createBeacon),
                addToBackStack = true
            )
        }

        val altimeterMode = userPrefs.navigation.altimeter

        altimeter = when (altimeterMode) {
            NavigationPreferences.AltimeterMode.GPS -> {
                FusedAltimeter(gps, Barometer(requireContext()))
            }
            NavigationPreferences.AltimeterMode.Barometer -> {
                Barometer(requireContext())
            }
            NavigationPreferences.AltimeterMode.None -> {
                NullBarometer()
            }
        }

        navigationVM = NavigationViewModel(compass, gps, altimeter, orientation, userPrefs)
        navigationVM.beacon = initialDestination

        roundCompass = CompassView(
            view.findViewById(R.id.needle),
            view.findViewById(R.id.destination_star),
            view.findViewById(R.id.azimuth_indicator)
        )
        linearCompass = LinearCompassView(
            view.findViewById(R.id.linear_compass),
            view.findViewById(R.id.destination_star)
        )

        visibleCompass = linearCompass
        setVisibleCompass(roundCompass)

        locationTxt.setOnLongClickListener {
            val sender = LocationSharesheet(requireContext())
            sender.send(navigationVM.shareableLocation)
            true
        }

        beaconBtn.setOnClickListener {
            if (!navigationVM.showDestination) {
                switchToFragment(
                    BeaconListFragment(BeaconDB(requireContext()), gps),
                    addToBackStack = true
                )
            } else {
                navigationVM.beacon = null
                updateNavigator()
            }
        }

        return view
    }

    private fun setVisibleCompass(compass: ICompassView) {
        if (visibleCompass == compass) {
            if (compass != roundCompass) {
                roundCompass.visibility = View.INVISIBLE
            } else {
                linearCompass.visibility = View.INVISIBLE
            }
        }

        compass.beacon = navigationVM.destinationBearing
        compass.azimuth = navigationVM.azimuth
        visibleCompass.visibility = View.INVISIBLE
        visibleCompass = compass
        visibleCompass.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        compass.start(this::onCompassUpdate)
        gps.start(this::onLocationUpdate)
        altimeter.start(this::onAltitudeUpdate)
        orientation.start(this::onOrientationUpdate)

        val hasGPS = SensorChecker(requireContext()).hasGPS()

        if (!hasGPS) {
            beaconBtn.hide()
        } else {
            beaconBtn.show()
        }


        // Update the UI
        updateNavigator()
    }

    override fun onPause() {
        super.onPause()
        compass.stop(this::onCompassUpdate)
        gps.stop(this::onLocationUpdate)
        altimeter.stop(this::onAltitudeUpdate)
        orientation.stop(this::onOrientationUpdate)
    }

    private fun updateUI(){
        if (navigationVM.showLinearCompass) {
            setVisibleCompass(linearCompass)
        } else {
            setVisibleCompass(roundCompass)
        }

        if (navigationVM.rulerVisible) {
            setupRuler()
        } else {
            ruler.visibility = View.INVISIBLE
        }

        azimuthTxt.text = navigationVM.azimuthTxt
        directionTxt.text = navigationVM.azimuthDirection
        visibleCompass.azimuth = navigationVM.azimuth
        visibleCompass.beacon = navigationVM.destinationBearing

        altitudeTxt.text = navigationVM.altitude

        visibleCompass.beacon = navigationVM.destinationBearing
        navigationTxt.text = navigationVM.destination
        locationTxt.text = navigationVM.location
    }

    private fun onOrientationUpdate(): Boolean {
        updateUI()
        return true
    }

    private fun onCompassUpdate(): Boolean {
        updateUI()
        return true
    }

    private fun onAltitudeUpdate(): Boolean {
        updateUI()
        return true
    }

    private fun onLocationUpdate(): Boolean {
        updateUI()
        return navigationVM.showDestination
    }

    private fun setupRuler() {
        val dpi = resources.displayMetrics.densityDpi
        val height =
            ruler.height / dpi.toDouble() * if (userPrefs.distanceUnits == UserPreferences.DistanceUnits.Meters) 2.54 else 1.0

        if (height == 0.0 || context == null) {
            return
        }

        if (!isRulerSetup) {

            val theme = requireContext().theme
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            val arr = requireContext().obtainStyledAttributes(typedValue.data, IntArray(1) {
                android.R.attr.textColorPrimary
            })
            val primaryColor = arr.getColor(0, -1)
            arr.recycle()

            ruler.visibility = View.INVISIBLE
            for (i in 0..ceil(height).toInt() * 8) {
                val inches = i / 8.0
                val tv = TextView(context)
                val bar = View(context)
                bar.setBackgroundColor(primaryColor)
                val layoutParams = ConstraintLayout.LayoutParams(1, 4)
                bar.layoutParams = layoutParams
                when {
                    inches % 1.0 == 0.0 -> {
                        bar.layoutParams.width = 48
                        tv.text = inches.toInt().toString()
                    }
                    inches % 0.5 == 0.0 -> {
                        bar.layoutParams.width = 36
                    }
                    inches % 0.25 == 0.0 -> {
                        bar.layoutParams.width = 24
                    }
                    else -> {
                        bar.layoutParams.width = 12
                    }
                }
                bar.y =
                    ruler.height * (inches / height).toFloat() + resources.getDimensionPixelSize(R.dimen.ruler_top)
                if (!tv.text.isNullOrBlank()) {
                    tv.setTextColor(primaryColor)
                    ruler.addView(tv)
                    tv.y = bar.y
                    tv.x =
                        bar.layoutParams.width.toFloat() + resources.getDimensionPixelSize(R.dimen.ruler_label)
                }

                ruler.addView(bar)
            }
        } else if (!areRulerTextViewsAligned) {
            for (view in ruler.children) {
                if (view.height != 0) {
                    areRulerTextViewsAligned = true
                }
                view.y -= view.height / 2f
            }
        }

        isRulerSetup = true

        if (areRulerTextViewsAligned) {
            ruler.visibility = View.VISIBLE
        }
    }

    private fun updateNavigator() {
        if (navigationVM.showDestination) {
            // Navigating
            gps.start(this::onLocationUpdate)
            beaconBtn.setImageDrawable(context?.getDrawable(R.drawable.ic_cancel))
            onLocationUpdate()
        } else {
            // Not navigating
            beaconBtn.setImageDrawable(context?.getDrawable(R.drawable.ic_beacon))
            onLocationUpdate()
        }
    }

}
