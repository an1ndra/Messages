package com.anindra.messages.ci

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `security.yml` uploads the SARIF that populates Security → Code scanning, and
 * CodeQL refuses to analyze a database built by a *different* version of the
 * CodeQL Action.
 *
 * Dependabot bumps each `uses:` path on its own, so it moved `analyze` and
 * `upload-sarif` from 4.38.2 to 4.38.3 while `init` stayed at 4.38.2. The next
 * scheduled scan failed at the analyze step and the repo's code-scanning page
 * showed "Code scanning configuration error: several tools are reporting
 * errors" — with no alert pointing at a workflow file.
 *
 * The workflow file is not a Gradle input, so after editing it re-run with
 * `--rerun-tasks` or Gradle hands back the previous, cached verdict.
 */
class SecurityWorkflowTest {

    private val workflow: String by lazy {
        File(LocaleCatalog.repoRoot(), ".github/workflows/security.yml").readText()
    }

    /** "github/codeql-action/init@<sha>" -> name to sha, one entry per pin. */
    private fun codeqlPins(): Map<String, String> =
        Regex("""github/codeql-action/([a-z-]+)@([0-9a-f]{40})""")
            .findAll(workflow)
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun everyCodeqlActionStepUsesOneVersion() {
        val pins = codeqlPins()
        assertTrue("security.yml no longer runs CodeQL at all", pins.containsKey("init"))
        assertTrue("security.yml no longer analyzes", pins.containsKey("analyze"))
        val init = pins.getValue("init")
        pins.forEach { (name, sha) ->
            assertEquals(
                "control-flow: $name is pinned to $sha but init is $init — " +
                    "CodeQL will fail to analyze a database from another version",
                init,
                sha
            )
        }
    }

    @Test
    fun theScanStillCoversKotlinWithQualityQueries() {
        assertTrue(
            "the init step no longer declares the Kotlin/Java language",
            workflow.contains("languages: java-kotlin")
        )
        assertTrue(
            "the security-and-quality query pack is gone",
            workflow.contains("queries: security-and-quality")
        )
        assertTrue(
            "no manual build mode, so the Gradle build would not be traced",
            workflow.contains("build-mode: manual")
        )
    }
}
