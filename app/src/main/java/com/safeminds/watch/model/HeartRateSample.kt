package com.safeminds.watch.model

// heart rate sample collected from the smartwatch sensor

data class HeartRateSample(
    val timestamp: Long,
    val beatsPerMinute: Float
)
