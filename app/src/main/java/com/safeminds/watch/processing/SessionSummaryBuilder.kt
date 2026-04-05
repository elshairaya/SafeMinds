package com.safeminds.watch.processing

import com.safeminds.watch.model.Epoch
import com.safeminds.watch.model.SessionSummary

class SessionSummaryBuilder {
    private val epochs = mutableListOf<Epoch>()

    fun addEpoch(epoch: Epoch){
        epochs.add(epoch)
    }
    fun build(): SessionSummary{
        val movements = epochs.map {
            it.movementScore
        }
        val movementMean = movements.average().toFloat()
        val movementVariance = movements.map {
            (it-movementMean)*(it-movementMean)}.average().toFloat()
        val heartRateValues = epochs.mapNotNull {
            it.hrMean
        }
        val heartRateMean = if(heartRateValues.isNotEmpty()) heartRateValues.average().toFloat() else null
        val heartRateMin = heartRateValues.minOrNull()
        val heartRateMax = heartRateValues.maxOrNull()

        return SessionSummary(
            hrMean  = heartRateMean, hrMin = heartRateMin, hrMax = heartRateMax,
            movementMean = movementMean, movementVariance = movementVariance, totalEpochs = epochs.size)


    }
}
