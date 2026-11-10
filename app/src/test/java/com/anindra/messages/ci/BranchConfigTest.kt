package com.anindra.messages.ci

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository's default branch is `Develop`; `main` only takes release
 * pull requests. Two files silently stop working when the default branch is
 * switched, so both are pinned here:
 *
 *  - the VirusTotal badge URL in `README.md` must name the branch the badge
 *    workflow commits to, which is `github.event.repository.default_branch`;
 *  - `pr-build.yml` must run for pull requests into `Develop` as well as
 *    `main`, otherwise feature work lands unchecked.
 */
class BranchConfigTest {

    private val root = LocaleCatalog.repoRoot()

    private val readme: String by lazy { File(root, "README.md").readText() }
    private val prBuild: String by lazy { File(root, ".github/workflows/pr-build.yml").readText() }
    private val virustotal: String by lazy { File(root, ".github/workflows/virustotal.yml").readText() }

    @Test
    fun virusTotalBadgeReadsTheBranchTheWorkflowWritesTo() {
        assertTrue(
            "virustotal.yml must publish the badge to the repository default branch",
            virustotal.contains("github.event.repository.default_branch"),
        )

        val url = Regex("""raw\.githubusercontent\.com/[\w.-]+/[\w.-]+/([^/]+)/\.github/virustotal\.json""")
            .find(readme)
            ?.groupValues
            ?.get(1)

        assertEquals("README badge must read the default branch", DEFAULT_BRANCH, url)
    }

    @Test
    fun prBuildRunsForBothLongLivedBranches() {
        val branches = Regex("""on:\s*\n\s*pull_request:\s*\n\s*branches:\s*\[([^\]]*)]""")
            .find(prBuild)
            ?.groupValues
            ?.get(1)
            ?.split(",")
            ?.map { it.trim() }
            .orEmpty()

        assertEquals(
            "pr-build.yml must run for pull requests into both long-lived branches",
            setOf("main", DEFAULT_BRANCH),
            branches.toSet(),
        )
    }

    private companion object {
        const val DEFAULT_BRANCH = "Develop"
    }
}
