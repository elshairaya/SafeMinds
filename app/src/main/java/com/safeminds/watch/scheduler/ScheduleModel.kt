package com.safeminds.watch.scheduler

data class ScheduleModel (
    val nightStartTime : Int =1*60, //10PM night session starts stored in min
    val nightEndTime : Int= 19*60+55, //6:30AM night session ends stored in min
    val isHourlyEnabled: Boolean = true, //flag that checks if hourly check is enabled
    val hourlyCheckDuration : Int=3 // the hourly check will be each hour for 3 min.
)












