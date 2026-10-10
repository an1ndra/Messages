package android.net

/**
 * The `android.net.Uri` the unit tests run against.
 *
 * The compile-time `android.jar` returns null from its methods, so a store that
 * takes a `Uri` cannot be exercised against it at all. Same class name, same
 * package, real behaviour, and the static members the store compiled against
 * marked `@JvmStatic` so they still resolve.
 *
 * `ContentUris` is deliberately not shadowed and is not used by the store, because
 * the jar's copy of it is stubbed out too.
 */
class Uri private constructor(private val value: String) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is Uri && other.value == value

    override fun hashCode(): Int = value.hashCode()

    private val withoutQuery: String get() = value.substringBefore('?')

    val path: String
        get() = withoutQuery.substringAfter("://", missingDelimiterValue = "")
            .substringAfter('/', missingDelimiterValue = "")

    val pathSegments: List<String>
        get() = path.split('/').filter { it.isNotEmpty() }

    val lastPathSegment: String? get() = pathSegments.lastOrNull()

    fun buildUpon(): Builder = Builder(value)

    class Builder(private val value: String) {
        private val appended = mutableListOf<String>()
        private val parameters = mutableListOf<Pair<String, String>>()

        fun appendEncodedPath(segment: String): Builder = apply {
            appended += segment.trim('/')
        }

        fun appendPath(segment: String): Builder = appendEncodedPath(encode(segment))

        fun appendQueryParameter(key: String, value: String): Builder = apply {
            parameters += key to encode(value)
        }

        fun build(): Uri {
            val base = value.substringBefore('?') + appended.joinToString("") { "/$it" }
            if (parameters.isEmpty()) return Uri(base)
            return Uri(base + "?" + parameters.joinToString("&") { "${it.first}=${it.second}" })
        }
    }

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun withAppendedPath(baseUri: Uri, pathSegment: String): Uri =
            baseUri.buildUpon().appendEncodedPath(pathSegment).build()

        @JvmStatic
        fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
