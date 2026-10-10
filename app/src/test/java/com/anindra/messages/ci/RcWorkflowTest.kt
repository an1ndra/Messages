package com.anindra.messages.ci

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `rc.yml` publishes to the public Releases page, so a mistake in it is a
 * mistake everyone sees. Four of them bit in turn:
 *
 *  - it derived the release tag from `versionName`, which lags the tag. Against
 *    the shipped v1.0.27 it would have republished the live release as
 *    "RC 1.0.27" and marked it pre-release.
 *  - it published the APK before the VirusTotal verdict, so a flagged build was
 *    downloadable for the length of the scan.
 *  - it fell back to `assembleDebug` when the keystore secrets were absent. A
 *    debug APK cannot install over the release build a tester already has.
 *  - it carried none of `release.yml`'s guards: no signing-certificate pin, no
 *    unit tests.
 */
class RcWorkflowTest {

    private val rc: String by lazy {
        File(LocaleCatalog.repoRoot(), ".github/workflows/rc.yml").readText()
    }

    private val release: String by lazy {
        File(LocaleCatalog.repoRoot(), ".github/workflows/release.yml").readText()
    }

    private val security: String by lazy {
        File(LocaleCatalog.repoRoot(), ".github/workflows/security.yml").readText()
    }

    private fun stepIndex(name: String): Int {
        val index = rc.lines().indexOfFirst { it.trimStart().startsWith("- name: $name") }
        assertTrue("no step named '$name' in rc.yml", index >= 0)
        return index
    }

    /**
     * The tag is the release identity. Scraping it out of `versionName` is what
     * let an RC overwrite the release it was supposed to precede.
     */
    @Test
    fun releaseTagComesFromTheRcTagNotVersionName() {
        assertTrue(
            "the published tag must be the pushed RC tag",
            rc.contains("tag_name: \${{ steps.rc.outputs.tag }}")
        )
        assertTrue(
            "rc.yml must not derive the release tag from versionName again",
            !rc.contains("tag_name: v\${{")
        )
    }

    /** An untagged or malformed ref must not produce a release. */
    @Test
    fun onlyWellFormedRcTagsAreAccepted() {
        assertTrue(
            "the tag shape must be validated, not assumed",
            rc.contains("""grep -qE '^v[0-9]+(\.[0-9]+)+-rc[0-9]+$'""")
        )
        assertTrue("a bad tag must fail the run", Regex("""::error::.*is not an RC tag""").containsMatchIn(rc))
    }

    /** The specific case that broke: versionName still on the last shipped version. */
    @Test
    fun aTagBehindVersionNameIsRejected() {
        assertTrue(
            "the tag's base version must be checked against versionName",
            rc.contains("""[ "${'$'}BASE" != "${'$'}NAME" ]""")
        )
        assertTrue(
            "an already-published release must never be republished as an RC",
            rc.contains("releases/tags/v\$NAME") && rc.contains("is already published")
        )
    }

    /** The APK is public the moment the release exists; the scan must come first. */
    @Test
    fun theApkIsScannedBeforeItIsPublished() {
        val publish = stepIndex("Publish RC pre-release")
        val flag = stepIndex("Flag malware detections")
        val report = stepIndex("Post scan report to release")
        assertTrue(
            "malware check must run before the release is published",
            flag < publish
        )
        assertTrue(
            "the report is appended to a release, so it must follow publishing",
            publish < report
        )
    }

    /** A debug-signed RC installs over nothing and blocks the next real update. */
    @Test
    fun thereIsNoDebugFallback() {
        assertTrue(
            "rc.yml must not fall back to assembleDebug",
            !rc.contains("assembleDebug")
        )
        assertTrue(
            "a missing keystore must fail the run, not downgrade the build",
            rc.contains("::error::RELEASE_KEYSTORE_BASE64 secret is not set")
        )
    }

