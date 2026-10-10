package com.anindra.messages.sms

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager

/**
 * Full-screen-intent target for an incoming message. Exists to turn the screen
 * on over the keyguard, then get out of the way and let the heads-up
 * notification be tapped like any other lock-screen notification.
 *
 * Deliberately not MainActivity: the platform full-screen-intent policy requires
 * the target not be the app's primary launch activity, and a real lock screen
 * takeover is not the same as cold-launching the app.
 */
class FullScreenSmsActivity : Activity() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverKeyguard()

        if (savedInstanceState != null) {
            finish()
            return
        }

        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
            acquireWakeLock()
            window.decorView.postDelayed({ finish() }, 250)
        } else {
            finish()
        }
    }

    private fun showOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Messages:full-screen-sms"
        ).apply { acquire(3000) }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_SHOW = "com.anindra.messages.SHOW_FULL_SCREEN_SMS"
    }
}
