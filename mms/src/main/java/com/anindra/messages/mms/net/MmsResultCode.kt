package com.anindra.messages.mms.net

import android.telephony.SmsManager

/** The platform result code vocabulary an MMS outcome is reported in. */
enum class MmsResultCode(val code: Int) {
    OK(android.app.Activity.RESULT_OK),
    UNABLE_CONNECT_MMS(SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS),
    MMS_DISABLED_BY_CARRIER(SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER),
    IO_ERROR(SmsManager.MMS_ERROR_IO_ERROR),
    INVALID_SUBSCRIPTION_ID(SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID),
    RETRY(SmsManager.MMS_ERROR_RETRY),
    UNSPECIFIED(SmsManager.MMS_ERROR_UNSPECIFIED),
    HTTP_FAILURE(SmsManager.MMS_ERROR_HTTP_FAILURE),
    INACTIVE_SUBSCRIPTION(SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION),
}
