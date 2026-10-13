package com.anindra.messages.ci

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * copilot-triage validates `reasoning-effort` against none/low/default and
 * otherwise fails the whole run with
 * "invalid assessment (ArgumentError in reasoning_flag at assessment.rb:834);
 * left for a maintainer" (#325).
 *
 * The workflow forwarded `vars.TRIAGE_REASONING_EFFORT` bare. An unset
 * repository variable renders as an empty string, which is a *present* input
 * rather than a missing one, so it overrode the action's `low` default and
 * tripped that guard on every comment. Each input now carries its own fallback.
 */
class TriageWorkflowDefaultsTest {

    private val triage: String by lazy {
        File(LocaleCatalog.repoRoot(), ".github/workflows/triage.yml").readText()
    }

    @Test
    fun reasoningEffortHasAValidFallback() {
        assertTrue(
            "reasoning-effort must fall back to a value the action accepts",
            triage.contains("reasoning-effort: \${{ vars.TRIAGE_REASONING_EFFORT || 'low' }}"),
        )
    }

    @Test
    fun theModelAndDryRunInputsAlsoDefault() {
        assertTrue(
            "model must fall back to the action's default",
            triage.contains("model: \${{ vars.TRIAGE_MODEL || 'gpt-5.6-luna' }}"),
        )
        assertTrue(
            "dry-run must fall back to false",
            triage.contains("dry-run: \${{ vars.TRIAGE_DRY_RUN || false }}"),
        )
    }

    @Test
    fun noInputForwardsABareEmptyVariable() {
        val bare = Regex("""^\s*(model|reasoning-effort|dry-run):\s*\$\{\{\s*vars\.\w+\s*\}\}\s*$""")
        val offenders = triage.lines().filter { bare.containsMatchIn(it) }
        assertTrue("these inputs forward a variable with no fallback: $offenders", offenders.isEmpty())
    }
}
