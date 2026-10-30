package com.anindra.messages.data

import java.util.Locale

/**
 * The calendar the app renders dates in (#363).
 *
 * [AUTO] is not a calendar: it defers to [forLanguage], so a Persian phone gets
 * Jalali and an Arabic one Hijri without anyone choosing. [resolve] is the one
 * place that turns the stored choice into the calendar that actually renders —
 * the settings subtitle and Diagnostics call it rather than re-deriving, so the
 * two can never disagree with what is on screen.
 *
 * [icuKeyword] is the CLDR calendar keyword handed to android.icu. It is null
 * for Gregorian on purpose: that path stays on the platform java.time
 * formatter, which is the only one testable off-device.
 *
 * [keepsEra] is true only for the Japanese calendar, and only because CLDR
 * writes its years as an era year: dropping "Reiwa" from "Oct 26, 8 Reiwa"
 * leaves a bare "8" that reads like a mistake. Every other calendar's era is a
 * redundant suffix ("1405 AP", "1448 AH"), so it is stripped.
 */
enum class CalendarSystem(
    val storageValue: String,
    val icuKeyword: String?,
    val keepsEra: Boolean = false
) {
    AUTO("auto", null),
    GREGORIAN("gregorian", null),
    PERSIAN("persian", "persian"),
    HIJRI_UMALQURA("hijri_umalqura", "islamic-umalqura"),
    HEBREW("hebrew", "hebrew"),
    BUDDHIST("buddhist", "buddhist"),
    JAPANESE("japanese", "japanese", keepsEra = true),
    INDIAN("indian", "indian"),
    COPTIC("coptic", "coptic");

    companion object {
        /** Unknown stored values fall back to [AUTO] rather than throwing. */
        fun fromStorage(value: String?): CalendarSystem =
            entries.firstOrNull { it.storageValue == value } ?: AUTO

        /** The local calendar a language expects to read its dates in. */
        fun forLanguage(locale: Locale): CalendarSystem = when (locale.language) {
            "fa" -> PERSIAN
            "ar" -> HIJRI_UMALQURA
            else -> GREGORIAN
        }

        /** The calendar that will actually render, never [AUTO]. */
        fun resolve(stored: String?, locale: Locale): CalendarSystem {
            val chosen = fromStorage(stored)
            return if (chosen == AUTO) forLanguage(locale) else chosen
        }
    }
}
