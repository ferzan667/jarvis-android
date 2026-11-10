package com.razen.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.openwakeword.OpenWakeWord
import java.util.Locale

/** User-started microphone foreground service; it never bypasses the Android lock screen. */
class WakeWordService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_STOP = "com.razen.jarvis.STOP_LISTENING"
        const val ACTION_TEST = "com.razen.jarvis.TEST_WAKE"
        const val ACTION_WAKE = "com.razen.jarvis.WAKE_DETECTED"
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
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var busy = false
    private var lastWake = 0L
    private var detections = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        // User starts foreground service from visible Activity to comply with Android 14+.
        startForeground(LISTEN_ID, listeningNotification())
        running = true
        saveStatus("Mikrofon servisi başlatıldı. Model hazırlanıyor.")
        tts = TextToSpeech(this, this)
        try {
            detector = OpenWakeWord.Builder(applicationContext)
                .setModelAsset("jarvis_v1.onnx")
                .setThreshold(0.50f)
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
            ACTION_STOP -> stopSelf()
            ACTION_TEST -> wakeDetected(1.0f, manualTest = true)
        }
        // Never automatically restart background microphone capture after process termination.
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Text-to-speech initialization failed")
            saveStatus("TTS başlatılamadı. Sesli yanıt için Türkçe dil paketi kontrol edilmeli.")
            return
        }
        val engine = tts ?: return
        val lang = engine.setLanguage(Locale.forLanguageTag("tr-TR"))
        ttsReady = lang != TextToSpeech.LANG_NOT_SUPPORTED &&
            lang != TextToSpeech.LANG_MISSING_DATA
        if (!ttsReady) saveStatus("Türkçe TTS desteklenmiyor veya dil verisi eksik (kod $lang). Telefonun TTS ayarlarını kontrol edin.")
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handler.postDelayed({ resumeWakeListener() }, 650L)
            }
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                saveStatus("TTS konuşma hatası; konuşma motorunu ve medya sesini kontrol edin.")
                handler.postDelayed({ resumeWakeListener() }, 650L)
            }
        })
    }

    private fun wakeDetected(confidence: Float, manualTest: Boolean = false) {
        if (!running || busy || SystemClock.elapsedRealtime() - lastWake < 5000L) return
        busy = true
        lastWake = SystemClock.elapsedRealtime()
        try { detector?.stop() } catch (e: Exception) { Log.w(TAG, "Pause failed", e) }
        Log.i(TAG, "Jarvis detected: $confidence")
        detections++
        saveStatus("JARVIS algılandı! Skor: ${"%.2f".format(Locale.US, confidence)}; bu oturumda: $detections")
        notifyResponse("Buyurun efendim", "ChatGPT'yi açmak için bildirime dokunun")
        // Activity launch from background/lock screen is not allowed on standard Android.
        // When this app is visible, route to the user's default assistant and release our mic.
        if (MainActivity.visible && !manualTest) {
            handler.postDelayed({
                if (running) {
                    sendBroadcast(Intent(ACTION_WAKE).setPackage(packageName))
                    stopSelf()
                }
            }, 1300L)
        }
        if (ttsReady) {
            val spoken = tts?.speak(
                "Buyurun efendim.", TextToSpeech.QUEUE_FLUSH, null, "jarvis_ack"
            ) ?: TextToSpeech.ERROR
            if (spoken != TextToSpeech.ERROR) {
                handler.postDelayed({ resumeWakeListener() }, 7000L)
                return
            }
            saveStatus("TTS speak() hata döndürdü. TTS motorunu ve Türkçe ses paketini kontrol edin.")
        } else {
            saveStatus("TTS hazır değil. Türkçe ses paketi veya TTS motoru kontrol edilmeli.")
        }
        handler.postDelayed({ resumeWakeListener() }, 1500L)
    }

    private fun resumeWakeListener() {
        if (!running || !busy) return
        busy = false
        try {
            detector?.start { confidence -> wakeDetected(confidence) }
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
        val launch = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
            ?: Intent(this, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 8, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun listeningNotification(): Notification {
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
            .setContentTitle("JARVIS dinliyor")
            .setContentText("Jarvis diyerek seslenebilirsiniz")
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
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        detector = null
        tts = null
        super.onDestroy()
    }
}

