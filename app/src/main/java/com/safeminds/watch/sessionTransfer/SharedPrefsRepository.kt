package com.safeminds.watch.sessionTransfer

import android.content.Context
import android.content.SharedPreferences

class SharedPrefsRepository(context: Context)  : TransferSessionRepository{
    private val TRANSFERS_PREF="TransfersPreferencesFile"

    private val preferences: SharedPreferences= context.getSharedPreferences(TRANSFERS_PREF, Context.MODE_PRIVATE)


    override fun getTransferredRecord(sessionID: String): TransferEntry? {

    val sessionKey=createKey(sessionID)
       val status=preferences.getString("$sessionKey status", null)?: return null // load the status (required)
        //if it is null -> no transfer -> stop
        val retryTimes=preferences.getInt("$sessionKey retryTimes",0) // load how many times we tried the transfer process (default value)
        val recordCreatedTime=preferences.getLong("$sessionKey recordCreatedTime",0L) // load when the transfer record was created (default value)
        val lastAttemptTime=
            if(preferences.contains("$sessionKey lastAttemptTime")){
                preferences.getLong("$sessionKey lastAttemptTime",0L)
            }
        else{
            null
            }

        val error=preferences.getString("$sessionKey error", null)

        return TransferEntry(
            sessionID=sessionID,
            status= DataTransferStatus.valueOf(status),
            retryTimes=retryTimes,
            recordCreatedTime=recordCreatedTime,
            lastAttemptTime=lastAttemptTime,
            error=error
        )
    }
    private val SESSIONS_ID="all known session IDs"
    private fun getAllIDs(): Set<String>{
        return preferences.getStringSet(SESSIONS_ID,emptySet())?:emptySet()
    }

    private fun saveID(sessionID: String){
        val currentID=getAllIDs().toMutableSet()
        currentID.add(sessionID)

        preferences.edit()
            .putStringSet(SESSIONS_ID,currentID)
            .apply()
    }

    override fun updateRecord(updatedRecord: TransferEntry) {
        val sessionKey=createKey(updatedRecord.sessionID)

        preferences.edit().putString("$sessionKey status", updatedRecord.status.name)
            .putInt("$sessionKey retryTimes", updatedRecord.retryTimes)
            .putLong("$sessionKey recordCreatedTime", updatedRecord.recordCreatedTime)
            .apply()

        val change=preferences.edit()
        if(updatedRecord.lastAttemptTime!=null){
            change.putLong("$sessionKey lastAttemptTime", updatedRecord.lastAttemptTime)
        }
        else{
            change.remove("$sessionKey lastAttemptTime")
        }

        if (updatedRecord.error!=null){
            change.putString("$sessionKey error", updatedRecord.error)
        }
        else{
            change.remove("$sessionKey error")
        }
        change.apply()

        saveID(updatedRecord.sessionID)

    }

    override fun listAllRecords(): List<TransferEntry> {
        return getAllIDs().mapNotNull {
                sessionID -> getTransferredRecord(sessionID)
        }
    }

    override fun listWaitingRecords(): List<TransferEntry> {
        return listAllRecords().filter {
            updatedRecord ->updatedRecord.status== DataTransferStatus.WAITING
        }
    }

    private fun createKey(sessionID: String): String{
        return "transferRecord_${sessionID}_"
    }




}