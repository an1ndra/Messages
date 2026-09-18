package com.anindra.messages.data

import android.annotation.TargetApi
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract

/**
 * `ContactsContract.CommonDataKinds.Phone.ENTERPRISE_CONTENT_URI` (the
 * cross-profile work-contact URI) was added in **API 34**. Referencing it on an
 * older device throws `NoSuchFieldError` — an `Error`, not an `Exception`, so a
 * `catch (Exception)` will not save you. It must be guarded by SDK level, and
 * the reference kept in a separate method so the verifier never resolves it on
 * older devices. See issue #209 (crash on launch on Android 10–13).
 */
object EnterpriseContacts {
    const val MIN_SDK = 34

    fun isSupported(sdkInt: Int): Boolean = sdkInt >= MIN_SDK

    /** Only call when [isSupported] is true (API 34+). */
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun phoneUri(): Uri = ContactsContract.CommonDataKinds.Phone.ENTERPRISE_CONTENT_URI
}
