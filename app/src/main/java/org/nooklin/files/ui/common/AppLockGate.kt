package org.nooklin.files.ui.common

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.nooklin.files.R

/** Biometric or the screen lock of the phone, the same two ways Android offers in its own prompt. */
private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True when the phone has a screen lock or biometric that App Lock can use. */
fun canUseAppLock(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Full screen lock. Asks for the phone's own authentication, shows Retry and Close when it fails or is dismissed. */
@Composable
fun AppLockGate(onAuthenticated: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val unlocking = stringResource(R.string.lock_unlocking)
    val title = stringResource(R.string.lock_title)
    var statusText by remember { mutableStateOf(unlocking) }
    var failed by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryCount) {
        if (activity == null) {
            onClose()
            return@LaunchedEffect
        }
        failed = false
        statusText = unlocking
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    statusText = errString.toString()
                    failed = true
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    Box(
        Modifier
            .fillMaxSize()
            // Explicit background: otherwise the window background ignores the Dark and Black themes
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(statusText, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    stringResource(R.string.lock_retry),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable { retryCount++ }
                        .padding(8.dp)
                )
                Text(
                    stringResource(R.string.lock_close),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(8.dp)
                )
            }
        } else {
            Text(statusText, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
