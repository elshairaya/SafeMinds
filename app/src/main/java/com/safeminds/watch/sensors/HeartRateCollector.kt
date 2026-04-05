package com.safeminds.watch.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.safeminds.watch.model.HeartRateSample

class HeartRateCollector(cont: Context): SensorEventListener {
    private val sensorManager = cont.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    var onHeartRate:((HeartRateSample) -> Unit)? = null

    fun start(){
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    fun stop(){
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val bpm = event.values[0]
        val sample = HeartRateSample(timestamp = System.currentTimeMillis(), beatsPerMinute = bpm)
        onHeartRate?.invoke(sample)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}