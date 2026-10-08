package com.anindra.messages.sms

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import com.anindra.messages.MainActivity

/**
 * Full-screen-intent target for an incoming message. Exists to turn the screen
 * on over the keyguard, then hand off to MainActivity and get out of the way.
 *
 * Deliberately not MainActivity: the platform full-screen-intent policy requires
 * the target not be the app's primary launch activity.
 */
class FullScreenSmsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverKeyguard()

        // The keyguard can be dismissed between the notification posting and this
        // launch. Showing a wake screen over a screen the user is already using
        // would be a hijack, so bail and let the notification stand on its own.
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true) {
            finish()
            return
        }

        val next = Intent(this, MainActivity::class.java)
        next.putExtra("open_conversation_address", intent.getStringExtra(EXTRA_ADDRESS))
        next.setPackage(packageName)
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(next)
        finish()
    }

    private fun showOverKeyguard() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    companion object {
        const val ACTION_SHOW = "com.anindra.messages.SHOW_FULL_SCREEN_SMS"
        const val EXTRA_ADDRESS = "open_conversation_address"
    }
}