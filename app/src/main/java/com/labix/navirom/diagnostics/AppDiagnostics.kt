package com.labix.navirom.diagnostics

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val errorCode: String,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val contextInfo: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

object DiagnosticCodes {
    // System & Lifecycle
    const val SYS_STARTUP_001 = "SYS_STARTUP_001"
    const val SYS_UNCAUGHT_CRASH_999 = "SYS_UNCAUGHT_CRASH_999"

    // Network & Subsonic API
    const val NET_CONNECT_FAIL_201 = "NET_CONNECT_FAIL_201"
    const val NET_HTTP_ERROR_202 = "NET_HTTP_ERROR_202"
    const val NET_JSON_PARSE_203 = "NET_JSON_PARSE_203"
    const val NET_TIMEOUT_204 = "NET_TIMEOUT_204"

    // Audio Player
    const val PLAYER_INIT_301 = "PLAYER_INIT_301"
    const val PLAYER_PREPARE_FAIL_302 = "PLAYER_PREPARE_FAIL_302"
    const val PLAYER_PLAYBACK_ERR_303 = "PLAYER_PLAYBACK_ERR_303"
    const val PLAYER_RELEASE_ERR_304 = "PLAYER_RELEASE_ERR_304"
    const val PLAYER_CROSSFADE_ERR_305 = "PLAYER_CROSSFADE_ERR_305"

    // Audio Focus & System Media
    const val AUDIO_FOCUS_LOST_401 = "AUDIO_FOCUS_LOST_401"
    const val AUDIO_NOISY_DISCONNECT_402 = "AUDIO_NOISY_DISCONNECT_402"
    const val MEDIA_BUTTON_EVENT_403 = "MEDIA_BUTTON_EVENT_403"

    // Offline Cache & Storage
    const val CACHE_DOWNLOAD_FAIL_501 = "CACHE_DOWNLOAD_FAIL_501"
    const val CACHE_WRITE_ERR_502 = "CACHE_WRITE_ERR_502"
    const val CACHE_DELETE_ERR_503 = "CACHE_DELETE_ERR_503"

    // Lyrics
    const val LYRICS_FETCH_WARN_601 = "LYRICS_FETCH_WARN_601"
    const val LYRICS_FETCH_ERR_602 = "LYRICS_FETCH_ERR_602"
}

object AppDiagnostics {
    private const val TAG = "AppDiagnostics"
    private const val MAX_LOGS = 500
    private const val LOG_FILE_NAME = "app_diagnostics_logs.json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logFile: File? = null

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        loadLogsFromDisk()
        setupUncaughtExceptionHandler()
        logInfo(DiagnosticCodes.SYS_STARTUP_001, TAG, "App diagnostics initialized successfully")
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = Log.getStackTraceString(throwable)
            val entry = LogEntry(
                level = LogLevel.ERROR,
                errorCode = DiagnosticCodes.SYS_UNCAUGHT_CRASH_999,
                tag = "UncaughtCrash",
                message = "Fatal uncaught crash on thread '${thread.name}': ${throwable.message}",
                stackTrace = stackTrace
            )
            appendLogSync(entry)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun log(
        level: LogLevel,
        errorCode: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        contextInfo: String? = null
    ) {
        val stackTrace = throwable?.let { Log.getStackTraceString(it) }
        val entry = LogEntry(
            level = level,
            errorCode = errorCode,
            tag = tag,
            message = message,
            stackTrace = stackTrace,
            contextInfo = contextInfo
        )

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, "[$errorCode] $message", throwable)
            LogLevel.INFO -> Log.i(tag, "[$errorCode] $message", throwable)
            LogLevel.WARN -> Log.w(tag, "[$errorCode] $message", throwable)
            LogLevel.ERROR -> Log.e(tag, "[$errorCode] $message", throwable)
        }

        synchronized(this) {
            val current = _logsFlow.value.toMutableList()
            current.add(0, entry) // Newest first
            if (current.size > MAX_LOGS) {
                current.removeAt(current.lastIndex)
            }
            _logsFlow.value = current
        }

        scope.launch {
            saveLogsToDisk()
        }
    }

    fun logError(
        errorCode: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        contextInfo: String? = null
    ) {
        log(LogLevel.ERROR, errorCode, tag, message, throwable, contextInfo)
    }

    fun logWarn(
        errorCode: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        contextInfo: String? = null
    ) {
        log(LogLevel.WARN, errorCode, tag, message, throwable, contextInfo)
    }

    fun logInfo(
        errorCode: String,
        tag: String,
        message: String,
        contextInfo: String? = null
    ) {
        log(LogLevel.INFO, errorCode, tag, message, null, contextInfo)
    }

    fun logDebug(
        errorCode: String,
        tag: String,
        message: String,
        contextInfo: String? = null
    ) {
        log(LogLevel.DEBUG, errorCode, tag, message, null, contextInfo)
    }

    fun clearLogs() {
        synchronized(this) {
            _logsFlow.value = emptyList()
        }
        scope.launch {
            try {
                logFile?.delete()
            } catch (_: Exception) {}
        }
    }

    fun exportFormattedLogs(): String {
        val logs = _logsFlow.value
        val sb = StringBuilder()
        sb.append("=== NAVIROM DIAGNOSTIC LOGS ===\n")
        sb.append("Total Entries: ${logs.size}\n")
        sb.append("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("================================\n\n")

        for (entry in logs) {
            sb.append("[${entry.formattedDate}] [${entry.level.name}] [${entry.errorCode}] [${entry.tag}]\n")
            sb.append("Message: ${entry.message}\n")
            if (!entry.contextInfo.isNullOrBlank()) {
                sb.append("Context: ${entry.contextInfo}\n")
            }
            if (!entry.stackTrace.isNullOrBlank()) {
                sb.append("StackTrace:\n${entry.stackTrace}\n")
            }
            sb.append("------------------------------------------------\n")
        }
        return sb.toString()
    }

    @Synchronized
    private fun appendLogSync(entry: LogEntry) {
        val current = _logsFlow.value.toMutableList()
        current.add(0, entry)
        _logsFlow.value = current
        saveLogsToDiskSync()
    }

    private fun saveLogsToDisk() {
        try {
            saveLogsToDiskSync()
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun saveLogsToDiskSync() {
        val file = logFile ?: return
        try {
            val array = JSONArray()
            val list = _logsFlow.value.take(MAX_LOGS)
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("level", item.level.name)
                    put("errorCode", item.errorCode)
                    put("tag", item.tag)
                    put("message", item.message)
                    put("stackTrace", item.stackTrace ?: "")
                    put("contextInfo", item.contextInfo ?: "")
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {}
    }

    private fun loadLogsFromDisk() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            val text = file.readText()
            if (text.isBlank()) return
            val array = JSONArray(text)
            val loadedList = mutableListOf<LogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val entry = LogEntry(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    level = try { LogLevel.valueOf(obj.getString("level")) } catch (_: Exception) { LogLevel.INFO },
                    errorCode = obj.optString("errorCode", "UNKNOWN"),
                    tag = obj.optString("tag", "General"),
                    message = obj.optString("message", ""),
                    stackTrace = obj.optString("stackTrace").ifEmpty { null },
                    contextInfo = obj.optString("contextInfo").ifEmpty { null }
                )
                loadedList.add(entry)
            }
            _logsFlow.value = loadedList
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load logs from disk", e)
        }
    }
}
