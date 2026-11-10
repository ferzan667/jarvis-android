package com.razen.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.openwakeword.OpenWakeWord
import java.util.Locale

/** User-started microphone foreground service; it never bypasses the Android lock screen. */
class WakeWordService : Service() {
    companion object {
        const val ACTION_STOP = "com.razen.jarvis.STOP_LISTENING"
        const val ACTION_START_LIVE = "com.razen.jarvis.START_GEMINI_LIVE"
        const val ACTION_STOP_LIVE = "com.razen.jarvis.STOP_GEMINI_LIVE"
        private const val LISTEN_CHANNEL = "jarvis_microphone_v3"
        private const val ALERT_CHANNEL = "jarvis_response_v3"
        private const val LISTEN_ID = 71
        private const val ALERT_ID = 72
        private const val TAG = "Jarvis"
        @Volatile var running = false
            private set
        @Volatile var liveActive = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var detector: OpenWakeWord? = null
    private var live: GeminiLiveClient? = null
    private var busy = false
    private var lastWake = 0L
    private var detections = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        // User starts foreground service from visible Activity to comply with Android 14+.
        try {
            startForeground(LISTEN_ID, listeningNotification())
        } catch (e: Exception) {
            saveStatus("Mikrofon servisi açılamadı: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "Foreground microphone failed", e)
            stopSelf()
            return
        }
        running = true
        saveStatus("Mikrofon servisi başlatıldı.")
    }

    private fun startDetector() {
        try {
            if (detector == null) detector = OpenWakeWord.Builder(applicationContext)
                .setModelAsset("jarvis_v1.onnx").setThreshold(0.25f)
                .setDebounceMs(5000L)
                .setTelemetryListener(object : OpenWakeWord.OnTelemetryListener {
                    override fun onState(state: String) {
                        getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE).edit()
                            .putString("engine_state", state).apply()
                        if (state == "MIC_ERROR" || state == "STOPPED_UNEXPECTEDLY" ||
                            state.startsWith("ERROR:") || state.startsWith("INFERENCE_ERROR:")) {
                            saveStatus("Algılama hatası: $state")
                            stopSelf()
                        }
                    }

                    override fun onFrame(level: Int, score: Float) {
                        getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE).edit()
                            .putInt("mic_level", level)
                            .putFloat("model_score", if (score.isFinite()) score else -1f).apply()
                    }
                }).build()
            detector?.start { confidence -> wakeDetected(confidence) }
            saveStatus("Algılayıcı iş parçacığı başlatıldı; model ve mikrofon henüz doğrulanmadı. Mikrofon göstergesini ve Logcat'i kontrol edin.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word engine", e)
            saveStatus("Ses algılama hatası: ${e.javaClass.simpleName}: ${e.message}")
            notifyResponse("JARVIS başlatılamadı", "Uygulamayı açıp tekrar deneyin")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_START_LIVE -> startGemini()
            ACTION_STOP_LIVE -> live?.stop()
            else -> if (!busy) startDetector()
        }
        // The user explicitly started continuous listening. Android may recreate this service.
        return START_STICKY
    }

    private fun wakeDetected(confidence: Float) {
        if (!running || busy || SystemClock.elapsedRealtime() - lastWake < 5000L) return
        lastWake = SystemClock.elapsedRealtime()
        pauseForLive()
        Log.i(TAG, "Jarvis detected: $confidence")
        detections++
        saveStatus("JARVIS algılandı! Skor: ${"%.2f".format(Locale.US, confidence)}; bu oturumda: $detections")
        startGemini()
    }

    private fun pauseForLive() {
        if (busy) return
        busy = true
        try { detector?.stop() } catch (e: Exception) { Log.w(TAG, "Pause failed", e) }
        getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE).edit()
            .putString("engine_state", "PAUSED_FOR_GEMINI")
            .putInt("mic_level", 0).putFloat("model_score", -1f).apply()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(LISTEN_ID, listeningNotification(paused = true))
    }

    private fun startGemini() {
        if (live != null) return
        pauseForLive()
        val key = GeminiKeyStore.load(this)
        if (key.isNullOrBlank()) {
            saveStatus("Gemini anahtarı yok. Uygulama ekranında anahtarı kaydedin.")
            handler.postDelayed({ resumeWakeListener() }, 800L)
            return
        }
        live = GeminiLiveClient(this, key,
            onStatus = { message -> saveStatus(message) },
            onConnected = {
                liveActive = true
                getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE).edit()
                    .putString("live_state", "CONNECTED").apply()
            },
            onStopped = {
                handler.post {
                    live = null
                    liveActive = false
                    getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE).edit()
                        .putString("live_state", "IDLE").apply()
                    if (running) handler.postDelayed({ resumeWakeListener() }, 500L)
                }
            })
        getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE).edit()
            .putString("live_state", "CONNECTING").apply()
        live?.start()
    }

    private fun resumeWakeListener() {
        if (!running || !busy || live != null) return
        val anotherRecording = try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.activeRecordingConfigurations.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect active recordings", e)
            false
        }
        if (anotherRecording) {
            handler.postDelayed({ resumeWakeListener() }, 5_000L)
            return
        }
        busy = false
        try {
            startDetector()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(LISTEN_ID, listeningNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume listening", e)
            saveStatus("Yeniden dinleme hatası: ${e.javaClass.simpleName}: ${e.message}")
            notifyResponse("JARVIS durdu", "Tekrar başlatmak için uygulamayı açın")
            stopSelf()
        }
    }

    private fun saveStatus(message: String) {
        getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE)
            .edit().putString("status", message).putLong("updated_at", System.currentTimeMillis()).apply()
    }

    private fun createChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            LISTEN_CHANNEL, "JARVIS mikrofon hizmeti", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Mikrofon dinlemesi etkin olduğunda görünür" })
        manager.createNotificationChannel(NotificationChannel(
            ALERT_CHANNEL, "JARVIS yanıtları", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Jarvis algılandığında bildirim gösterir" })
    }

    private fun listeningNotification(paused: Boolean = false): Notification {
        val stop = PendingIntent.getService(
            this, 9, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, LISTEN_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (paused) "JARVIS Gemini görüşmesinde" else "JARVIS dinliyor")
            .setContentText(if (paused) "Görüşme bitince dinleme devam eder" else "Jarvis diyerek seslenebilirsiniz")
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_pause, "Dinlemeyi durdur", stop)
            .build()
    }

    private fun notifyResponse(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_ID, Notification.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(PendingIntent.getActivity(this, 8,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build())
    }

    override fun onDestroy() {
        running = false
        liveActive = false
        handler.removeCallbacksAndMessages(null)
        live?.stop()
        live = null
        try { detector?.release() } catch (_: Exception) {}
        detector = null
        super.onDestroy()
    }
}





