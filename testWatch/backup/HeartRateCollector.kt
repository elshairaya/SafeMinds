package com.safeminds.watch.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.safeminds.watch.model.HeartRateSample

class HeartRateCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    var onHeartRate: ((HeartRateSample) -> Unit)? = null

    fun isSensorAvailable(): Boolean = heartRateSensor != null

    fun start() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: Log.w(TAG, "Heart rate sensor is not available on this watch")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        if (sensorEvent.sensor.type != Sensor.TYPE_HEART_RATE) return
        val bpm = sensorEvent.values.firstOrNull() ?: return
        if (bpm <= 0f) return

        onHeartRate?.invoke(
            HeartRateSample(
                timestamp = System.currentTimeMillis(),
                beatsPerMinute = bpm
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val TAG = "HeartRateCollector"
    }
}
