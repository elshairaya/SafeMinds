package com.safeminds.watch.sessionTransfer

data class TransferEntry (
    //THIS DATA CLASS TO TRACK A SINGLE SESSION

    val sessionID: String,
    val status: DataTransferStatus,
    val retryTimes: Int=0, //NUM OF RETRIES TO SEND DATA
    val recordCreatedTime: Long= System.currentTimeMillis(), //THE TIME OF TRANSFER RECORD CREATION
    val lastAttemptTime: Long?=null, //THE LAST TIME SENDING ATTEMPTS HAPPENED
    val error: String?=null //SAVES THE ERROR MESSAGE IF FAIL HAPPENS
)