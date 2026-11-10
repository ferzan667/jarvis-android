package com.razen.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType
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
    private var pendingStartLive = false
    private lateinit var keyInput: EditText
    private var testTts: TextToSpeech? = null
    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1200)
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
        root.addView(label("0.5.0 • JARVIS + Gemini Live", 17, Color.LTGRAY))
        status = label("Durum okunuyor", 19, Color.WHITE)
        root.addView(status)

        keyInput = EditText(this).apply {
            hint = if (GeminiKeyStore.load(this@MainActivity) == null)
                "Gemini API anahtarını gir" else "Gemini anahtarı kayıtlı"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(keyInput)
        root.addView(button("Anahtarı telefonda kaydet") {
            val value = keyInput.text.toString().trim()
            if (value.isBlank()) status.text = "Önce Gemini API anahtarını girin"
            else try {
                GeminiKeyStore.save(this, value)
                keyInput.text.clear()
                keyInput.hint = "Gemini anahtarı kayıtlı"
                status.text = "Anahtar bu telefonda şifreli kaydedildi"
            } catch (e: Exception) { status.text = "Anahtar kaydedilemedi: ${e.javaClass.simpleName}" }
        })

        root.addView(button("1 — Dinlemeyi başlat") { startListening() })
        root.addView(button("2 — Gemini görüşmesini dene") { startGeminiLive() })
        root.addView(button("Gemini görüşmesini bitir") {
            if (WakeWordService.running) startService(Intent(this, WakeWordService::class.java)
                .setAction(WakeWordService.ACTION_STOP_LIVE))
        })
        root.addView(button("TTS tanı testi") { testSpeech() })
        root.addView(button("Dinlemeyi durdur") {
            stopService(Intent(this, WakeWordService::class.java))
            status.text = "Durduruldu"
        })

        detail = label("", 14, Color.rgb(171, 210, 225))
        root.addView(detail)
        root.addView(label(
            "Gemini anahtarını kaydet. Önce 2 ile sesli görüşmeyi ayrı test et. " +
            "Sonra 1 ile yerel Jarvis algılamasını başlat; algılarsa Gemini görüşmesi açılır. " +
            "Kilit ekranında çalışma Realme 11 üzerinde doğrulanmalıdır.",
            14, Color.LTGRAY
        ))
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        handler.post(refresh)
    }

    override fun onStop() {
        visible = false
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    override fun onDestroy() {
        testTts?.shutdown()
        testTts = null
        super.onDestroy()
    }

    private fun updateStatus() {
        status.text = when {
            WakeWordService.liveActive -> "Gemini görüşmesi açık"
            WakeWordService.running -> "Jarvis dinleme servisi açık"
            else -> "Servis kapalı"
        }
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
        pendingStartLive = false
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
            startWakeService(pendingStartLive)
        } else status.text = "Mikrofon izni gerekli"
    }

    private fun startGeminiLive() {
        if (GeminiKeyStore.load(this).isNullOrBlank()) {
            status.text = "Önce Gemini anahtarını kaydedin"
            return
        }
        pendingStartLive = true
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequest)
        else startWakeService(true)
    }

    private fun startWakeService(startLive: Boolean = false) {
        try {
            val intent = Intent(this, WakeWordService::class.java)
            if (startLive) intent.action = WakeWordService.ACTION_START_LIVE
            startForegroundService(intent)
            status.text = "Başlatılıyor..."
        } catch (e: Exception) {
            status.text = "Başlatma hatası: ${e.javaClass.simpleName}"
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




