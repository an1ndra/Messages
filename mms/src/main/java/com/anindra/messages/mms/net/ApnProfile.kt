package com.anindra.messages.mms.net

/**
 * The MMS-relevant columns of one `content://telephony/carriers` row.
 *
 * Every field is nullable because the platform schema is: a row of type `*` on
 * some carriers has no `mmsproxy` at all, and a row with no `mmsc` is a
 * data-only APN that happens to be flagged MMS. Nothing here decides whether
 * the APN is usable — [endpoint] does, and returns null rather than an empty
 * MMSC string, so the caller can report `INVALID_APN` instead of handing an
 * empty URL to something that will throw `MalformedURLException` further down.
 */
data class ApnProfile(
    val apnName: String?,
    val mmscUrl: String?,
    val mmsProxy: String?,
    val mmsPort: Int?,
    val type: String?,
) {
    /** Where the MMSC actually is, or null when this APN cannot carry MMS. */
    fun endpoint(): MmscEndpoint? {
        val mmsc = mmscUrl?.trim().orEmpty()
        if (mmsc.isEmpty()) return null
        val normalized = normalizeMmscUrl(mmsc) ?: return null
        val proxy = proxySpec()
        return if (proxy == null) {
            MmscEndpoint.Direct(normalized)
        } else {
            MmscEndpoint.Proxied(normalized, proxy)
        }
    }

    /**
     * A carrier proxy only counts when there is somewhere to send the request.
     * Proxyless HTTPS is the normal case and must not be turned into a
     * `ProxySelector` that dereferences a null proxy.
     */
    fun proxySpec(): ProxySpec? {
        val host = mmsProxy?.trim().orEmpty()
        if (host.isEmpty()) return null
        val port = mmsPort?.takeIf { it > 0 } ?: DEFAULT_PROXY_PORT
        return ProxySpec(host, port)
    }
}

/** An MMSC URL plus the proxy it should be reached through, if any. */
sealed interface MmscEndpoint {
    val url: String

    data class Direct(override val url: String) : MmscEndpoint

    data class Proxied(override val url: String, val proxy: ProxySpec) : MmscEndpoint
}

internal const val DEFAULT_PROXY_PORT = 80

private val IPV4_HOST = Regex("""\d{1,3}(\.\d{1,3}){3}""")

/**
 * Returns the MMSC URL with leading zeros stripped from an IPv4 host, or null
 * if the carrier gave something that is not an HTTP(S) URL.
 *
 * Carriers ship MMSC hosts as `http://010.004.000.001/servlets/mms`, and some
 * resolvers read that as octal. Trimming per octet keeps the modem's own
 * interpretation of a numeric host intact.
 */
internal fun normalizeMmscUrl(raw: String): String? {
    val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    val normalizedHost = if (IPV4_HOST.matches(host)) {
        host.split('.').joinToString(".") { octet -> octet.trimStart('0').ifEmpty { "0" } }
    } else {
        host.lowercase()
    }
    val port = if (uri.port > 0) ":${uri.port}" else ""
    val path = uri.rawPath.orEmpty().ifEmpty { "/" }
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "$scheme://$normalizedHost$port$path$query"
}