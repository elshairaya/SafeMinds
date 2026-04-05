package com.safeminds.watch.processing

// calculate the difference between the movements

class MovementProcessor {

    private var lastMagnitude: Float? = null

    fun processMagnitude(magnitude: Float): Float{
        val movement = if(lastMagnitude == null){
            0f
        }
        else{
            kotlin.math.abs(magnitude - lastMagnitude!!)
        }
        lastMagnitude = magnitude
        return movement
    }

}