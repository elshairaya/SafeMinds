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
    }

    enum class SessionState {
        IDLE,
        RUNNING,
        STOPPING
    }

    private lateinit var storage: SafeMindsStorage
    private lateinit var accelerometerCollector: AccelerometerCollector
    private lateinit var heartRateCollector: HeartRateCollector
    private val mainHandler = Handler(Looper.getMainLooper())

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
        storage = SafeMindsStorage(this)
        accelerometerCollector = AccelerometerCollector(this)
        heartRateCollector = HeartRateCollector(this)
        resetProcessingPipeline()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val sessionType = parseSessionType(intent)
                startSession(sessionType)
            }
            ACTION_STOP_SESSION -> stopSession()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun parseSessionType(intent: Intent): MonitoringSessionType {
        return try {
            MonitoringSessionType.valueOf(
                intent.getStringExtra(EXTRA_SESSION_TYPE)
                    ?: MonitoringSessionType.NIGHT_SESSION.name
            )
        } catch (_: Exception) {
            MonitoringSessionType.NIGHT_SESSION
        }
    }

    private fun resetProcessingPipeline() {
        movementProcessor = MovementProcessor()
        epochBuilder = EpochBuilder()
        summaryBuilder = SessionSummaryBuilder()
        epochLogs.clear()

        epochBuilder.onEpochReady = { epoch ->
            summaryBuilder.addEpoch(epoch)
            epochLogs.add(
                JSONObject().apply {
                    put("epochStart", epoch.epochStart)
                    put("movementScore", epoch.movementScore)
                    put("hrMean", epoch.hrMean)
                }
            )
        }

        accelerometerCollector.onSampleCollected = { sample ->
            val magnitude = sqrt(
                (sample.x * sample.x + sample.y * sample.y + sample.z * sample.z).toDouble()
            ).toFloat()
            val movement = movementProcessor.processMagnitude(magnitude)
            epochBuilder.addMovement(sample.timestamp, movement)
        }

        heartRateCollector.onHeartRate = { heartRateSample ->
            epochBuilder.addHeartRate(heartRateSample.beatsPerMinute)
        }
    }

    private fun startSession(sessionType: MonitoringSessionType) {
        if (sessionState == SessionState.RUNNING) {
            if (currentSessionType == sessionType) {
                Log.d(TAG, "Session already running: $sessionType")
                return
            }

            Log.d(TAG, "Switching session from $currentSessionType to $sessionType")
            stopSession(saveData = true, stopForegroundService = false)
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground", e)
            stopSelf()
            return
        }

        resetProcessingPipeline()
        sessionState = SessionState.RUNNING
        currentSessionType = sessionType
        sessionStartTime = System.currentTimeMillis()
        SessionStatePref.setStarted(this, sessionType)

        accelerometerCollector.start()
        heartRateCollector.start()
        scheduleAutoStop(sessionType, ScheduleStorage.getSchedule(this))
        Log.d(TAG, "Session started: type=$sessionType, hrSensor=${heartRateCollector.isSensorAvailable()}")
    }

    private fun stopSession() {
        stopSession(saveData = true, stopForegroundService = true)
    }

    private fun stopSession(saveData: Boolean, stopForegroundService: Boolean) {
        if (sessionState != SessionState.RUNNING) {
            if (stopForegroundService) {
                stopSelf()
            }
            return
        }

        sessionState = SessionState.STOPPING
        mainHandler.removeCallbacks(autoStopRunnable)

        accelerometerCollector.stop()
        heartRateCollector.stop()
        epochBuilder.flushPending()

        if (saveData) {
            processAndSaveSession()
        }

        SessionStatePref.clear(this)
        currentSessionType = null
        sessionState = SessionState.IDLE

        if (stopForegroundService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun scheduleAutoStop(
        sessionType: MonitoringSessionType,
        schedule: ScheduleModel
    ) {
        mainHandler.removeCallbacks(autoStopRunnable)

        val stopDelayMillis = when (sessionType) {
            MonitoringSessionType.HOURLY_CHECK_SESSION -> {
                schedule.hourlyCheckDuration.coerceAtLeast(1) * 60_000L
            }
            MonitoringSessionType.NIGHT_SESSION -> {
                computeNightStopDelayMillis(schedule.nightEndTime)
            }
        }

        mainHandler.postDelayed(autoStopRunnable, stopDelayMillis)
    }

    private fun computeNightStopDelayMillis(nightEndMinutes: Int): Long {
        val now = Calendar.getInstance()
        val end = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, nightEndMinutes / 60)
            set(Calendar.MINUTE, nightEndMinutes % 60)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (end.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }

    private fun processAndSaveSession() {
        val sessionType = currentSessionType ?: return
        val sessionEndTime = System.currentTimeMillis()
        val summary = summaryBuilder.build()

        val sessionJson = JSONObject().apply {
            put("sessionType", sessionType.name)
            put("sessionStartTime", sessionStartTime)
            put("sessionEndTime", sessionEndTime)
            put("missingHeartRateSensor", !heartRateCollector.isSensorAvailable())
            put(
                "summary",
                JSONObject().apply {
                    put("hrMean", summary.hrMean)
                    put("hrMin", summary.hrMin)
                    put("hrMax", summary.hrMax)
                    put("movementMean", summary.movementMean)
                    put("movementVariance", summary.movementVariance)
                    put("totalEpochs", summary.totalEpochs)
                }
            )
            put("epochs", JSONArray(epochLogs))
        }

        when (sessionType) {
            MonitoringSessionType.NIGHT_SESSION -> storage.writeNightSession(sessionStartTime, sessionJson)
            MonitoringSessionType.HOURLY_CHECK_SESSION -> storage.writeHourlyCheck(sessionStartTime, sessionJson)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeMinds")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
}
