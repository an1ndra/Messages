package com.anindra.messages.ui

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.anindra.messages.R
import java.util.concurrent.Executor

/** What the caller should do with an App lock toggle. */
sealed interface AppLockVerdict {
    /** The user proved who they are, so the toggle may go through. */
    data object Verified : AppLockVerdict

    /** Dismissed, or the wrong credential. Leave the setting as it was. */
    data object Rejected : AppLockVerdict

    /**
     * The device has no screen lock to verify against, so there is nothing to
     * check and the toggle cannot be made to mean anything.
     */
    data object NoScreenLock : AppLockVerdict
}

/**
 * Asks the device to confirm the user's identity before an App lock setting
 * changes, for **both** directions.
 *
 * Turning the lock off is the sensitive direction: the lock is what stands
 * between a stolen or borrowed unlocked phone and the user's messages, so
 * disabling it deserves the same check as enabling it. Only a verified caller
 * may apply the new value.
 *
 * Shared by both UIs on purpose. The rule is identical in each, and a second
 * copy is how one of them ends up skipping the check.
 */
fun verifyForAppLockChange(
    context: android.content.Context,
    onVerdict: (AppLockVerdict) -> Unit
) {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val activity = context as? FragmentActivity
    if (activity == null) {
        // A BiometricPrompt needs a FragmentActivity to attach its dialog to.
        // Without one there is no way to ask, so refuse rather than assume.
        onVerdict(AppLockVerdict.NoScreenLock)
        return
    }
    if (BiometricManager.from(activity).canAuthenticate(authenticators) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        onVerdict(AppLockVerdict.NoScreenLock)
        return
    }

    val executor: Executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onVerdict(AppLockVerdict.Verified)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onVerdict(AppLockVerdict.Rejected)
            }

            override fun onAuthenticationFailed() {
                // A single rejected finger is not a decision to give up: the
                // prompt stays up and the user can retry. Only onAuthenticationError
                // means they dismissed it.
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_change_app_lock_title))
            .setSubtitle(activity.getString(R.string.lock_change_app_lock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
    )
}
