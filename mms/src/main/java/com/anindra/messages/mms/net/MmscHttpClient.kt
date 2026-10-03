package com.anindra.messages.mms.net

import android.net.Network
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import javax.net.ssl.SSLException
import java.util.concurrent.TimeUnit

/** A carrier HTTP proxy. Absence of one is the normal case and means "direct". */
data class ProxySpec(val host: String, val port: Int)

/**
 * A transport-agnostic HTTP request.
 *
 * The engine seam deliberately carries no OkHttp types: [MmscHttpClient] is where
 * the MMS-specific rules live (macros, headers, status classification), and those
 * rules are worth testing without standing up a server.
 */
data class MmscHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val proxy: ProxySpec?,
)

data class MmscHttpReply(val statusCode: Int, val body: ByteArray)

/** Performs one request. Implementations surface transport faults as exceptions. */
interface HttpEngine {
    fun execute(request: MmscHttpRequest): MmscHttpReply
}

/**
 * Builds an engine bound to a particular network.
 *
 * The argument is an [MmsNetworkLease.handle], which is an `android.net.Network`
 * in production. Null means no network was bound, which the caller has already
 * refused by then.
 */
fun interface HttpEngineFactory {
    fun create(handle: Any?): HttpEngine
}

enum class MmscFailure {
    NO_NETWORK,
    INVALID_APN,
    HTTP_FAILURE,
    TIMEOUT,
    TLS,
    IO,
    UNSPECIFIED,
}

sealed interface MmscResponse {
    data class Success(val statusCode: Int, val bytes: ByteArray) : MmscResponse

    /** [statusCode] is null when the request never produced an HTTP response. */
    data class Failure(val kind: MmscFailure, val statusCode: Int?) : MmscResponse
}

/**
 * Speaks HTTP to an MMSC.
 *
 * Neither the request body nor the MSISDN is logged anywhere in this class: a
 * composed MMS runs to megabytes and the MSISDN is PII, and a failure report
 * that carries either is a leak. Only the URL's host and the status are ever
 * worth showing.
 */
class MmscHttpClient(
    private val engine: HttpEngine,
    private val userAgent: () -> String?,
    private val uaProfileUrl: () -> String?,
    private val language: () -> String = { Locale.getDefault().toLanguageTag() },
) {
    fun post(pduBytes: ByteArray, profile: ApnProfile, line1: String?): MmscResponse {
        val endpoint = profile.endpoint()
            ?: return MmscResponse.Failure(MmscFailure.INVALID_APN, null)
        return exchange(
            url = endpoint.url,
            method = METHOD_POST,
            headers = headers(line1),
            body = pduBytes,
            proxy = (endpoint as? MmscEndpoint.Proxied)?.proxy,
        )
    }

    fun retrieve(contentLocation: String, profile: ApnProfile): MmscResponse {
        val endpoint = profile.endpoint()
            ?: return MmscResponse.Failure(MmscFailure.INVALID_APN, null)
        val url = resolveLocation(endpoint.url, contentLocation)
            ?: return MmscResponse.Failure(MmscFailure.INVALID_APN, null)
        return exchange(
            url = url,
            method = METHOD_GET,
            headers = headers(line1 = null),
            body = null,
            proxy = (endpoint as? MmscEndpoint.Proxied)?.proxy,
        )
    }

    private fun exchange(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        proxy: ProxySpec?,
    ): MmscResponse {
        val request = MmscHttpRequest(url, method, headers, body, proxy)
        return try {
            val reply = engine.execute(request)
            if (isSuccess(reply.statusCode)) {
                MmscResponse.Success(reply.statusCode, reply.body)
            } else {
                MmscResponse.Failure(MmscFailure.HTTP_FAILURE, reply.statusCode)
            }
        } catch (e: SocketTimeoutException) {
            MmscResponse.Failure(MmscFailure.TIMEOUT, null)
        } catch (e: SSLException) {
            MmscResponse.Failure(MmscFailure.TLS, null)
        } catch (e: IOException) {
            MmscResponse.Failure(MmscFailure.IO, null)
        }
    }

    private fun headers(line1: String?): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers[HEADER_ACCEPT] = MMS_MEDIA_TYPE
        headers[HEADER_ACCEPT_LANGUAGE] = language()
        userAgent()?.trim()?.takeIf { it.isNotEmpty() }?.let { headers[HEADER_USER_AGENT] = it }
        uaProfileUrl()?.let { template ->
            expandProfileMacros(template, line1)?.let { headers[HEADER_WAP_PROFILE] = it }
        }
        return headers
    }

    companion object {
        const val MMS_MEDIA_TYPE = "application/vnd.wap.mms-message"

        const val METHOD_POST = "POST"
        const val METHOD_GET = "GET"

        const val HEADER_ACCEPT = "Accept"
        const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_WAP_PROFILE = "x-wap-profile"

        const val HTTP_NOT_FOUND = 404

        private const val LINE1_MACRO = "##LINE1##"

        private val MACRO = Regex("##[A-Za-z0-9_]*##")

        /**
         * Success rule: any 2xx.
         *
         * The reference stack has two disagreeing rules — one accepts any 2xx, the
         * other demands exactly 200 — so an MMSC that answers 202 to a POST is
         * reported as delivered by one class and as failed by the other. Every
         * 2xx means the MMSC understood the transaction; only the status code
         * that means "gone" (404) needs special handling further up.
         */
        fun isSuccess(statusCode: Int): Boolean = statusCode in 200..299

        /**
         * Substitutes the WAP profile macros.
         *
         * `##LINE1##` comes from the caller, which owns the MSISDN lookup.
         * `##NAI##` has no public accessor — `TelephonyManager.getNai()` is
         * `@hide` and reflection onto hidden APIs is not an option here — so the
         * macro is dropped rather than substituted. Any macro left over after
         * both substitutions are removed as well: an MMSC is entitled to reject
         * a request carrying a literal `##...##`, and it is not worth a delivery
         * failure to send one.
         */
        fun expandProfileMacros(template: String, line1: String?): String? {
            val withLine1 = if (line1.isNullOrEmpty()) template else template.replace(LINE1_MACRO, line1)
            val expanded = MACRO.replace(withLine1, "").trim()
            return expanded.ifEmpty { null }
        }

        /**
         * Resolves a notification's content-location against the MMSC.
         *
         * M-MMS notifications carry both relative (`../mmsc/inbox`) and absolute
         * content-locations; the relative form is relative to the MMSC, not to
         * the current directory.
         */
        fun resolveLocation(mmscUrl: String, contentLocation: String): String? {
            val location = contentLocation.trim()
            if (location.isEmpty()) return null
            val resolved = runCatching { URI(mmscUrl).resolve(location).toString() }.getOrNull() ?: return null
            return normalizeMmscUrl(resolved)
        }
    }
}

