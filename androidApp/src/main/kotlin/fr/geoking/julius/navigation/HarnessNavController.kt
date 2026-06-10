package fr.geoking.julius.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class HarnessNavController(
    initial: HarnessRoute = HarnessRoute.QueueDashboard,
) {
    private val _stack: SnapshotStateList<HarnessRoute> = mutableStateListOf(initial)

    val stack: List<HarnessRoute> get() = _stack
    val current: HarnessRoute get() = _stack.last()

    fun push(route: HarnessRoute) {
        _stack.add(route)
    }

    fun pop(): Boolean {
        if (_stack.size <= 1) return false
        _stack.removeAt(_stack.lastIndex)
        return true
    }

    fun popTo(route: HarnessRoute): Boolean {
        val index = _stack.indexOfLast { it::class == route::class && it == route }
        if (index < 0) return false
        while (_stack.size > index + 1) {
            _stack.removeAt(_stack.lastIndex)
        }
        return true
    }

    fun resetTo(route: HarnessRoute) {
        _stack.clear()
        _stack.add(route)
    }

    fun canPop(): Boolean = _stack.size > 1
}