    /** Same digest `release.yml` pins: an RC is signed by the real key or it is not published. */
    @Test
    fun theSigningCertificateIsPinned() {
        val expected = Regex("""expected="([0-9a-f]{64})"""")
            .find(release)?.groupValues?.get(1)
        assertTrue("release.yml no longer pins a signing certificate", expected != null)
        assertTrue(
            "rc.yml must pin the same certificate as release.yml",
            rc.contains("""expected="$expected"""")
        )
        assertTrue(
            "the pin has to be enforced, not printed",
            rc.contains("""if [ "${'$'}got" != "${'$'}expected" ]""")
        )
    }

    /** The APK must actually be the version it claims to be. */
    @Test
    fun theApkVersionIsVerifiedAgainstTheTag() {
        assertTrue(
            "the built APK's versionCode must be read back with aapt",
            rc.contains("aapt") && rc.contains("versionCode is")
        )
        assertTrue("the built APK's versionName must be read back too", rc.contains("versionName is"))
    }

    @Test
    fun unitTestsGateTheRc() {
        assertTrue(
            "an RC is handed to testers; a red unit test must stop it",
            Regex("""assembleRelease testDebugUnitTest""").containsMatchIn(rc)
        )
    }

    /** Without `always()` the keystore outlives a failed build. */
    @Test
    fun theKeystoreIsRemovedEvenWhenTheBuildFails() {
        val remove = rc.lines().indexOfFirst { it.trimStart().startsWith("- name: Remove keystore") }
        assertTrue("rc.yml must delete the keystore", remove >= 0)
        val following = rc.lines().drop(remove + 1).take(2).joinToString("\n")
        assertTrue(
            "keystore removal must be `if: always()`",
            Regex("""if:\s*always\(\)""").containsMatchIn(following)
        )
    }

    /** Polling a fixed 12 times cost six minutes on every run. */
    @Test
    fun theReportPollLoopStopsOnceTheReportIsPresent() {
        val loop = rc.substringAfter("for i in \$(seq 1 12); do")
            .substringBefore("done\n")
        assertTrue(
            "the append loop must break once the report is present",
            Regex("""\bbreak\b""").containsMatchIn(loop)
        )
    }

    /** Two queued runs on one ref would clobber each other's release. */
    @Test
    fun aSecondRunSupersedesTheFirst() {
        assertTrue(
            "concurrency must cancel the in-flight run",
            rc.contains("cancel-in-progress: true")
        )
    }

    /**
     * An `v*-rc*` tag must not also trigger the final release. `rc.yml` matches
     * `v*-rc*`, so `release.yml` and `security.yml` have to exclude it too —
     * and they must keep excluding it, or tagging an RC ships it.
     */
    @Test
    fun rcTagsAreExcludedFromTheFinalReleaseAndSecurityScan() {
        for ((name, text) in listOf("release.yml" to release, "security.yml" to security)) {
            val on = text.substringBefore("\njobs:")
            assertTrue(
                "$name must exclude rc tags from its tag trigger",
                on.contains("!v*-rc*")
            )
        }
        assertTrue(
            "rc.yml must trigger on rc tags",
            rc.contains("""tags:
      - "v*-rc*"""")
        )
    }

    /** A failed scan must not leave a pre-release behind for someone to find. */
    @Test
    fun aFlaggedApkIsNotPublished() {
        val step = rc.substringAfter("- name: Flag malware detections")
            .substringBefore("- name: Publish RC pre-release")
        assertTrue(
            "the malware step must exit non-zero",
            Regex("""malicious != '0'""").containsMatchIn(step) && step.contains("exit 1")
        )
        assertTrue(
            "the publish step must not run when the scan flagged the build",
            !step.contains("continue-on-error")
        )
        assertEquals("the publish step is unconditional because the scan gates it", 1, countPublishSteps())
    }

    private fun countPublishSteps(): Int =
        rc.lines().count { it.trimStart().startsWith("- name: Publish RC pre-release") }
}