package com.safeminds.watch.scheduler

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object SessionStatePref {

    private const val PREFERENCE_NAME="SessionStatusPreferences"
    private const val RUNNING_SESSION_KEY="RunningSession"
    private const val SESSION_ID="sessionID"

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

    //check if hourly check session is active
    fun isHourlyCheckRunning (context: Context): Boolean {
        return runningSession(context) == MonitoringSessionType.HOURLY_CHECK_SESSION

    }

    //marks that the session is started
    fun setStarted(context: Context, sessionType: MonitoringSessionType, sessionID: String){
        preferences(context).edit {
            putString(RUNNING_SESSION_KEY, sessionType.name)
            putString(SESSION_ID,sessionID)

        }
        }

    fun getID(context: Context): String?{
        return preferences(context).getString(SESSION_ID,null)
    }

    fun clear(context: Context){
        preferences(context).edit {
            remove(RUNNING_SESSION_KEY)
            remove(SESSION_ID)
        }
    }
}