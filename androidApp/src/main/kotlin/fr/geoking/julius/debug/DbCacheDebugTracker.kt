package fr.geoking.julius.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DbEntityKind(val label: String) {
    PROJECTS("Projects"),
    SESSIONS("Sessions"),
    ACTIVITIES("Activities"),
    FEATURES("Features"),
}

data class DbCacheCounts(
    val saved: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
) {
    fun plusDelta(saved: Int = 0, updated: Int = 0, deleted: Int = 0): DbCacheCounts =
        copy(
            saved = this.saved + saved,
            updated = this.updated + updated,
            deleted = this.deleted + deleted,
        )

    fun isEmpty(): Boolean = saved == 0 && updated == 0 && deleted == 0

    val total: Int get() = saved + updated + deleted
}

data class DbCacheDebugState(
    val loading: Boolean = false,
    val currentOp: String? = null,
    val current: Map<DbEntityKind, DbCacheCounts> = emptyMap(),
    val lifetime: Map<DbEntityKind, DbCacheCounts> = emptyMap(),
    val lastFinishedAt: Long? = null,
    val lastError: String? = null,
) {
    fun currentTotals(): DbCacheCounts = current.values.fold(DbCacheCounts()) { acc, c ->
        acc.plusDelta(c.saved, c.updated, c.deleted)
    }

    fun lifetimeTotals(): DbCacheCounts = lifetime.values.fold(DbCacheCounts()) { acc, c ->
        acc.plusDelta(c.saved, c.updated, c.deleted)
    }
}

/** Tracks Room writes during cache refresh cycles (sources, sessions, activities, features). */
class DbCacheDebugTracker {
    private val mutex = Mutex()
    private var depth = 0
    private val _state = MutableStateFlow(DbCacheDebugState())
    val state: StateFlow<DbCacheDebugState> = _state.asStateFlow()

    suspend fun begin(operation: String) {
        mutex.withLock {
            if (depth == 0) {
                _state.value = _state.value.copy(
                    loading = true,
                    currentOp = operation,
                    current = emptyMap(),
                    lastError = null,
                )
            } else {
                _state.value = _state.value.copy(currentOp = operation, loading = true)
            }
            depth++
        }
    }

    suspend fun record(
        kind: DbEntityKind,
        saved: Int = 0,
        updated: Int = 0,
        deleted: Int = 0,
    ) {
        if (saved == 0 && updated == 0 && deleted == 0) return
        mutex.withLock {
            val cur = _state.value.current[kind] ?: DbCacheCounts()
            val life = _state.value.lifetime[kind] ?: DbCacheCounts()
            val delta = cur.plusDelta(saved, updated, deleted)
            val lifeDelta = life.plusDelta(saved, updated, deleted)
            _state.value = _state.value.copy(
                current = _state.value.current + (kind to delta),
                lifetime = _state.value.lifetime + (kind to lifeDelta),
            )
        }
    }

    suspend fun finish(error: String? = null) {
        mutex.withLock {
            depth = (depth - 1).coerceAtLeast(0)
            if (depth == 0) {
                _state.value = _state.value.copy(
                    loading = false,
                    lastFinishedAt = System.currentTimeMillis(),
                    lastError = error,
                )
            }
        }
    }

    suspend fun resetLifetime() {
        mutex.withLock {
            _state.value = _state.value.copy(lifetime = emptyMap())
        }
    }

    suspend fun clear() {
        mutex.withLock {
            depth = 0
            _state.value = DbCacheDebugState()
        }
    }
}
