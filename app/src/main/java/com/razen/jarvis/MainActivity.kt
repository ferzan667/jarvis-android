package com.razen.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        @Volatile var visible = false
            private set
    }

    private val background = Color.rgb(6, 13, 25)
    private val panel = Color.rgb(14, 29, 46)
    private val cyan = Color.rgb(70, 225, 241)
    private val muted = Color.rgb(152, 177, 195)
    private val handler = Handler(Looper.getMainLooper())
    private val permissionRequest = 210
    private var pendingStartLive = false
    private lateinit var stateText: TextView
    private lateinit var stageText: TextView
    private lateinit var levelText: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var scoreText: TextView
    private lateinit var logText: TextView
    private lateinit var keyInput: EditText
    private lateinit var hud: JarvisHudView
    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(28))
            setBackgroundColor(this@MainActivity.background)
        }
        root.addView(text("J A R V I S", 34, cyan, true))
        root.addView(text("AUTONOMOUS VOICE INTERFACE  /  0.7.3", 12, muted))
        hud = JarvisHudView(this)
        root.addView(hud, LinearLayout.LayoutParams(-1, dp(260)))

        val liveCard = card()
        liveCard.addView(text("SİSTEM DURUMU", 12, cyan, true))
        stateText = text("Hazırlanıyor", 24, Color.WHITE, true)
        liveCard.addView(stateText)
        stageText = text("Model: beklemede", 14, muted)
        liveCard.addView(stageText)
        liveCard.addView(space(14))
        levelText = text("MİKROFON  0%", 12, muted, true)
        liveCard.addView(levelText)
        levelBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(39, 64, 82))
        }
        liveCard.addView(levelBar, LinearLayout.LayoutParams(-1, dp(9)).apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        })
        scoreText = text("JARVIS SKORU  —", 12, muted, true)
        liveCard.addView(scoreText)
        root.addView(liveCard)

        root.addView(space(22))
        val keyCard = card()
        keyCard.addView(text("GEMINI BAĞLANTISI", 12, cyan, true))
        keyCard.addView(text("Sesli görüşme için kendi Gemini API anahtarın gerekir.", 14, muted))
        keyInput = EditText(this).apply {
            hint = if (GeminiKeyStore.load(this@MainActivity) == null)
                "API anahtarını gir" else "Anahtar kayıtlı • değiştirmek için yaz"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setSingleLine(true)
            backgroundTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        keyCard.addView(keyInput)
        keyCard.addView(actionButton("ANAHTARI KAYDET", false) {
            val value = keyInput.text.toString().trim()
            if (value.isBlank()) message("Önce API anahtarını gir")
            else try {
                GeminiKeyStore.save(this, value)
                keyInput.text.clear()
                keyInput.hint = "Anahtar kayıtlı • değiştirmek için yaz"
                message("Anahtar şifreli kaydedildi")
            } catch (e: Exception) { message("Anahtar kaydedilemedi: ${e.javaClass.simpleName}") }
        })
        root.addView(keyCard)

        root.addView(space(18))
        val diagCard = card()
        diagCard.addView(text("TANILAMA", 12, cyan, true))
        logText = text("Henüz kayıt yok", 13, muted)
        diagCard.addView(logText)
        root.addView(diagCard)
        root.addView(space(16))
        root.addView(text("JARVIS açılınca dinleme kendiliğinden başlar. “Jarvis” algılanınca Gemini sesli görüşmesi otomatik bağlanır. Bildirimden dinlemeyi durdurabilirsin.", 12, muted))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)
        setContentView(scroll)
        handler.post { startListening() }
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

    private fun updateStatus() {
        val pref = getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE)
        val engine = pref.getString("engine_state", "BEKLEMEDE") ?: "BEKLEMEDE"
        val liveState = pref.getString("live_state", "IDLE")
        val connecting = WakeWordService.running &&
            (liveState == "CONNECTING" || liveState == "GREETING")
        stateText.text = when {
            WakeWordService.liveActive -> "Gemini görüşmesi açık"
            liveState == "GREETING" -> "Gemini yanıtlıyor"
            connecting -> "Gemini bağlanıyor"
            !WakeWordService.running -> "Dinleme kapalı"
            engine == "CAPTURING" -> "Jarvis dinliyor"
            engine == "MODEL_LOADING" -> "Model yükleniyor"
            engine == "MODEL_READY" -> "Mikrofon açılıyor"
            engine.startsWith("ERROR") || engine.contains("ERROR") -> "Algılama hatası"
            else -> "Başlatılıyor"
        }
        stageText.text = if (connecting || WakeWordService.liveActive)
            "ALGILAYICI DURAKLATILDI  /  GEMINI ${liveState ?: "IDLE"}"
            else "ALGILAYICI  /  $engine"
        val level = if (WakeWordService.running && !connecting && !WakeWordService.liveActive)
            pref.getInt("mic_level", 0) else 0
        hud.update(level, WakeWordService.liveActive, WakeWordService.running)
        levelBar.progress = level
        levelText.text = "MİKROFON  $level%"
        val score = pref.getFloat("model_score", -1f)
        scoreText.text = if (score < 0 || !WakeWordService.running || connecting || WakeWordService.liveActive) "JARVIS SKORU  —"
            else "JARVIS SKORU  ${"%.2f".format(Locale.US, score)} / 0.25"
        val wakeAt = pref.getLong("last_wake_at", 0L)
        val wakeLine = if (wakeAt == 0L) "Son Jarvis tetiklemesi: yok"
            else "Son Jarvis tetiklemesi: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(wakeAt))} • skor ${"%.2f".format(Locale.US, pref.getFloat("last_wake_score", 0f))}"
        val geminiLine = "Son Gemini durumu: " +
            pref.getString("gemini_status", "Henüz bağlantı denenmedi")
        val peak = pref.getFloat("peak_score", -1f)
        val peakLine = if (peak < 0f) "Bu dinlemede en yüksek skor: henüz yok"
            else "Bu dinlemede en yüksek skor: ${"%.3f".format(Locale.US, peak)}"
        logText.text = "$wakeLine\n$geminiLine\n$peakLine\nAlgılayıcı: $engine"
    }

    private fun message(value: String) {
        getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE).edit()
            .putString("status", value).apply()
        logText.text = value
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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startWakeService(pendingStartLive)
        else message("Mikrofon izni gerekli")
    }

    private fun startWakeService(startLive: Boolean = false) {
        try {
            val intent = Intent(this, WakeWordService::class.java)
            if (startLive) intent.action = WakeWordService.ACTION_START_LIVE
            startForegroundService(intent)
            message("Servis başlatılıyor")
        } catch (e: Exception) {
            message("Başlatma hatası: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun text(value: String, sp: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(18))
        background = GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(33, 67, 87))
        }
    }

    private fun actionButton(value: String, primary: Boolean, action: () -> Unit) =
        Button(this).apply {
            text = value
            textSize = 13f
            isAllCaps = false
            setTextColor(if (primary) this@MainActivity.background else cyan)
            background = GradientDrawable().apply {
                setColor(if (primary) cyan else panel)
                cornerRadius = dp(13).toFloat()
                if (!primary) setStroke(dp(1), Color.rgb(48, 101, 121))
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        }

    private fun space(height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

