package com.safeminds.watch.sessionTransfer

class TestRecoverableErrorSender : Sender {
    override suspend fun sendSession(sessionID: String): TransferResults {
        return TransferResults.CoverableError("Temporary failure")
    }
}
//we have the case after 1 fail it should resend this function will be added retryAllWaiting() after AYA's code(communication)