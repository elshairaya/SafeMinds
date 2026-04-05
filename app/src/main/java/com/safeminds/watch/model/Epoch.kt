package com.safeminds.watch.model

//includes movement score and average heart rate

data class Epoch(
    val epochStart: Long,
    val movementScore: Float,
    val hrMean: Float?
)
