package com.anindra.messages.data

/** What a launch asks for, after the default-SMS role has had its say.
 *
 *  The role grants the whole SMS set on its own, so asking for those
 *  permissions before the role dialog resolves showed a dialog for something
 *  the role was about to hand over anyway. Contacts never belong to a launch
 *  ask: they are requested at the first moment they are actually needed, and
 *  the list already renders raw numbers until then. */
object LaunchPermissions {

    val SMS_SET = listOf(
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
    )

    const val CONTACTS = "android.permission.READ_CONTACTS"

    /** Runtime-only from API 33; before that the OS grants it by default. */
    const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    fun stillNeeded(
        roleHeld: Boolean,
        sdkInt: Int,
        granted: (String) -> Boolean
    ): List<String> {
        val needed = mutableListOf<String>()
        if (!roleHeld) needed += SMS_SET.filter { !granted(it) }
        if (sdkInt >= 33 && !granted(NOTIFICATIONS)) needed += NOTIFICATIONS
        return needed
    }
}
