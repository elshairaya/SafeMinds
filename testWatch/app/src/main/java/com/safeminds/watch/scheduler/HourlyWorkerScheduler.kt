package com.safeminds.watch.scheduler
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

object HourlyWorkerScheduler {
    private const val WORKER_NAME="HourlyWorker" //unique identifier

    fun register(context: Context){ // this is a function that register the worker with WorkManager (Android's system)

        val request= PeriodicWorkRequestBuilder<HourlySessionWorker>(15, //request that runs the worker class every 15 min
            java.util.concurrent.TimeUnit.MINUTES).build()
        //get access to workManager to update
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORKER_NAME, //register worker with unique name and if the name exist to update the new request
            ExistingPeriodicWorkPolicy.UPDATE,request)
    }
}