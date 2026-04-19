package com.example.fishclassification.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
) {
    fun formatTime(): String = TIME_FORMAT.format(Date(timestampMs))

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * Fans log events to logcat AND to an in-memory ring buffer surfaced via
 * [logs]. The in-app Log screen observes [logs] so the user can diagnose
 * failures without `adb logcat`.
 */
object AppLogger {

    private const val CAPACITY = 500

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun d(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.DEBUG, tag, message, throwable).also { Log.d(tag, message, throwable) }

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.INFO, tag, message, throwable).also { Log.i(tag, message, throwable) }

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable).also { Log.w(tag, message, throwable) }

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable).also { Log.e(tag, message, throwable) }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwable)
        _logs.update { current ->
            val next = current + entry
            if (next.size > CAPACITY) next.takeLast(CAPACITY) else next
        }
    }
}
