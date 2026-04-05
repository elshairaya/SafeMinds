package com.safeminds.watch.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.safeminds.watch.scheduler.ScheduleModel

object ScheduleStorage {

   // object is used to have single instance
   private const val PREFERENCE_NAME="SchedulePreferences" // the name of shared preferences file

           //they are unique identifiers for the stored values
           private const val NIGHT_START_KEY="nightStartTime"
           private const val NIGHT_END_KEY="nightEndTime"
           private const val IS_HOURLY_ENABLED_KEY="isHourlyEnabled"
           private const val HOURLY_CHECK_DURATION_KEY="hourlyCheckDuration"
            private const val LAST_HOURLY_CHECK_TIME="lastHourlyCheckTime"



           //function to return shared preferences object (that contains config settings)
           //Note: Context is the environment that enable me to access android resources like shared preferences(storage system in android)
           private fun preferences(context: Context): SharedPreferences {
       return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

   }

   fun getSchedule(context: Context): ScheduleModel {
   val sharedPrefs = preferences(context)
   return ScheduleModel(
       nightStartTime = sharedPrefs.getInt(NIGHT_START_KEY, 1 * 60),
       nightEndTime = sharedPrefs.getInt(NIGHT_END_KEY, 19*60+55),
       isHourlyEnabled = sharedPrefs.getBoolean(IS_HOURLY_ENABLED_KEY, true),
       hourlyCheckDuration = sharedPrefs.getInt(HOURLY_CHECK_DURATION_KEY, 3)
   )
}


fun updateSchedule(context: Context, configuration: ScheduleModel) {
    preferences(context).edit {
        putInt(NIGHT_START_KEY, configuration.nightStartTime)
            .putInt(NIGHT_END_KEY, configuration.nightEndTime)
            .putBoolean(IS_HOURLY_ENABLED_KEY, configuration.isHourlyEnabled)
            .putInt(HOURLY_CHECK_DURATION_KEY, configuration.hourlyCheckDuration)
    }
}

    fun getLastHourlyCheck(context: Context): String? {
        return preferences(context).getString(LAST_HOURLY_CHECK_TIME, null)
    }

    fun setLastHourlyCheck(context: Context, timeSlot: String) {
        preferences(context).edit().putString(LAST_HOURLY_CHECK_TIME, timeSlot)
            .apply()
    }

}