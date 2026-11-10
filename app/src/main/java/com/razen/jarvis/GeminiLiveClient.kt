package com.razen.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One Gemini Live audio session. Audio stays in memory and is never written to disk. */
class GeminiLiveClient(
    private val context: Context,
    private val apiKey: String,
    private val onStatus: (String) -> Unit,
    private val onConnected: () -> Unit,
    private val onStopped: () -> Unit,
) {
    companion object {
        private const val MODEL = "models/gemini-3.8-live"
        private const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    private val closed = AtomicBoolean(false)
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val playback = LinkedBlockingQueue<ByteArray>(200)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionTimeout = Runnable {
        if (!closed.get()) {
            onStatus("Gemini bağlantısı 20 saniyede kurulamadı. İnternet ve API anahtarını kontrol et.")
            stop()
        }
    }
    private var socket: WebSocket? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null

    fun start() {
        onStatus("Gemini Live bağlanıyor...")
        mainHandler.postDelayed(connectionTimeout, 20_000)
        // Do not log this URL: it contains the user's API key.
        val url = "$ENDPOINT?key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus("Gemini WebSocket açıldı; ses oturumu hazırlanıyor...")
                val setup = JSONObject().put("setup", JSONObject()
                    .put("model", MODEL)
                    .put("generationConfig", JSONObject()
                        .put("responseModalities", org.json.JSONArray().put("AUDIO")))
                    .put("systemInstruction", JSONObject().put("parts", org.json.JSONArray()
                        .put(JSONObject().put("text", "Sen JARVIS adlı Türkçe sesli asistansın. Kısa ve doğal Türkçe konuş.")))))
                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed.get()) return
                try {
                    val message = JSONObject(text)
                    if (message.has("setupComplete")) {
                        mainHandler.removeCallbacks(connectionTimeout)
                        startAudio(webSocket)
                        if (closed.get()) return
                        onConnected()
                        onStatus("Gemini Live bağlı; konuşabilirsiniz")
                        // The wake phrase gets an immediate spoken acknowledgement.
                        val greeting = JSONObject().put("clientContent", JSONObject()
                            .put("turns", org.json.JSONArray().put(JSONObject()
                                .put("role", "user")
                                .put("parts", org.json.JSONArray().put(JSONObject()
                                    .put("text", "Bana yalnızca 'Buyurun efendim.' diye sesli yanıt ver; sonra komutumu dinle.")))))
                            .put("turnComplete", true))
                        webSocket.send(greeting.toString())
                    }
                    val content = message.optJSONObject("serverContent") ?: return
                    if (content.optBoolean("interrupted")) {
                        playback.clear()
                        player?.pause()
                        player?.flush()
                        player?.play()
                    }
                    val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts") ?: return
                    for (i in 0 until parts.length()) {
                        val data = parts.optJSONObject(i)?.optJSONObject("inlineData")
                            ?.optString("data").orEmpty()
                        if (data.isNotEmpty()) playback.offer(Base64.decode(data, Base64.DEFAULT))
                    }
                } catch (_: Exception) { onStatus("Gemini yanıtı işlenemedi") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closed.get()) return
                // Exception text may contain the authenticated URL; only report class/status.
                onStatus("Gemini bağlantı hatası: HTTP ${response?.code ?: "yok"}, ${t.javaClass.simpleName}")
                stop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (closed.get()) return
                onStatus("Gemini görüşmesi kapandı (kod $code)")
                stop()
            }
        })
    }

    private fun startAudio(webSocket: WebSocket) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onStatus("Mikrofon izni yok")
            stop()
            return
        }
        try {
            val inputBuffer = AudioRecord.getMinBufferSize(16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4_096)
            val input = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inputBuffer)
            if (input.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("AudioRecord")
            recorder = input
            val outputBuffer = AudioTrack.getMinBufferSize(24_000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4_096)
            val output = AudioTrack(AudioManager.STREAM_MUSIC, 24_000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                outputBuffer, AudioTrack.MODE_STREAM)
            if (output.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("AudioTrack")
            player = output
            output.play()
            input.startRecording()
            Thread({
                val chunk = ByteArray(1_280) // 40 ms, PCM16 mono at 16 kHz.
                while (!closed.get()) {
                    val count = try { input.read(chunk, 0, chunk.size) } catch (_: Exception) { break }
                    if (count <= 0) break
                    val data = Base64.encodeToString(chunk.copyOf(count), Base64.NO_WRAP)
                    val event = JSONObject().put("realtimeInput", JSONObject().put("audio",
                        JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", data)))
                    if (!webSocket.send(event.toString())) break
                }
            }, "JarvisGeminiMic").start()
            Thread({
                while (!closed.get()) {
                    val chunk = try { playback.poll(500, TimeUnit.MILLISECONDS) } catch (_: Exception) { null }
                    if (chunk != null) try { output.write(chunk, 0, chunk.size) } catch (_: Exception) { break }
                }
            }, "JarvisGeminiSpeaker").start()
        } catch (e: Exception) {
            onStatus("Ses donanımı açılamadı: ${e.javaClass.simpleName}")
            stop()
        }
    }

    fun stop() {
        if (!closed.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(connectionTimeout)
        playback.clear()
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        recorder = null
        player = null
        socket?.close(1000, "done")
        client.dispatcher.executorService.shutdown()
        onStopped()
    }
}


