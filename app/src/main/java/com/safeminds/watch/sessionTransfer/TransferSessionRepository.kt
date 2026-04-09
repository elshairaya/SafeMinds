package com.safeminds.watch.sessionTransfer

//it is storage interface that define how the app should read and retrieve transferred data

interface TransferSessionRepository {
    fun getTransferredRecord(sessionID: String): TransferEntry? //to get the transfer record of one session

    fun updateRecord(updatedRecord: TransferEntry) //to update transfer changes

    fun listAllRecords(): List<TransferEntry> // return list of all saved transfer records

    fun listWaitingRecords(): List<TransferEntry> //returns list of records that need to be sent again "pending state"




}