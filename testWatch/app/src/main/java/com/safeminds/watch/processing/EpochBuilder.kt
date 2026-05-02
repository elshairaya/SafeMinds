package com.safeminds.watch.processing

import com.safeminds.watch.model.Epoch

class EpochBuilder {
    private val epochLengthMillis = 30_000L
    private var currentEpochStart: Long? = null
    private val movementBuffer = mutableListOf<Float>()
    private val heartRateBuffer = mutableListOf<Float>()
    var onEpochReady: ((Epoch) -> Unit)? = null

    fun addMovement(timestamp: Long, movement: Float){
        if(currentEpochStart == null){
            currentEpochStart = timestamp
        }
        movementBuffer.add(movement)
        val elapsed = timestamp - currentEpochStart!!
        if(elapsed >= epochLengthMillis){
            val meanMovement = movementBuffer.average().toFloat()

            val hrMean = if(heartRateBuffer.isNotEmpty()){
                heartRateBuffer.average().toFloat()
            }
            else{
                null
            }

            val epoch = Epoch(
                epochStart = currentEpochStart!!,
                movementScore = meanMovement,
                hrMean = hrMean
            )
            // old hr code
            //val epoch = Epoch(epochStart = currentEpochStart!!, movementScore = meanMovement, hrMean = null)

             onEpochReady?.invoke(epoch)

            movementBuffer.clear()
            heartRateBuffer.clear()
            currentEpochStart = timestamp



        }
    }

    fun addHeartRate(bpm: Float){
        heartRateBuffer.add(bpm)
    }
}