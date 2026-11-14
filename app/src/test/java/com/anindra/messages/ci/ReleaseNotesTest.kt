package com.anindra.messages.ci

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GitHub release body is built from the store changelog, not from GitHub's
 * listing of merged pull requests.
 *
 * v1.0.31 published that list: a wall of Crowdin and dependabot entries that
 * says nothing about what a user gets. `release.yml` now seeds the body from
 * `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — the same
 * file F-Droid shows — keeps the toolchain block under it, and adds the compare
 * link itself. `generate_release_notes` must stay false, or GitHub appends its
 * own second changelog underneath.
 *
 * Pinned at the source level because the workflow only runs on a tag push.
 */
class ReleaseNotesTest {

    private val root: File = LocaleCatalog.repoRoot()

    private val workflow: String by lazy {
        File(root, ".github/workflows/release.yml").readText()
    }

    private val versionCode: String by lazy {
        Regex("""versionCode = (\d+)""")
            .find(File(root, "app/build.gradle.kts").readText())
            ?.groupValues?.get(1)
            ?: error("no versionCode in app/build.gradle.kts")
    }

    private val changelog: File
        get() = File(
            root,
            "fastlane/metadata/android/en-US/changelogs/$versionCode.txt"
        )

    @Test
    fun theBodyIsSeededFromTheStoreChangelog() {
        assertTrue(
            "release.yml must read the fastlane changelog",
            workflow.contains("fastlane/metadata/android/en-US/changelogs/"),
        )
        assertTrue(
            "the changelog must be selected by versionCode, or the release " +
                "publishes the previous version's note",
            workflow.contains("changelogs/\${CODE}.txt"),
        )
    }

    @Test
    fun githubsOwnChangelogIsNotAppended() {
        assertTrue(
            "generate_release_notes must be false, or GitHub appends its PR list " +
                "under the store note",
            Regex("""generate_release_notes:\s*false""").containsMatchIn(workflow),
        )
        assertTrue(
            "generate_release_notes: true must not come back",
            !Regex("""generate_release_notes:\s*true""").containsMatchIn(workflow),
        )
    }

    @Test
    fun theNoteIsComposedBeforeTheReleaseIsPublished() {
        val seeded = workflow.indexOf("Append the store changelog")
        val published = workflow.indexOf("Publish GitHub Release")
        assertTrue("release.yml must have the changelog step", seeded > 0)
        assertTrue("release.yml must publish the release", published > 0)
        assertTrue(
            "the changelog step must run in the build job, before publish",
            seeded < published,
        )
        assertTrue(
            "the body must still name the toolchain and the APK digest",
            workflow.contains("Toolchain used for this build") &&
                workflow.contains("APK SHA-256"),
        )
    }

    @Test
    fun theVersionBeingReleasedHasAChangelogThatFits() {
        assertTrue(
            "missing ${changelog.path}: F-Droid and the release body would both " +
                "have no note for versionCode $versionCode",
            changelog.isFile,
        )
        val note = changelog.readText()
        assertTrue("the store note must not be empty", note.isNotBlank())
        assertTrue(
            "the store note is ${note.length} characters; the limit is 500",
            note.length <= 500,
        )
    }
}
