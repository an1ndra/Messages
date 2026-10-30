package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The About screen's three outgoing actions all end in a URL, and a wrong one
 * is invisible in review: the screen still opens, the browser still launches,
 * and the wrong page appears.
 *
 * These cover the building blocks only — the row wiring and the placement are
 * checked by [AboutRowPlacementTest] and by `scripts/test-about.sh` on a device.
 */
class AboutLinksTest {

    @Test
    fun theRepositoryAndItsIssueFormAreTheDestinations() {
        assertEquals("https://github.com/an1ndra/Messages", AboutLinks.REPO_URL)
        assertEquals(
            "https://github.com/an1ndra/Messages/issues/new",
            AboutLinks.issuesUrl
        )
    }

    @Test
    fun theIssueTitleNamesTheBuildThatReported() {
        val title = AboutLinks.issueTitle("1.0.31")
        assertTrue("title should name the version: $title", title.contains("1.0.31"))
        assertTrue("title should say it came from Messages: $title", title.startsWith("Messages "))
    }

    @Test
    fun aBlankBodyLeavesTheFormEmpty() {
        assertEquals(
            "https://github.com/an1ndra/Messages/issues/new?title=Messages%201.0.31",
            AboutLinks.issueUrl("Messages 1.0.31", "")
        )
    }

    @Test
    fun thePrefillIsPercentEncodedWithoutPlusForSpaces() {
        val url = AboutLinks.issueUrl("a b", "line one\nline two")
        assertTrue("spaces must be %20: $url", url.contains("title=a%20b"))
        assertTrue("newlines must be %0A: $url", url.contains("line%20one%0Aline%20two"))
        assertTrue("a '+' would arrive as a literal plus", !url.contains("+"))
    }

    @Test
    fun aReportThatAlreadyFitsIsAttachedWhole() {
        val report = "Messages diagnostics report\n--- App ---\nVersion: 1.0.31"
        assertEquals(report, diagnosticsForIssue(report, limit = 4096))
        assertEquals("trailing blank lines are dropped", "a", diagnosticsForIssue("a\n\n\n", 4096))
    }

    @Test
    fun aReportOverTheLimitIsCutToWholeLinesAndSaysSo() {
        val report = (1..200).joinToString("\n") { "line $it" }
        val trimmed = diagnosticsForIssue(report, limit = 120)
        assertTrue("the prefill must fit the limit: ${trimmed.length}", trimmed.length <= 120)
        assertTrue("the reader must be told it was cut: $trimmed", trimmed.endsWith("(truncated)"))
        // Every kept line is a complete one; no half line glued to the marker.
        val keptLines = trimmed.removeSuffix("\n(truncated)").lines()
        assertEquals("the first line is kept", "line 1", keptLines.first())
        assertTrue("no line is cut in half", keptLines.all { it.matches(Regex("line \\d+")) })
    }

    @Test
    fun truncationKeepsTheSectionsThatAnswerReports() {
        val report = buildString {
            appendLine("Messages diagnostics report")
            appendLine("--- App ---")
            appendLine("Version: 1.0.31 (31)")
            repeat(500) { appendLine("--- filler $it ---") }
        }
        val trimmed = diagnosticsForIssue(report, limit = 4096)
        assertTrue("the app section survives: $trimmed", trimmed.contains("--- App ---"))
        assertTrue(trimmed.length <= 4096)
    }

    @Test
    fun theLimitMatchesWhatGithubAccepts() {
        // GitHub drops a prefill body over 4,096 characters without telling
        // anyone, which would leave the reporter with an empty form.
        assertEquals(4096, AboutLinks.ISSUE_BODY_LIMIT)
    }

    @Test
    fun sharingPointsAtWhereTheAppCanBeDownloaded() {
        assertEquals(
            "https://github.com/an1ndra/Messages/releases/latest",
            AboutLinks.RELEASE_URL
        )
        assertEquals(
            "https://f-droid.org/packages/com.anindra.messages/",
            AboutLinks.FDROID_URL
        )
    }

    @Test
    fun theShareTemplateTakesBothDownloadLinks() {
        val res = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/res/values/strings_settings.xml") }
            .first { it.isFile }
            .readText()
        val entry = Regex("""<string name="about_share_text">(.*?)</string>""")
            .find(res)?.groupValues?.get(1)
        require(entry != null) { "about_share_text is missing" }
        assertTrue("the release link needs a slot: $entry", entry.contains("%1\$s"))
        assertTrue("the F-Droid link needs its own slot: $entry", entry.contains("%2\$s"))
    }

    @Test
    fun everyLicenceEntryIsNamed() {
        assertTrue("licences are listed", AboutLinks.LICENCES.isNotEmpty())
        for ((licence, libraries) in AboutLinks.LICENCES) {
            assertTrue("every group names its licence", licence.isNotBlank())
            assertTrue("$licence should list its libraries", libraries.isNotEmpty())
            libraries.forEach { assertTrue("library name", it.isNotBlank()) }
        }
    }

    @Test
    fun aLicenceIsNamedOnceNoMatterHowManyLibrariesUseIt() {
        // The dialog draws one heading per licence; repeating the same licence
        // under every library is what made the old dialog a wall of identical
        // "Apache License 2.0" lines.
        val apache = AboutLinks.LICENCES["Apache License 2.0"]
        requireNotNull(apache) { "the Apache group is missing" }
        assertTrue("all four libraries sit under it", apache.size >= 4)
        assertEquals(
            "each licence appears exactly once",
            AboutLinks.LICENCES.keys.toList().distinct(),
            AboutLinks.LICENCES.keys.toList()
        )
    }
}
