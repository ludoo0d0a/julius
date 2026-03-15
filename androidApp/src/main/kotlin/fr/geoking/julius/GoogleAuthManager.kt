package fr.geoking.julius

import android.content.Context
import android.util.Log
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

private const val TAG = "GoogleAuth"

class GoogleAuthManager(
    private val appContext: Context,
    private val settingsManager: SettingsManager,
    private val conversationStore: ConversationStore
) {
    private val credentialManager = CredentialManager.create(appContext)
    private val scope = CoroutineScope(Dispatchers.Main)

    fun signIn(context: Context, onResult: (Boolean, String?) -> Unit) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        val isPlaceholder = clientId.isBlank() || clientId.contains("placeholder", ignoreCase = true)
        Log.d(TAG, "signIn: clientId configured=${!isPlaceholder}, length=${clientId.length}")
        if (isPlaceholder) {
            Log.w(TAG, "signIn: GOOGLE_WEB_CLIENT_ID is missing or placeholder; add it in GitHub secrets and rebuild")
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                Log.d(TAG, "signIn: requesting credential...")
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is GoogleIdTokenCredential) {
                    val settings = settingsManager.settings.value
                    val firstName = credential.givenName ?: credential.displayName ?: "User"
                    Log.d(TAG, "signIn: success user=$firstName")

                    settingsManager.saveSettings(settings.copy(
                        googleUserName = firstName,
                        isLoggedIn = true
                    ))

                    conversationStore.userName = firstName
                    onResult(true, null)
                } else {
                    val msg = "Unexpected credential type: ${credential?.javaClass?.simpleName ?: "null"}"
                    Log.e(TAG, "signIn: $msg")
                    conversationStore.recordError(null, "Google Auth: $msg")
                    onResult(false, msg)
                }
            } catch (e: GetCredentialException) {
                val detail = buildErrorDetail(e)
                Log.e(TAG, "signIn: GetCredentialException $detail", e)
                conversationStore.recordError(null, "Google Auth: $detail")
                onResult(false, e.message ?: detail)
            } catch (e: Exception) {
                val detail = buildErrorDetail(e)
                Log.e(TAG, "signIn: Exception $detail", e)
                conversationStore.recordError(null, "Google Auth: $detail")
                onResult(false, e.message ?: detail)
            }
        }
    }

    private fun buildErrorDetail(e: Throwable): String {
        val type = e.javaClass.simpleName
        val msg = e.message ?: "no message"
        val cause = e.cause?.let { " cause=${it.javaClass.simpleName}: ${it.message}" } ?: ""
        return "$type: $msg$cause"
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
                Log.d(TAG, "signOut: success")
                onResult(true)
            } catch (e: Exception) {
                val detail = buildErrorDetail(e)
                Log.e(TAG, "signOut: $detail", e)
                conversationStore.recordError(null, "Google Auth sign-out: $detail")
                onResult(false)
            }
        }
    }
}
