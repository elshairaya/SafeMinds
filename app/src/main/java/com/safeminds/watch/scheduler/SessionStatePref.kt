package com.safeminds.watch.scheduler

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object SessionStatePref {

    private const val PREFERENCE_NAME="SessionStatusPreferences"
    private const val RUNNING_SESSION_KEY="RunningSession"

    private fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    }

    fun runningSession(context: Context): MonitoringSessionType?{
        val result=preferences(context).getString(RUNNING_SESSION_KEY,null) ?: return null
        return try{
            MonitoringSessionType.valueOf(result)
        }
        catch (e: IllegalArgumentException){
            null
        }
    }

    //check if night session is active
    fun isNightSessionRunning (context: Context): Boolean {
        return runningSession(context) == MonitoringSessionType.NIGHT_SESSION
    }

    //check if hourly check session is active
    fun isHourlyCheckRunning (context: Context): Boolean {
        return runningSession(context) == MonitoringSessionType.HOURLY_CHECK_SESSION

    }

    //marks that the session is started
    fun setStarted(context: Context, sessionType: MonitoringSessionType){
        preferences(context).edit {
            putString(RUNNING_SESSION_KEY, sessionType.name)
        }
        }


    fun clear(context: Context){
        preferences(context).edit {
            remove(RUNNING_SESSION_KEY)
        }
    }
}