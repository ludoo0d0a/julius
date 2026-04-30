package fr.geoking.julius.logging

import android.os.Process as AndroidProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader

data class LogcatLine(
    val raw: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Streams device logcat output for the current app process.
 *
 * Notes:
 * - On modern Android, apps can generally read their own process' log output without privileged permissions.
 * - We filter by PID to avoid pulling unrelated system logs.
 */
object LogcatStore {
    private const val MAX_LINES = 2000

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

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return

        _lastError.value = null
        _isRunning.value = true

        job = scope.launch(Dispatchers.IO) {
            val pid = AndroidProcess.myPid()
            val cmd = listOf(
                "logcat",
                "-v", "time",
                "--pid", pid.toString()
            )

            try {
                val pb = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                val p = pb.start()
                process = p

                p.inputStream.bufferedReader().use { reader ->
                    streamLines(reader)
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

    private suspend fun streamLines(reader: BufferedReader) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                ensureActive()
                val line = reader.readLine() ?: break
                append(line)
            }
        }
    }

    private fun append(rawLine: String) {
        val line = LogcatLine(raw = rawLine)
        _lines.update { current ->
            val next = if (current.size >= MAX_LINES) {
                // drop oldest chunk to avoid O(n) on each line for large lists
                current.drop(current.size / 4) + line
            } else {
                current + line
            }
            next
        }
    }
}

