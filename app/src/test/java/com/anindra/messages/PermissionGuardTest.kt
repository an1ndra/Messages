package com.anindra.messages

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the permission-sensitive platform reads that lint's `MissingPermission`
 * check flags.
 *
 * The original code wrapped these in `runCatching { }` and `catch (_: Exception)`.
 * Both are correct at runtime — `SecurityException` is a subclass of `Exception` —
 * but they leave the *reason* for the failure anonymous, and lint credits neither,
 * so a denied `READ_PHONE_STATE` looked like a required-permission bug rather than
 * a handled degradation. Each call now sits in an explicit
 * `catch (_: SecurityException)` that returns a documented default.
 *
 * The check is source-level on purpose: the permission denial itself cannot be
 * reproduced in a JVM unit test (there is no package manager to revoke against).
 * `scripts/test-permission-denied-degrades.sh` covers the runtime half by revoking
 * the permission on a live emulator.
 */
class PermissionGuardTest {
    /**
     * Each entry is a file and the permission-gated calls in it, paired with the
     * default the call site degrades to when the user has refused the permission.
     */
    private val gated = listOf(
        Gated(
            "com/anindra/messages/sms/MmsCarrierConfig.kt",
            "manager.getConfigForSubId(subId)",
            default = "null PersistableBundle, so MmsConfig falls back to the AOSP MMS limits",
        ),
        Gated(
            "com/anindra/messages/sms/SimMmsProbe.kt",
            "manager.getConfigForSubId(subscriptionId)",
            default = "null config, so SimMmsCheck reports the SIM as unknown",
        ),
        Gated(
            "com/anindra/messages/data/SimCard.kt",
            "?.activeSubscriptionInfoList",
            default = "an empty SIM list rather than a crash",
        ),
        Gated(
            "com/anindra/messages/data/SimCard.kt",
            "info.number",
            default = "a null SimCard.number; READ_PHONE_NUMBERS is never requested",
        ),
        Gated(
            "com/anindra/messages/data/PhoneNumberUtils.kt",
            "sm?.activeSubscriptionInfoList?.firstOrNull()",
            default = "the locale's country, as a last-resort region",
        ),
    )

    private data class Gated(val path: String, val call: String, val default: String)

    @Test
    fun everyGatedCallIsGuardedByAnExplicitSecurityExceptionCatch() {
        for (g in gated) {
            val lines = source(g.path).lines()
            val at = lines.indexOfFirst { it.contains(g.call) }
            assertTrue("${g.path} no longer contains `${g.call}`", at >= 0)
            val catchAt = (at until minOf(at + 15, lines.size))
                .firstOrNull { lines[it].contains("catch") }
            assertTrue(
                "`${g.call}` in ${g.path} has no enclosing catch within 15 lines; " +
                    "it must degrade to ${g.default}",
                catchAt != null,
            )
            val catchLine = lines[catchAt!!].trim()
            assertTrue(
                "`${g.call}` in ${g.path} is followed by `$catchLine` but must be " +
                    "guarded by `catch (_: SecurityException)` — a broad Exception catch " +
                    "does not tell lint (or a reader) the failure was a permission denial",
                catchLine.contains("SecurityException"),
            )
        }
    }

    @Test
    fun gatedCallsDoNotUseRunCatching() {
        // runCatching swallows every Throwable without recording that the cause was
        // a permission denial, and lint does not treat it as handling the exception.
        // This is the exact form the MissingPermission errors were reported against.
        for (g in gated) {
            val lines = source(g.path).lines()
            val at = lines.indexOfFirst { it.contains(g.call) }
            val window = lines.subList(at, minOf(at + 3, lines.size)).joinToString("\n")
            assertFalse(
                "`${g.call}` in ${g.path} is wrapped in runCatching, which masks a " +
                    "permission denial: $window",
                window.contains("runCatching"),
            )
        }
    }

    @Test
    fun noFileGuardingAPermissionGatedCallUsesRunCatchingAtAll() {
        for (path in gated.map { it.path }.distinct()) {
            val body = source(path)
            assertFalse(
                "$path regressed to runCatching around a permission-gated read",
                Regex("""runCatching\s*\{[^}]*getConfigForSubId|runCatching\s*\{\s*it\.number""")
                    .containsMatchIn(body),
            )
        }
    }

    /** [relativePath] is relative to the `src/main/java` source root; the test's working
     *  directory is the module dir, so walk up looking for either prefix. */
    private fun source(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (candidate in listOf(
                File(dir, "app/src/main/java/$relativePath"),
                File(dir, "src/main/java/$relativePath"),
            )) {
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relativePath from ${System.getProperty("user.dir")}")
    }
}
