package com.anindra.messages.ui

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CLDR skeletons, not literal patterns.
 *
 * A hardcoded "MMM d" renders as "Mar 3" in English but "Mar 3" in French too,
 * where it should read "3 mars" — the field order itself is locale data. Each
 * skeleton below is handed to the platform, which resolves it against the
 * active locale's CLDR data.
 */
enum class DateStyle(val skeleton: String) {
    /** Day and month: "Mar 3" / "3 mars". */
    Day("MMMd"),

    /** Day, month, abbreviated weekday: "Tue, Mar 3" / "mar. 3 mars". */
    DayWithAbbrevWeekday("MMMEd"),

    /** Day, month, full weekday: "Tuesday, Aug 2" / "mardi 2 août". */
    DayWithFullWeekday("MMMMEEEEd"),

    /** As [DayWithFullWeekday], plus year: "Tuesday, Aug 2, 2024". */
    DayWithFullWeekdayAndYear("yMMMMEEEEd"),
}

/** Resolves a skeleton to a locale-specific pattern. Overridable so the caching
 *  and locale-plumbing logic can be exercised off-device. */
internal fun interface SkeletonResolver {
    fun resolve(locale: Locale, skeleton: String): String
}

private val androidResolver = SkeletonResolver { locale, skeleton ->
    android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
}

private val cache = HashMap<String, DateTimeFormatter>()

private fun cacheKey(locale: Locale, skeleton: String) = "${locale.toLanguageTag()}|$skeleton"

/**
 * Formatters are cached per locale, not built once for the process. The previous
 * top-level `val`s captured Locale.getDefault() during class initialisation, so
 * after a per-app language change they kept formatting in the old language.
 */
internal fun dateFormatter(
    style: DateStyle,
    locale: Locale = Locale.getDefault(),
    resolver: SkeletonResolver = androidResolver
): DateTimeFormatter {
    val key = cacheKey(locale, style.skeleton)
    cache[key]?.let { return it }
    return DateTimeFormatter.ofPattern(resolver.resolve(locale, style.skeleton), locale)
        .also { cache[key] = it }
}

internal fun clearDateFormatterCache() = cache.clear()