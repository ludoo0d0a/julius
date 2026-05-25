package fr.geoking.julius.ui

/**
 * GitHub classic PAT creation with scopes pre-selected for Julius (PRs, file content, Actions).
 * @see [GitHub docs](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)
 */
object GitHubPatUrls {
    const val CREATE_CLASSIC_PAT =
        "https://github.com/settings/tokens/new?description=Julius&scopes=repo,workflow"

    /** Human-readable scopes matching [CREATE_CLASSIC_PAT]. */
    val CLASSIC_SCOPES = listOf(
        "repo" to "Pull requests, file content, merge/close, and comments",
        "workflow" to "GitHub Actions workflows and run status",
    )
}
