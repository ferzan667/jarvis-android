package com.razen.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val permissionRequest = 210

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 15, 25)
        window.navigationBarColor = Color.rgb(8, 15, 25)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(76), dp(24), dp(24))
            setBackgroundColor(Color.rgb(8, 15, 25))
        }
        root.addView(label("J A R V I S", 32, Color.rgb(91, 226, 255)))
        root.addView(label("Hey Jarvis • Yerel ses algılama", 15, Color.LTGRAY))
        status = label("Kapalı", 18, Color.WHITE)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Dinlemeyi başlat"
            setOnClickListener { startListening() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "Dinlemeyi durdur"
            setOnClickListener {
                stopService(Intent(this@MainActivity, WakeWordService::class.java))
                status.text = "Durduruldu"
            }
        }, buttonParams())
        root.addView(label(
            "Uygulama açıkken dinlemeyi başlat. Telefon kilitliyken 'Hey Jarvis' de. Algılanırsa 'Buyurun efendim' yanıtını alırsın.",
            15, Color.LTGRAY
        ))
        root.addView(label(
            "ChatGPT'ye geçiş için bildirime dokunman gerekir. Android kilidini aşamaz veya ChatGPT'nin sesli modunu kendiliğinden açamaz.",
            13, Color.rgb(170, 180, 195)
        ))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (WakeWordService.running) "Dinliyor • Hey Jarvis" else "Kapalı"
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

    private fun label(value: String, sp: Int, color: Int) = TextView(this).apply {
        text = value
        textSize = sp.toFloat()
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
    }
    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}