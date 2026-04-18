package com.safeminds.watch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.safeminds.watch.R
import com.safeminds.watch.data.SessionReader
import com.safeminds.watch.logging.AppLogger
import com.safeminds.watch.processing.EpochBuilder
import com.safeminds.watch.processing.MovementProcessor
import com.safeminds.watch.processing.SessionSummaryBuilder
import com.safeminds.watch.scheduler.MonitoringSessionType
import com.safeminds.watch.scheduler.SessionStatePref
import com.safeminds.watch.sensors.AccelerometerCollector
import com.safeminds.watch.sensors.HeartRateCollector
import com.safeminds.watch.sessionTransfer.Sender
import com.safeminds.watch.sessionTransfer.SharedPrefsRepository
import com.safeminds.watch.sessionTransfer.TransferController
import com.safeminds.watch.sessionTransfer.TransferSessionRepository
import com.safeminds.watch.sessionTransfer.WearMessageSender
import com.safeminds.watch.storage.SafeMindsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.sqrt
import com.google.gson.Gson
import com.safeminds.watch.data.DataDeduplicator
import com.safeminds.watch.data.SessionMapper
import com.safeminds.watch.sessionTransfer.TransferResults

class MonitoringService : Service() {

    companion object {
        private val serviceJob = SupervisorJob()
        private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

        const val ACTION_START_SESSION = "com.safeminds.watch.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.safeminds.watch.action.STOP_SESSION"
        const val EXTRA_SESSION_TYPE = "extra_session_type"

        private const val CHANNEL_ID = "safeminds_monitoring_channel"
        private const val CHANNEL_NAME = "SafeMinds Monitoring"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "SafeMindsService"

        @Volatile
        var isServiceRunning: Boolean = false
    }

    enum class SessionState {
        IDLE,
        RUNNING,
        STOPPING
    }

    private lateinit var storage: SafeMindsStorage
    private lateinit var accelerometerCollector: AccelerometerCollector
    private lateinit var heartRateCollector: HeartRateCollector
    private lateinit var mainHandler: Handler

    private lateinit var movementProcessor: MovementProcessor
    private lateinit var epochBuilder: EpochBuilder
    private lateinit var summaryBuilder: SessionSummaryBuilder
    private val epochLogs = mutableListOf<JSONObject>()

    private var sessionState = SessionState.IDLE
    private var sessionStartTime: Long = 0L
    private var currentSessionType: MonitoringSessionType? = null
    private var currentSessionId: String? = null

    private lateinit var transferController: TransferController
    private lateinit var sender: Sender
    private lateinit var transferSessionRepository: TransferSessionRepository

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        isServiceRunning = true

        storage = SafeMindsStorage(this)
        accelerometerCollector = AccelerometerCollector(this)
        heartRateCollector = HeartRateCollector(this)
        movementProcessor = MovementProcessor()
        epochBuilder = EpochBuilder()
        summaryBuilder = SessionSummaryBuilder()
        mainHandler = Handler(Looper.getMainLooper())
        transferSessionRepository = SharedPrefsRepository(this)
        sender = WearMessageSender(this, storage)
        transferController = TransferController(transferSessionRepository, sender)

        setupProcessingPipeline()
        createNotificationChannel()
        AppLogger.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val sessionType = try {
                    MonitoringSessionType.valueOf(
                        intent.getStringExtra(EXTRA_SESSION_TYPE)
                            ?: MonitoringSessionType.NIGHT_SESSION.name
                    )
                } catch (_: Exception) {
                    MonitoringSessionType.NIGHT_SESSION
                }

