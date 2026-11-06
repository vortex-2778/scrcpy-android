package com.scrcpy.android.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory ring log mirrored to logcat.
 * Shown in the in-app Log screen so users can debug phone/TV pairing issues.
 */
object AppLog {

    private const val MAX_LINES = 400

    private val lines = ArrayDeque<String>()

    private val _linesFlow = MutableStateFlow<List<String>>(emptyList())
    val linesFlow: StateFlow<List<String>> = _linesFlow

    fun i(tag: String, msg: String) = add("I/$tag", msg)
    fun w(tag: String, msg: String) = add("W/$tag", msg)
    fun e(tag: String, msg: String) = add("E/$tag", msg)

    fun e(tag: String, msg: String, t: Throwable?) {
        add("E/$tag", msg + (t?.let { ": ${it.message ?: it.javaClass.name}" } ?: ""))
    }

    private fun add(level: String, msg: String) {
        val stamp = stamp()
        val line = "$stamp $level: $msg"
        Log.println(
            when {
                level.startsWith("E/") -> Log.ERROR
                level.startsWith("W/") -> Log.WARN
                else -> Log.INFO
            },
            "ScrcpyAndroid",
            msg
        )
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            _linesFlow.value = lines.toList()
        }
    }

    fun clear() {
        synchronized(lines) {
            lines.clear()
            _linesFlow.value = emptyList()
        }
    }

    private fun stamp(): String {
        val t = System.currentTimeMillis()
        val s = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        s.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return s.format(java.util.Date(t))
    }
}
