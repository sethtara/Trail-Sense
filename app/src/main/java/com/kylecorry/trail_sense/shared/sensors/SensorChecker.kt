package com.kylecorry.trail_sense.shared.sensors

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

class SensorChecker(private val context: Context) {

    private val sensorManager = context.getSystemService(Service.SENSOR_SERVICE) as SensorManager

    fun hasBarometer(): Boolean {
        val sensors = sensorManager.getSensorList(Sensor.TYPE_PRESSURE)
        return sensors.isNotEmpty()
    }

    fun hasGPS(): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

}