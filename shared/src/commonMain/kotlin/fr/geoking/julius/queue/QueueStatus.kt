package fr.geoking.julius.queue

data class AccountQuotaRow(
    val accountId: String,
    val label: String,
    val usedToday: Int,
    val dailyLimit: Int,
    val activeSessions: Int,
    val enabled: Boolean,
)

data class QueueStatus(
    val paused: Boolean = false,
    val activeCount: Int = 0,
    val parallelLimit: Int = 3,
    val pendingCount: Int = 0,
    val accounts: List<AccountQuotaRow> = emptyList(),
)
