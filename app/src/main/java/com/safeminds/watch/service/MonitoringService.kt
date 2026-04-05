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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.safeminds.watch.R
import com.safeminds.watch.processing.EpochBuilder
import com.safeminds.watch.processing.MovementProcessor
import com.safeminds.watch.processing.SessionSummaryBuilder
import com.safeminds.watch.scheduler.MonitoringSessionType
import com.safeminds.watch.scheduler.ScheduleModel
import com.safeminds.watch.scheduler.SessionStatePref
import com.safeminds.watch.sensors.AccelerometerCollector
import com.safeminds.watch.sensors.HeartRateCollector
import com.safeminds.watch.service.MonitoringService.Companion.TAG
import com.safeminds.watch.storage.SafeMindsStorage
import com.safeminds.watch.storage.ScheduleStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.sqrt

class MonitoringService : Service() {

    companion object {
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

    private val autoStopRunnable = Runnable {
        Log.d(TAG, "Auto-stop reached for session=$currentSessionType")
        stopSession()
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        storage = SafeMindsStorage(this)
        accelerometerCollector = AccelerometerCollector(this)
        heartRateCollector = HeartRateCollector(this)
        movementProcessor = MovementProcessor()
        epochBuilder = EpochBuilder()
        summaryBuilder = SessionSummaryBuilder()
        mainHandler = Handler(Looper.getMainLooper())


        setupProcessingPipeline()
        createNotificationChannel()
        Log.d(TAG, "Service CREATED")

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action = ${intent?.action}")
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val sessionType = try {
                    MonitoringSessionType.valueOf(
                        intent.getStringExtra(EXTRA_SESSION_TYPE)
                            ?: MonitoringSessionType.NIGHT_SESSION.name
                    )
                } catch (e: Exception) {
                    MonitoringSessionType.NIGHT_SESSION
                }

                startSession(sessionType)
            }
            ACTION_STOP_SESSION -> stopSession()
            else -> {
                Log.d(TAG, "Unknown action received")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun setupProcessingPipeline() {
        epochBuilder.onEpochReady = { epoch ->
            try{
            summaryBuilder.addEpoch(epoch)
                val epochJson = JSONObject().apply {
                    put("movementScore", epoch.movementScore)
                    put("hrMean", epoch.hrMean)
                }
                epochLogs.add(epochJson)

                Log.d(
                    TAG,
                    "Epoch created: movement=${epoch.movementScore}, hr=${epoch.hrMean}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed while handling epoch", e)
            }
        }

        accelerometerCollector.onSampleCollected = { sample ->
            try{
            val magnitude = sqrt(
                (sample.x * sample.x + sample.y * sample.y + sample.z * sample.z).toDouble()
            ).toFloat()
            val movement = movementProcessor.processMagnitude(magnitude)
            epochBuilder.addMovement(sample.timestamp, movement)
            }catch(e: Exception){
                Log.e(TAG, "Accelerometer processing failed", e)

            }
        }

        heartRateCollector.onHeartRate = { heartRateSample ->
            try {
                epochBuilder.addHeartRate(heartRateSample.beatsPerMinute)
            } catch (e: Exception) {
                Log.e(TAG, "Heart rate processing failed", e)
            }
        }
    }

    private fun startSession(sessionType: MonitoringSessionType) {
        Log.d(TAG, "startSession called with type = $sessionType")
        if (sessionState == SessionState.RUNNING) {
            if (currentSessionType == sessionType) {
                Log.d(TAG, "Session already running: $sessionType")
                return
            }
//new
            Log.d(TAG, "Switching session from $currentSessionType to $sessionType")
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
            Log.d(TAG, "Foreground service started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground", e)
            stopSelf()
            return
        }
        epochLogs.clear()
        summaryBuilder = SessionSummaryBuilder()

        sessionState = SessionState.RUNNING
        currentSessionType = sessionType
        sessionStartTime = System.currentTimeMillis()
        SessionStatePref.setStarted(this, sessionType)
        Log.d(TAG, "Session state stored: $sessionType")

        accelerometerCollector.start()
        heartRateCollector.start()
        Log.d(TAG, "Sensors started")
    }

    private fun stopSession() {
        Log.d(TAG, "stopSession called")

        if (sessionState != SessionState.RUNNING) {
            Log.d(TAG, "Service not running -> clearing stale state")
            SessionStatePref.clear(this)
            isServiceRunning = false
            stopSelf()
            return
        }
        SessionStatePref.clear(this)
        isServiceRunning = false
        sessionState = SessionState.STOPPING
        Log.d(TAG, "Session state → STOPPING")


        accelerometerCollector.stop()
        heartRateCollector.stop()
        Log.d(TAG, "Sensors stopped")

        processAndSaveSession()

        SessionStatePref.clear(this)
        Log.d(TAG, "Session state cleared")

        currentSessionType = null
        sessionState = SessionState.IDLE
        Log.d(TAG, "Session state → IDLE")

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "Foreground service stopped")
    }
    private fun processAndSaveSession() {
        try {
            val sessionEndTime = System.currentTimeMillis()
            val summary = summaryBuilder.build()

            val epochsArray = JSONArray()
            for (epoch in epochLogs) {
                epochsArray.put(epoch)
            }

            val summaryJson = JSONObject().apply {
                put("sessionStart", sessionStartTime)
                put("sessionEnd", sessionEndTime)
                put("sessionType", currentSessionType?.name ?: "UNKNOWN")

                // summary values
                put("summary", summary.toString())

                // epoch records collected during session
                put("epochs", epochsArray)

                put("note", "Integrated service with sensors, processing, epoch builder, and summary flow")
            }

            when (currentSessionType) {
                MonitoringSessionType.NIGHT_SESSION -> {
                    storage.writeNightSession(summaryJson)
                    Log.d(TAG, "Night session saved")
                }

                MonitoringSessionType.HOURLY_CHECK_SESSION -> {
                    storage.writeHourlyCheck(1, summaryJson)
                    Log.d(TAG, "Hourly check saved")
                }

                null -> {
                    storage.writeNightSession(summaryJson)
                    Log.d(TAG, "Fallback session saved")
                }
            }

            Log.d(TAG, "Session data saved successfully")
            Log.d(TAG, "Final summary = $summary")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        Log.d(TAG, "Building notification")

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

            Log.d(TAG, "Notification channel created")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        SessionStatePref.clear(this)
        try {
            accelerometerCollector.stop()
        } catch (_: Exception) {
        }

        try {
            heartRateCollector.stop()
        } catch (_: Exception) {
        }

        Log.d(TAG, "Service DESTROYED")
    }
}