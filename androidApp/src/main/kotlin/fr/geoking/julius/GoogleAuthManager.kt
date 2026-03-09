package fr.geoking.julius

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context, private val settingsManager: SettingsManager) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = result.credential
            if (credential is GoogleIdTokenCredential) {
                val displayName = credential.displayName ?: credential.givenName ?: "User"
                // Extract first name if possible
                val firstName = displayName.split(" ").firstOrNull() ?: displayName

                settingsManager.saveSettings(settingsManager.settings.value.copy(googleUserName = firstName))
                Result.success(firstName)
            } else {
                Result.failure(Exception("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        settingsManager.saveSettings(settingsManager.settings.value.copy(googleUserName = null))
    }
}
