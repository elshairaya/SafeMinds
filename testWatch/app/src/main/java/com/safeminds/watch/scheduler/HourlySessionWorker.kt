package com.safeminds.watch.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safeminds.watch.service.Bridge
import com.safeminds.watch.storage.ScheduleStorage
import kotlinx.coroutines.delay

// this class recheck current status (night session exist) then it calls handleHourlyCheckAction function to ask if hourly check should run

class HourlySessionWorker (
    context: Context,params: WorkerParameters)
    : CoroutineWorker(context,params){   // to run class as a background worker
    override suspend fun doWork(): Result { //suspend used because it's asynchronous function
        return try {
            Controller.checkNightSessionNow(applicationContext)
            val start= Controller.handleHourlyCheckAction(applicationContext)
            if(start) {
                val duration = ScheduleStorage.getSchedule(applicationContext).hourlyCheckDuration
                delay(duration * 60 * 1000L)

                if (SessionStatePref.isHourlyCheckRunning(applicationContext)) {
                    Bridge.stopSession(applicationContext)
                }
            }
            Result.success()
        }
        catch (ex: Exception){
            Result.retry()
        }}
}
