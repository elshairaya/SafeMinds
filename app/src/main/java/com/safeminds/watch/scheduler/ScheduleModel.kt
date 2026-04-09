package com.safeminds.watch.scheduler

data class ScheduleModel (
    val nightStartTime : Int =22*60, //10PM night session starts stored in min
    val nightEndTime : Int=4*60+30, //6:30AM night session ends stored in min
    val isHourlyEnabled: Boolean = true, //flag that checks if hourly check is enabled
    val hourlyCheckDuration : Int=3 // the hourly check will be each hour for 3 min.
)












