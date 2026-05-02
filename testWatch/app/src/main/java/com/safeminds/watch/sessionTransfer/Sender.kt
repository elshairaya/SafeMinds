package com.safeminds.watch.sessionTransfer

//it introduces what should be done
interface Sender {
    suspend fun sendSession(sessionID: String): TransferResults
}

//it will be used on real implementation (communication)
//I will use it on the controller