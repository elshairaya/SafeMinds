package com.safeminds.watch.scheduler

import android.content.Context
import android.util.Log
import com.safeminds.watch.service.Bridge
import com.safeminds.watch.service.MonitoringService
import com.safeminds.watch.storage.ScheduleStorage
import java.util.Calendar

object Controller {

    private const val TAG = "Controller"

    private fun isNightSession(current: Int, startTime: Int, endTime: Int): Boolean {
        val result = if (startTime <= endTime) {
            current in startTime until endTime
        } else {
            current >= startTime || current < endTime
        }

        Log.d(
            TAG,
            "isNightSession -> current=$current, startTime=$startTime, endTime=$endTime, result=$result"
        )

        return result
    }

    private fun currentTimeSlot(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val day = calendar.get(Calendar.DAY_OF_YEAR)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val slot = "$year-$day-$hour"
        Log.d(TAG, "currentTimeSlot -> $slot")
        return slot
    }

    private fun currentTimeMin(): Int {
        val currentTime = Calendar.getInstance()
        val minutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
        Log.d(TAG, "currentTimeMin -> $minutes")
        return minutes
    }

    private fun nightSessionPriority(context: Context) {
        Log.d(TAG, "nightSessionPriority() called")

        val savedSession = SessionStatePref.runningSession(context)

        val reallyRunning =
            MonitoringService.isServiceRunning &&
                    savedSession == MonitoringSessionType.NIGHT_SESSION

        if (reallyRunning) {
            Log.d(TAG, "Night session already running -> no action")
            return
        }

        val staleNightState =
            savedSession == MonitoringSessionType.NIGHT_SESSION &&
                    !MonitoringService.isServiceRunning

        if (staleNightState) {
            Log.d(TAG, "Stale NIGHT_SESSION detected -> clearing")
            SessionStatePref.clear(context)
        }

        val hourlyRunning =
            MonitoringService.isServiceRunning &&
                    savedSession == MonitoringSessionType.HOURLY_CHECK_SESSION

        if (hourlyRunning) {
            Log.d(TAG, "Stopping hourly before night")
            Bridge.stopSession(context)
        }

        Log.d(TAG, "Starting NIGHT_SESSION")
        Bridge.startSession(context, MonitoringSessionType.NIGHT_SESSION)
    }
    fun handleHourlyCheckAction(context: Context): Boolean {
        Log.d(TAG, "handleHourlyCheckAction() called")

        val configuration = ScheduleStorage.getSchedule(context)
        Log.d(
            TAG,
            "Schedule -> nightStart=${configuration.nightStartTime}, nightEnd=${configuration.nightEndTime}, hourlyEnabled=${configuration.isHourlyEnabled}, hourlyDuration=${configuration.hourlyCheckDuration}"
        )

        val currentTime = currentTimeMin()
        val inNightSession = isNightSession(
            currentTime,
            configuration.nightStartTime,
            configuration.nightEndTime
        )

        if (inNightSession) {
            Log.d(TAG, "Currently inside night window -> applying night session priority")
            nightSessionPriority(context)
            return false
        }

        if (!configuration.isHourlyEnabled) {
            Log.d(TAG, "Hourly check is disabled -> no hourly session will start")
            return false
        }

        val savedSession = SessionStatePref.runningSession(context)

        val staleHourly = savedSession == MonitoringSessionType.HOURLY_CHECK_SESSION &&
                !MonitoringService.isServiceRunning

        if (staleHourly){
            Log.d(TAG, "Clearing stale hourly session")
            SessionStatePref.clear(context)
        }

        val nightRunning =
            MonitoringService.isServiceRunning &&
                    savedSession == MonitoringSessionType.NIGHT_SESSION

        if (nightRunning) {
            Log.d(TAG, "Night session already running -> hourly check blocked")
            return false
        }


        val staleNight =
            savedSession == MonitoringSessionType.NIGHT_SESSION &&
                    !MonitoringService.isServiceRunning

        if (staleNight) {
            Log.d(TAG, "Clearing stale night session before hourly")
            SessionStatePref.clear(context)
        }

        if (SessionStatePref.isHourlyCheckRunning(context)) {
            Log.d(TAG, "Hourly check already running -> no duplicate start")
            return false
        }

        val currentTimeSlot = currentTimeSlot()
        val lastSlot = ScheduleStorage.getLastHourlyCheck(context)

        Log.d(TAG, "Hourly slot check -> currentSlot=$currentTimeSlot, lastSlot=$lastSlot")

        if (currentTimeSlot == lastSlot) {
            Log.d(TAG, "Hourly check already ran in this hour -> skip")
            return false
        }

        Log.d(TAG, "Starting HOURLY_CHECK_SESSION")
        Bridge.startSession(context, MonitoringSessionType.HOURLY_CHECK_SESSION)

        ScheduleStorage.setLastHourlyCheck(context, currentTimeSlot)
        Log.d(TAG, "Saved last hourly check slot = $currentTimeSlot")

        return true
    }

    fun checkNightSessionNow(context: Context) {
        Log.d(TAG, "checkNightSessionNow() called")

        val configuration = ScheduleStorage.getSchedule(context)
        Log.d(
            TAG,
            "Schedule -> nightStart=${configuration.nightStartTime}, nightEnd=${configuration.nightEndTime}"
        )

        val currentTime = currentTimeMin()
        val inNightSession = isNightSession(
            currentTime,
            configuration.nightStartTime,
            configuration.nightEndTime
        )

        Log.d(TAG, "Night window evaluation -> inNightSession=$inNightSession")

        if (inNightSession) {
            Log.d(TAG, "Inside night window -> ensure night session is active")
            nightSessionPriority(context)
        } else {
            Log.d(TAG, "Outside night window")

            val savedSession = SessionStatePref.runningSession(context)

            val reallyRunning =
                MonitoringService.isServiceRunning &&
                        savedSession == MonitoringSessionType.NIGHT_SESSION

            if (reallyRunning) {
                Bridge.stopSession(context)
            } else if (savedSession == MonitoringSessionType.NIGHT_SESSION) {
                Log.d(TAG, "Clearing stale night session")
                SessionStatePref.clear(context)

            } else {
                Log.d(TAG, "No night session running -> nothing to stop")
            }
        }
    }
}