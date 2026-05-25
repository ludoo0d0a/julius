package fr.geoking.julius.settings

import android.content.Context

/**
 * Persists the selected GitHub Actions workflow id per repository (`owner/repo`).
 */
class ProjectWorkflowPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWorkflowId(projectKey: String): Long? {
        val raw = prefs.getString(workflowKey(projectKey), null) ?: return null
        return raw.toLongOrNull()
    }

    fun setWorkflowId(projectKey: String, workflowId: Long) {
        prefs.edit().putString(workflowKey(projectKey), workflowId.toString()).apply()
    }

    private fun workflowKey(projectKey: String) = "workflow_$projectKey"

    companion object {
        private const val PREFS_NAME = "github_workflow_prefs"

        fun projectKey(owner: String, repo: String): String = "$owner/$repo"
    }
}
