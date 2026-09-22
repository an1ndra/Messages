package com.anindra.messages.ui

/** Settings-row subtitle: "None" when the list is empty, else the count. */
fun blockedNumbersSubtitle(none: String, countFormat: String, count: Int): String =
    if (count == 0) none else String.format(countFormat, count)
