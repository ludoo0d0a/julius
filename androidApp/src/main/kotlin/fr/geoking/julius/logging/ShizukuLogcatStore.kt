package fr.geoking.julius.logging

import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.coroutines.coroutineContext
import java.io.BufferedReader

/**
 * Streams full device logcat using Shizuku/Sui.
 *
 * This requires:
 * - Shizuku (or Sui) available
 * - User granted Shizuku permission to this app
 */
object ShizukuLogcatStore {
    private const val MAX_LINES = 5000

    private val _lines = MutableStateFlow<List<LogcatLine>>(emptyList())
    val lines: StateFlow<List<LogcatLine>> = _lines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var process: java.lang.Process? = null
    private var job: Job? = null

    fun clear() {
        _lines.value = emptyList()
        _lastError.value = null
    }

    fun canUseShizuku(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (t: Throwable) {
            _lastError.value = t.message ?: t.toString()
        }
    }

    fun start(scope: CoroutineScope, extraArgs: List<String> = emptyList()) {
        if (job?.isActive == true) return

        _lastError.value = null
        _isRunning.value = true

        job = scope.launch(Dispatchers.IO) {
            val cmd = buildList {
                add("logcat")
                add("-v"); add("time")
                addAll(extraArgs)
            }

            try {
                if (!canUseShizuku()) {
                    _lastError.value = "Shizuku/Sui not available"
                    return@launch
                }
                if (!hasPermission()) {
                    _lastError.value = "Shizuku permission not granted"
                    return@launch
                }

                // NOTE: Shizuku is deprecating/removing newProcess in favor of UserService.
                // Some versions also hide it from direct calls. We keep a reflective fallback here
                // because this feature is strictly for debugging.
                val p = run {
                    val arr = cmd.toTypedArray()
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val m = Shizuku::class.java.getDeclaredMethod(
                            "newProcess",
                            Array<String>::class.java,
                            Array<String>::class.java,
                            String::class.java
                        ).apply { isAccessible = true }
                        m.invoke(null, arr, null, null) as java.lang.Process
                    } catch (t: Throwable) {
                        throw IllegalStateException("Shizuku newProcess unavailable (use UserService). ${t.message}", t)
                    }
                }
                process = p
                p.inputStream.bufferedReader().use { reader ->
                    stream(reader)
                }
            } catch (t: Throwable) {
                _lastError.value = t.message ?: t.toString()
            } finally {
                _isRunning.value = false
                try {
                    process?.destroy()
                } catch (_: Throwable) {
                }
                process = null
            }
        }
    }

    suspend fun stop() {
        _isRunning.value = false
        try {
            process?.destroy()
        } catch (_: Throwable) {
        } finally {
            process = null
        }
        job?.cancel()
        job?.cancelAndJoin()
        job = null
    }

    private suspend fun stream(reader: BufferedReader) {
        while (true) {
            coroutineContext.ensureActive()
            val line = reader.readLine() ?: return
            append(line)
        }
    }

    private fun append(rawLine: String) {
        val line = LogcatLine(raw = rawLine)
        _lines.update { current ->
            val next = if (current.size >= MAX_LINES) {
                current.drop(current.size / 4) + line
            } else {
                current + line
            }
            next
        }
    }
}

