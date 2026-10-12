package com.anindra.messages.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.anindra.messages.R
import com.anindra.messages.data.MessageLockCrypto
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
 * The verdict for a completed prompt, split out so it is testable without a real
 * biometric prompt. [proofOk] is false only when the authenticated cipher was
 * present and failed to complete a real crypto operation, which is the
 * UI-hooking case the CryptoObject is there to catch.
 */
internal fun appLockVerdict(
    hasActivity: Boolean,
    canAuthenticate: Boolean,
    proofOk: Boolean
): AppLockVerdict = when {
    !hasActivity || !canAuthenticate -> AppLockVerdict.NoScreenLock
    proofOk -> AppLockVerdict.Verified
    else -> AppLockVerdict.Rejected
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
 * The prompt is tied to a Keystore key that requires user authentication, and
 * the result is applied only after the returned cipher performs a real crypto
 * operation. A bare success callback is a boolean that a UI-hooking tool can
 * flip; the CryptoObject makes the unlock depend on the key actually being
 * released by a live authentication.
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
        onVerdict(appLockVerdict(hasActivity = false, canAuthenticate = false, proofOk = false))
        return
    }
    if (BiometricManager.from(activity).canAuthenticate(authenticators) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        onVerdict(appLockVerdict(hasActivity = true, canAuthenticate = false, proofOk = false))
        return
    }

    val executor: Executor = ContextCompat.getMainExecutor(activity)
    val authCipher = MessageLockCrypto.newAuthCipher()
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // A null cipher is the device-credential path on API 29, where
                // crypto-bound auth is not available; it stays accepted so a
                // PIN-only user is not locked out of the setting.
                val proofOk = result.cryptoObject?.cipher?.let { MessageLockCrypto.proveAuth(it) }
                    ?: true
                onVerdict(appLockVerdict(hasActivity = true, canAuthenticate = true, proofOk = proofOk))
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
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.lock_change_app_lock_title))
        .setSubtitle(activity.getString(R.string.lock_change_app_lock_subtitle))
        .setAllowedAuthenticators(authenticators)
        .build()
    if (authCipher != null) {
        prompt.authenticate(info, BiometricPrompt.CryptoObject(authCipher))
    } else {
        prompt.authenticate(info)
    }
}
