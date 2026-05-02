package com.safeminds.watch.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.safeminds.watch.model.AccelSample

class AccelerometerCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var onSampleCollected: ((AccelSample) -> Unit)? = null

    fun start() {
        accelerometer?.let {
            // ~25 Hz ≈ 40,000 microseconds
            sensorManager.registerListener(this, it, 40_000)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        onSampleCollected?.invoke(
            AccelSample(
                timestamp = System.currentTimeMillis(),
                x = event.values[0],
                y = event.values[1],
                z = event.values[2]
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