                startSession(sessionType)
            }

            ACTION_STOP_SESSION -> stopSession()
            else -> AppLogger.w(TAG, "Unknown action received")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupProcessingPipeline() {
        epochBuilder.onEpochReady = { epoch ->
            try {
                summaryBuilder.addEpoch(epoch)
                val epochJson = JSONObject().apply {
                    put("epochStart", epoch.epochStart)
                    put("movementScore", epoch.movementScore)
                    put("hrMean", epoch.hrMean)
                }
                epochLogs.add(epochJson)
                AppLogger.d(TAG, "Epoch created movement=${epoch.movementScore}, hr=${epoch.hrMean}")
            } catch (exception: Exception) {
                AppLogger.e(TAG, "Failed while handling epoch", exception)
            }
        }

        accelerometerCollector.onSampleCollected = { sample ->
            try {
                val magnitude = sqrt(
                    (sample.x * sample.x + sample.y * sample.y + sample.z * sample.z).toDouble()
                ).toFloat()
                val movement = movementProcessor.processMagnitude(magnitude)
                epochBuilder.addMovement(sample.timestamp, movement)
            } catch (exception: Exception) {
                AppLogger.e(TAG, "Accelerometer processing failed", exception)
            }
        }

        heartRateCollector.onHeartRate = { heartRateSample ->
            try {
                epochBuilder.addHeartRate(heartRateSample.beatsPerMinute)
            } catch (exception: Exception) {
                AppLogger.e(TAG, "Heart rate processing failed", exception)
            }
        }
    }

    private fun startSession(sessionType: MonitoringSessionType) {
        AppLogger.i(TAG, "startSession called with type=$sessionType")
        if (sessionState == SessionState.RUNNING) {
            if (currentSessionType == sessionType) {
                AppLogger.d(TAG, "Session already running: $sessionType")
                return
            }
            AppLogger.w(TAG, "Switching session from $currentSessionType to $sessionType")
        }

        val notification = buildNotification("SafeMinds monitoring running: $sessionType")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            AppLogger.i(TAG, "Foreground service started")
        } catch (exception: Exception) {
            AppLogger.e(TAG, "Failed to enter foreground", exception)
            stopSelf()
            return
        }

        epochLogs.clear()
        summaryBuilder = SessionSummaryBuilder()
        sessionState = SessionState.RUNNING
        currentSessionType = sessionType
        currentSessionId = UUID.randomUUID().toString()
        sessionStartTime = System.currentTimeMillis()

        SessionStatePref.setStarted(this, sessionType, currentSessionId!!)
        AppLogger.i(TAG, "Session state stored. sessionId=$currentSessionId")

        accelerometerCollector.start()
        heartRateCollector.start()
        AppLogger.i(TAG, "Sensors started")
    }

    private fun stopSession() {
        AppLogger.i(TAG, "stopSession called")

        if (sessionState != SessionState.RUNNING) {
            AppLogger.w(TAG, "Service not running. Clearing stale state and stopping")
            SessionStatePref.clear(this)
            isServiceRunning = false
            stopSelf()
            return
        }

        val sessionId = currentSessionId ?: SessionStatePref.getID(this)
        isServiceRunning = false
        sessionState = SessionState.STOPPING
        AppLogger.i(TAG, "Session state -> STOPPING for sessionId=$sessionId")

        accelerometerCollector.stop()
        heartRateCollector.stop()
        AppLogger.i(TAG, "Sensors stopped")

        if (sessionId != null) {
            processAndSaveSession(sessionId)
            scope.launch {
                val result = sender.sendSession(sessionId)
                when (result) {
                    is TransferResults.Success -> {
                        AppLogger.d(TAG, "Session sent successfully: $sessionId")
                    }

                    is TransferResults.CoverableError -> {
                        AppLogger.e(TAG, "Recoverable error: ${result.errorReason}")
                    }

                    is TransferResults.UncoverableError -> {
                        AppLogger.e(TAG, "Fatal error: ${result.errorReason}")
                    }
                }
            }
        }
         else {
            AppLogger.w(TAG, "No session ID available; skipping save and transfer")
        }

        SessionStatePref.clear(this)
        currentSessionId = null
        currentSessionType = null
        sessionState = SessionState.IDLE
        AppLogger.i(TAG, "Session state -> IDLE")

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "Foreground service stopped")
    }

    private fun processAndSaveSession(sessionId: String) {
        try {
            val sessionEndTime = System.currentTimeMillis()
            val summary = summaryBuilder.build()

            val epochsArray = JSONArray()
            epochLogs.forEach { epoch -> epochsArray.put(epoch) }

            val summaryJson = JSONObject().apply {
                put("sessionId", sessionId)
                put("sessionStart", sessionStartTime)
                put("sessionEnd", sessionEndTime)
                put("sessionType", currentSessionType?.name ?: "UNKNOWN")
                put("summary", summary.toString())
                put("epochs", epochsArray)
                put("epochCount", epochLogs.size)
                put("note", "Integrated service with sensors, processing, epoch builder, and transfer-ready storage")
            }

            when (currentSessionType) {
                MonitoringSessionType.NIGHT_SESSION -> storage.writeNightSession(sessionId, summaryJson)
                MonitoringSessionType.HOURLY_CHECK_SESSION -> storage.writeHourlyCheck(sessionId, summaryJson)
                null -> storage.writeNightSession(sessionId, summaryJson)
            }

            AppLogger.i(TAG, "Session saved successfully for $sessionId")
            AppLogger.d(TAG, "Final summary = $summary")
        } catch (exception: Exception) {
            AppLogger.e(TAG, "Failed to save session $sessionId", exception)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SafeMinds")
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service channel for SafeMinds monitoring"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            AppLogger.i(TAG, "Notification channel created")
        }
    }
}
