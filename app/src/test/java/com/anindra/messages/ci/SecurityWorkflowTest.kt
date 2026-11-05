package com.anindra.messages.ci

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `github/codeql-action` step in one workflow must run the same
 * version: `analyze` reads the configuration `init` wrote and refuses to run
 * when the two disagree.
 *
 * Dependabot bumped `analyze` and `upload-sarif` to v4.39.1 but left `init` on
 * v4.38.2, so every CodeQL job died at the analyze post-step with
 *
 *   Loaded a configuration file for version '4.38.2', but running version
 *   '4.39.1'
 *
 * That took out the scheduled scans from 2026-10-19 and the v1.0.31 release
 * run. `dependabot.yml` now groups the action so a split bump cannot recur;
 * these tests hold the invariant itself.
 */
class SecurityWorkflowTest {

    private val workflowsDir = File(LocaleCatalog.repoRoot(), ".github/workflows")

    // The 40-hex commit is what actually runs; the `# vX.Y.Z` comment is not
    // machine-checkable.
    private val codeqlStep =
        Regex("""uses:\s*github/codeql-action/([A-Za-z-]+)@([0-9a-f]{40})""")

    private fun pins(file: File): List<Triple<String, String, Int>> =
        file.readLines().mapIndexedNotNull { index, line ->
            codeqlStep.find(line)?.let {
                Triple(it.groupValues[1], it.groupValues[2], index + 1)
            }
        }

    private fun workflowFiles(): List<File> =
        workflowsDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".yml") || file.name.endsWith(".yaml"))
        }?.sortedBy { it.name }.orEmpty()

    @Test
    fun everyCodeqlActionStepInAWorkflowUsesTheSameCommit() {
        val files = workflowFiles()
        assertTrue("no workflow files under $workflowsDir", files.isNotEmpty())

        val mismatches = files.mapNotNull { file ->
            val steps = pins(file)
            if (steps.isEmpty()) return@mapNotNull null
            if (steps.map { it.second }.distinct().size == 1) return@mapNotNull null
            "${file.name}: " + steps.joinToString(", ") {
                "${it.first}@${it.second.take(7)} (line ${it.third})"
            }
        }

        assertEquals(
            "CodeQL action versions must match within a workflow",
            emptyList<String>(),
            mismatches
        )
    }

    /** Guard against the check passing because the steps were renamed away. */
    @Test
    fun securityWorkflowStillRunsInitAndAnalyze() {
        val actions = pins(File(workflowsDir, "security.yml")).map { it.first }
        assertTrue("security.yml must run codeql-action/init, found $actions", actions.contains("init"))
        assertTrue("security.yml must run codeql-action/analyze, found $actions", actions.contains("analyze"))
    }

    @Test
    fun dependabotGroupsTheCodeqlAction() {
        val dependabot = File(LocaleCatalog.repoRoot(), ".github/dependabot.yml").readText()
        assertTrue(
            "dependabot must group github/codeql-action so init and analyze bump together",
            dependabot.contains("\"github/codeql-action*\"")
        )
    }
}