/**
 * OkHttp 4 implementation of [HttpEngine].
 *
 * OkHttp's proxy and socket factory are client-level, not call-level, so clients
 * are memoised per proxy. The socket factory is only set when a network was
 * bound: without one the request rides the process default, which is what a
 * proxyless request wants anyway.
 */
class OkHttpEngine(
    private val network: Network?,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MS,
) : HttpEngine {
    private val clients = HashMap<String, OkHttpClient>()

    override fun execute(request: MmscHttpRequest): MmscHttpReply {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val body = request.body?.toRequestBody(MMS_BODY_TYPE)
            ?: EMPTY_BODY
        builder.method(request.method, if (request.method == MmscHttpClient.METHOD_GET) null else body)
        clientFor(request.proxy).newCall(builder.build()).execute().use { response ->
            return MmscHttpReply(response.code, response.body?.bytes() ?: ByteArray(0))
        }
    }

    private fun clientFor(proxy: ProxySpec?): OkHttpClient = synchronized(clients) {
        clients.getOrPut(proxy?.let { "${it.host}:${it.port}" } ?: DIRECT) { buildClient(proxy) }
    }

    internal fun buildClient(proxy: ProxySpec?): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .apply {
                // Only build a Proxy when the carrier actually gave one. A
                // proxyless MMSC is reached directly, and building an unconditional
                // ProxySelector is what makes the reference stack fail here.
                proxy?.let {
                    proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)))
                }
                network?.let { socketFactory(it.socketFactory) }
            }
            .build()

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000L
        const val DEFAULT_READ_TIMEOUT_MS = 60_000L

        private const val DIRECT = ""
        private val MMS_BODY_TYPE = MmscHttpClient.MMS_MEDIA_TYPE.toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}

/** Production [HttpEngineFactory]: one engine per bound network. */
class OkHttpEngineFactory(
    private val connectTimeoutMillis: Long = OkHttpEngine.DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Long = OkHttpEngine.DEFAULT_READ_TIMEOUT_MS,
) : HttpEngineFactory {
    override fun create(handle: Any?): HttpEngine =
        OkHttpEngine(handle as? Network, connectTimeoutMillis, readTimeoutMillis)
}

/** Device identity the MMSC sees, beyond what the carrier config already says. */
object MmsIdentity {
    /**
     * The platform's MMS user agent.
     *
     * This is the only *public* route to the value: the carrier-config constant
     * behind it is `@hide`. It reads the same `mmsUserAgent` carrier-config entry
     * [CarrierProfile.userAgent] exposes, and is used only where no cached profile
     * is available, so the two cannot disagree.
     */
    fun userAgent(context: android.content.Context): String? =
        context.applicationContext
            .getSystemService(android.telephony.TelephonyManager::class.java)
            ?.mmsUserAgent
}