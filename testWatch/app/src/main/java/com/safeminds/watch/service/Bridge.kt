package com.safeminds.watch.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.safeminds.watch.scheduler.MonitoringSessionType

object Bridge {

    fun startSession(context: Context, sessionType: MonitoringSessionType) {
        val intentMessage = Intent(
            context,
            MonitoringService::class.java
        ).apply {
            action = MonitoringService.ACTION_START_SESSION
            putExtra(MonitoringService.EXTRA_SESSION_TYPE, sessionType.name)
        }

        ContextCompat.startForegroundService(context, intentMessage)
    }

    fun stopSession(context: Context) {
        val intentMessage = Intent(
            context,
            MonitoringService::class.java
        ).apply {
            action = MonitoringService.ACTION_STOP_SESSION
        }

        context.startService(intentMessage)
    }
}