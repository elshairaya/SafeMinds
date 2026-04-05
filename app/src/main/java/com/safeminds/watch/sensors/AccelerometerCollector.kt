package com.safeminds.watch.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.safeminds.watch.model.AccelSample

/* Collects raw accelerometer data.

Each reading is converted to AccelSample object, and sent
by a callback to the rest of the system.
 */


class AccelerometerCollector(context: Context) : SensorEventListener {

    // for managing access to sensors.
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // reference to the sensor
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // send collected samples using callback
    var onSampleCollected: ((AccelSample) -> Unit)? = null

    // start and stop listening to the sensor
    fun start(){
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    fun stop(){
        sensorManager.unregisterListener(this)
    }
    // when new data is available
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = Math.sqrt(((x*x)+(y*y)+(z*z)).toDouble()).toFloat()

        val sample = AccelSample(
            timestamp = System.currentTimeMillis(), x=x, y=y, z=z)
        onSampleCollected?.invoke(sample)

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

}