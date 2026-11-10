package com.razen.jarvis

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

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
        root.addView(label("0.3.0 • Tek kelime: JARVIS", 17, Color.LTGRAY))
        status = label("Durum okunuyor", 19, Color.WHITE)
        root.addView(status)

        root.addView(button("1 — Dinlemeyi başlat") { startListening() })
        root.addView(button("2 — Sesli yanıtı test et") {
            if (WakeWordService.running) {
                startService(Intent(this, WakeWordService::class.java)
                    .setAction(WakeWordService.ACTION_TEST))
            } else status.text = "Önce dinlemeyi başlat"
        })
        root.addView(button("3 — ChatGPT sesli asistanını aç") { openChatGpt() })
        root.addView(button("Dinlemeyi durdur") {
            stopService(Intent(this, WakeWordService::class.java))
            status.text = "Durduruldu"
        })

        detail = label("", 14, Color.rgb(171, 210, 225))
        root.addView(detail)
        root.addView(label(
            "Kullanım: 1'e bas, telefonu açıkken ya da kilitliyken 'Jarvis' de. " +
            "Uygulama açıkken algılarsa ChatGPT'yi açmayı dener. " +
            "Ekran kilitliyken Android'in güvenlik kuralları nedeniyle bildirim kullanılır.",
            14, Color.LTGRAY
        ))
        root.addView(label(
            "ChatGPT → Ayarlar → Ses → Sesle Başlat özelliğini aç. " +
            "ChatGPT'nin Android'de varsayılan asistan olarak seçilmesi " +
            "uygun cihazlarda sesli asistan açılışını kolaylaştırabilir. " +
            "Bu APK ChatGPT kilidini açamaz, sesli görüşmeyi her durumda doğrudan başlatamaz.",
            13, Color.rgb(155, 175, 190)
        ))
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
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

    private fun updateStatus() {
        status.text = if (WakeWordService.running) "Servis açık • JARVIS dinliyor" else "Servis kapalı"
        val pref = getSharedPreferences("jarvis_diag", Context.MODE_PRIVATE)
        detail.text = "Tanılama: " + pref.getString("status", "Henüz kayıt yok") +
            "\nTelefonunda üst çubukta mikrofon kullanımı simgesi görünüyor mu?"
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
        // ACTION_ASSIST routes to the user's chosen system digital assistant.
        // ChatGPT must be selected in Android Settings for it to open ChatGPT Voice.
        try {
            val defaultAssistant = Settings.Secure.getString(contentResolver, "assistant") ?: ""
            val assist = Intent(Intent.ACTION_ASSIST)
            if (defaultAssistant.contains("com.openai.chatgpt") &&
                assist.resolveActivity(packageManager) != null) {
                startActivity(assist)
                return
            }
        } catch (_: Exception) { }
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