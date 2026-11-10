package com.razen.jarvis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/** Requests the documented Android assist action only when ChatGPT owns the assistant role. */
object AssistantHandoff {
    fun chatGptOwnsRole(context: Context): Boolean {
        return try {
            val component = Settings.Secure.getString(context.contentResolver, "assistant")
            component?.startsWith("com.openai.chatgpt/") == true
        } catch (e: Exception) {
            Log.w("Jarvis", "Assistant role lookup failed", e)
            false
        }
    }

    fun request(context: Context): Boolean {
        if (!chatGptOwnsRole(context)) return false
        return try {
            context.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true // Android can still reject a background launch without an exception.
        } catch (e: Exception) {
            Log.w("Jarvis", "Android assist request failed", e)
            false
        }
    }
}

