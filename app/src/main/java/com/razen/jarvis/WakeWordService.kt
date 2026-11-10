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
        const val ACTION_PAUSE = "com.razen.jarvis.PAUSE_FOR_ASSISTANT"
        const val ACTION_WAKE = "com.razen.jarvis.WAKE_DETECTED"
        const val EXTRA_HANDOFF = "com.razen.jarvis.HANDOFF_TO_CHATGPT"
        private const val LISTEN_CHANNEL = "jarvis_microphone_v3"
        private const val ALERT_CHANNEL = "jarvis_response_v3"
        private const val LISTEN_ID = 71
        private const val ALERT_ID = 72
        private const val TAG = "Jarvis"
        @Volatile var running = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var detector: OpenWakeWord? = null
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
        saveStatus("Mikrofon servisi başlatıldı. Model hazırlanıyor.")
        try {
            detector = OpenWakeWord.Builder(applicationContext)
                .setModelAsset("jarvis_v1.onnx")
                .setThreshold(0.35f)
                .setDebounceMs(5000L)
                .build()
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
            ACTION_PAUSE -> pauseForAssistant()
        }
        // The user explicitly started continuous listening. Android may recreate this service.
        return START_STICKY
    }

    private fun wakeDetected(confidence: Float) {
        if (!running || busy || SystemClock.elapsedRealtime() - lastWake < 5000L) return
        lastWake = SystemClock.elapsedRealtime()
        pauseForAssistant()
        Log.i(TAG, "Jarvis detected: $confidence")
        detections++
        saveStatus("JARVIS algılandı! Skor: ${"%.2f".format(Locale.US, confidence)}; bu oturumda: $detections")
        notifyResponse("Jarvis algılandı", "ChatGPT açılmazsa bildirime dokunun")
        if (MainActivity.visible) {
            handler.postDelayed({
                if (running) {
                    sendBroadcast(Intent(ACTION_WAKE).setPackage(packageName))
                }
            }, 250L)
        } else if (AssistantHandoff.chatGptOwnsRole(this)) {
            handler.postDelayed({
                if (running) {
                    val requested = AssistantHandoff.request(this)
                    saveStatus(if (requested) "Android asistan çağrısı istendi; açılışı cihazda doğrulayın."
                        else "Asistan çağrısı reddedildi; bildirimden geçin.")
                }
            }, 250L)
        }
    }

    private fun pauseForAssistant() {
        if (busy) return
        busy = true
        try { detector?.stop() } catch (e: Exception) { Log.w(TAG, "Pause failed", e) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(LISTEN_ID, listeningNotification(paused = true))
        handler.postDelayed({ resumeWakeListener() }, 20_000L)
    }

    private fun resumeWakeListener() {
        if (!running || !busy) return
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
            detector?.start { confidence -> wakeDetected(confidence) }
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

    private fun chatGptIntent(): PendingIntent {
        val launch = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_HANDOFF, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 8, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
            .setContentTitle(if (paused) "JARVIS asistanı bekliyor" else "JARVIS dinliyor")
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
            .setContentIntent(chatGptIntent())
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build())
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try { detector?.release() } catch (_: Exception) {}
        detector = null
        super.onDestroy()
    }
}



