package fr.geoking.julius

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import fr.geoking.julius.shared.ConversationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GoogleAuthManager(
    private val appContext: Context,
    private val settingsManager: SettingsManager,
    private val conversationStore: ConversationStore
) {
    private val credentialManager = CredentialManager.create(appContext)
    private val scope = CoroutineScope(Dispatchers.Main)

    fun signIn(context: Context, onResult: (Boolean, String?) -> Unit) {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is GoogleIdTokenCredential) {
                    val settings = settingsManager.settings.value
                    val firstName = credential.givenName ?: credential.displayName ?: "User"

                    settingsManager.saveSettings(settings.copy(
                        googleUserName = firstName,
                        isLoggedIn = true
                    ))

                    // Update conversation store with user name for personalized context
                    conversationStore.userName = firstName

                    onResult(true, null)
                } else {
                    onResult(false, "Unexpected credential type")
                }
            } catch (e: GetCredentialException) {
                onResult(false, e.message)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun signOut(onResult: (Boolean) -> Unit) {
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                val settings = settingsManager.settings.value
                settingsManager.saveSettings(settings.copy(
                    googleUserName = null,
                    isLoggedIn = false
                ))
                conversationStore.userName = null
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
