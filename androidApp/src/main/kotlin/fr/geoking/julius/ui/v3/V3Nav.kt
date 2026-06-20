package fr.geoking.julius.ui.v3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf

/** v3 navigation routes. Tab roots + pushed detail routes. */
sealed class V3Route {
    // tab roots
    data object Scheduler : V3Route()
    data object Features : V3Route()
    data object Projects : V3Route()
    data object Settings : V3Route()

    // pushed routes
    data class ProjectFeatures(val sourceName: String) : V3Route()
    data class FeatureDetail(val featureId: String) : V3Route()
    data class Conversation(val sessionId: String) : V3Route()
    data class GitCi(val owner: String, val repo: String) : V3Route()
    data class PrConflict(val sessionId: String, val prUrl: String) : V3Route()
    data class AddFeature(val sourceName: String?) : V3Route()
    data class AgentDetail(val accountId: String?) : V3Route()
    data class AgentBilling(val targetKey: String) : V3Route()
    data class JsonDebug(val title: String, val json: String) : V3Route()

    /** Which bottom-bar tab is highlighted for this route. */
    val tabRoot: V3Route
        get() = when (this) {
            is Scheduler -> Scheduler
            is Features -> Features
            is Settings, is AgentDetail, is AgentBilling -> Settings
            is Projects -> Projects
            is ProjectFeatures, is FeatureDetail, is Conversation, is GitCi, is PrConflict, is AddFeature, is JsonDebug -> Features
        }
}

val V3_TABS: List<V3Route> = listOf(V3Route.Scheduler, V3Route.Features, V3Route.Projects, V3Route.Settings)

/** Tiny in-memory navigation stack for v3 routes. */
class V3NavController {
    private val _stack = mutableStateListOf<V3Route>(V3Route.Scheduler)
    val current: V3Route get() = _stack.last()
    val canPop: Boolean get() = _stack.size > 1

    /** Bottom-bar highlight follows the tab the stack is rooted on (where you navigated from),
     *  not the type of the current detail route. */
    val activeTab: V3Route get() = _stack.first().tabRoot

    fun push(route: V3Route) { _stack.add(route) }

    fun pop(): Boolean {
        if (_stack.size > 1) { _stack.removeAt(_stack.lastIndex); return true }
        return false
    }

    /** Switch bottom-bar tab: collapse to that single root. */
    fun selectTab(route: V3Route) {
        _stack.clear()
        _stack.add(route)
    }
}

@Composable
fun rememberV3NavController(): V3NavController = remember { V3NavController() }
