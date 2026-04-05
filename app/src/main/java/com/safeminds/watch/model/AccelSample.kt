package com.safeminds.watch.model

// accelerometer sample for motion data including x,y,z axes and timestamp
// for user movement patterns and detecting behavioral changes

data class AccelSample(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)
