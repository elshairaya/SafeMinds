package com.safeminds.watch.sessionTransfer

sealed class TransferResults {

    //this class explain what happens after trying to send the data (success or fail)
    //sealed is used as each result has different data + it handles all possible cases
    object Success: TransferResults()
    data class UncoverableError( //permanent failure "don't retry it will never success"
        val errorReason: String)
        : TransferResults()
    data class CoverableError(
        val errorReason: String)
        : TransferResults()
}