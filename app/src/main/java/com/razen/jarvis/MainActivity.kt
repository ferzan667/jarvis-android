package com.razen.jarvis

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        @Volatile var visible: Boolean = false
            private set
    }
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val permissionRequest = 210
    private var receiverRegistered = false
    private var testTts: TextToSpeech? = null
    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1200)
        }
    }
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WakeWordService.ACTION_WAKE) {
                // An Activity may launch another app while the user is actively viewing it.
                // On the lock screen, only the notification is shown; it cannot unlock the phone.
                openChatGpt()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 15, 25)
        window.navigationBarColor = Color.rgb(8, 15, 25)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.rgb(8, 15, 25))
        }
        root.addView(label("J A R V I S", 34, Color.rgb(91, 226, 255)))
        root.addView(label("0.3.4 • Tek kelime: JARVIS", 17, Color.LTGRAY))
        status = label("Durum okunuyor", 19, Color.WHITE)
        root.addView(status)

        root.addView(button("1 — Dinlemeyi başlat") { startListening() })
        root.addView(button("2 — Sesli yanıtı test et") { testSpeech() })
        root.addView(button("3 — ChatGPT'yi aç") { openChatGpt() })
        root.addView(button("Dinlemeyi durdur") {
            stopService(Intent(this, WakeWordService::class.java))
            status.text = "Durduruldu"
        })

        detail = label("", 14, Color.rgb(171, 210, 225))
        root.addView(detail)
        root.addView(label(
            "Kullanım: 1'e bas, telefonu açıkken ya da kilitliyken 'Jarvis' de. " +
            "ChatGPT varsayılan asistan ise Android asistan çağrısı denenir. " +
            "Kilitliyken Android çağrıyı engelleyebilir; bildirim yedek geçiştir.",
            14, Color.LTGRAY
        ))
        root.addView(label(
            "ChatGPT → Ayarlar → Ses → Sesle Başlat özelliğini aç. " +
            "ChatGPT yeni/boş sohbete açılırsa sesli görüşme otomatik başlayabilir. " +
            "Kilitliyken otomatik açılış cihazda test edilmelidir; gerekirse bildirime dokunup kilidi açın.",
            13, Color.rgb(155, 175, 190)
        ))
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        handleHandoff(intent)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        val filter = IntentFilter(WakeWordService.ACTION_WAKE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(wakeReceiver, filter)
        }
        receiverRegistered = true
        handler.post(refresh)
    }

    override fun onStop() {
        visible = false
        if (receiverRegistered) {
            unregisterReceiver(wakeReceiver)
            receiverRegistered = false
        }
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    override fun onDestroy() {
        testTts?.shutdown()
        testTts = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHandoff(intent)
    }

    private fun handleHandoff(intent: Intent?) {
        if (intent?.getBooleanExtra(WakeWordService.EXTRA_HANDOFF, false) != true) return
        intent.removeExtra(WakeWordService.EXTRA_HANDOFF)
        stopService(Intent(this, WakeWordService::class.java))
        handler.postDelayed({ openChatGpt() }, 350L)
    }

    private fun updateStatus() {
        status.text = if (WakeWordService.running) "Servis açık • mikrofon durumu için tanılamayı kontrol edin" else "Servis kapalı"
        val pref = getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE)
        detail.text = "Tanılama: " + pref.getString("status", "Henüz kayıt yok") +
            "\nMikrofon kaydı: " + microphoneState()
    }

    private fun microphoneState(): String {
        if (Build.VERSION.SDK_INT < 29) return "Android sürümü nedeniyle ölçülemiyor"
        return try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.activeRecordingConfigurations.any {
                    it.clientAudioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION && !it.isClientSilenced
                }) "ses tanıma kaydı etkin" else "ses tanıma kaydı görünmüyor"
        } catch (e: Exception) { "ölçülemedi: ${e.javaClass.simpleName}" }
    }

    private fun testSpeech() {
        testTts?.shutdown()
        status.text = "TTS hazırlanıyor..."
        testTts = TextToSpeech(this) { result -> runOnUiThread {
            val engine = testTts
            if (result != TextToSpeech.SUCCESS || engine == null) {
                status.text = "TTS başlatılamadı (kod $result). Telefonun metin okuma motorunu kontrol edin."
                return@runOnUiThread
            }
            val language = engine.setLanguage(Locale.forLanguageTag("tr-TR"))
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                status.text = "Türkçe TTS sesi eksik veya desteklenmiyor (kod $language)."
                return@runOnUiThread
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { runOnUiThread { status.text = "TTS konuşuyor..." } }
                override fun onDone(utteranceId: String?) { runOnUiThread { status.text = "TTS tamamlandı. Ses duyulmadıysa medya sesini kontrol edin." } }
                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) { runOnUiThread { status.text = "TTS konuşma hatası." } }
            })
            if (engine.speak("Buyurun efendim.", TextToSpeech.QUEUE_FLUSH, null, "manual_tts") == TextToSpeech.ERROR)
                status.text = "TTS speak() hata döndürdü."
        } }
    }

    private fun startListening() {
        val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) required.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), permissionRequest)
        else startWakeService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permissionRequest) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeService()
        } else status.text = "Mikrofon izni gerekli"
    }

    private fun startWakeService() {
        try {
            startForegroundService(Intent(this, WakeWordService::class.java))
            status.text = "Başlatılıyor..."
        } catch (e: Exception) {
            status.text = "Başlatma hatası: ${e.javaClass.simpleName}"
        }
    }

    private fun openChatGpt() {
        stopService(Intent(this, WakeWordService::class.java))
        if (AssistantHandoff.request(this)) {
            status.text = "Android varsayılan asistan çağrısı gönderildi"
            return
        }
        try {
            val launch = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            } else status.text = "ChatGPT uygulaması yüklü değil"
        } catch (e: Exception) {
            status.text = "ChatGPT açılamadı: ${e.javaClass.simpleName}"
        }
    }

    private fun label(value: String, sp: Int, color: Int) = TextView(this).apply {
        text = value
        textSize = sp.toFloat()
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}


