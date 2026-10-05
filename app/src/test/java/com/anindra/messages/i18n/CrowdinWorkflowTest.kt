package com.anindra.messages.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Crowdin sync is a two-way street: sources go up, translations come back.
 * Only those two directions are safe. `upload_translations` is the third
 * direction and it is destructive -- it pushes the repo's locale files over
 * whatever a translator has done in Crowdin but not yet merged, silently
 * reverting their work on the next scheduled run.
 */
class CrowdinWorkflowTest {

    private val workflow: String by lazy {
        File(LocaleCatalog.repoRoot(), ".github/workflows/crowdin.yml").readText()
    }

    @Test
    fun workflowDoesNotUploadTranslationsBackIntoCrowdin() {
        assertTrue(
            "upload_translations would overwrite translator work with the repo's " +
                "locale files on every run; it must stay off",
            !Regex("""upload_translations:\s*true""").containsMatchIn(workflow)
        )
    }

    @Test
    fun workflowStillExportsTranslationsAndSources() {
        assertTrue("sources must be pushed to Crowdin", Regex("""upload_sources:\s*true""").containsMatchIn(workflow))
        assertTrue("translations must come back from Crowdin", Regex("""download_translations:\s*true""").containsMatchIn(workflow))
    }

    /**
     * Without this, a language missing a few strings would have Crowdin write
     * the English source over good translations -- the failure behind PR #287.
     */
    @Test
    fun workflowSkipsUnfinishedTranslationFiles() {
        assertTrue(
            "skip_untranslated_files stops English overwriting partial translations",
            Regex("""skip_untranslated_files:\s*true""").containsMatchIn(workflow)
        )
    }

    /** Translations land as a PR so CI can vet them, never straight onto main. */
    @Test
    fun translationsArriveThroughAPullRequest() {
        assertTrue("translations must open a PR", Regex("""create_pull_request:\s*true""").containsMatchIn(workflow))
        val branch = Regex("""localization_branch_name:\s*(\S+)""").find(workflow)?.groupValues?.get(1)
        assertEquals("translations must not be committed to the source branch", "l10n_crowdin", branch)
    }

    /** An unsigned or unknown key yields Unverified commits, which we reject. */
    @Test
    fun translationsAreGpgSigned() {
        assertTrue(
            "commits must be signed or GitHub marks them Unverified",
            workflow.contains("CROWDIN_GPG_PRIVATE_KEY") && Regex("""gpg_private_key:""").containsMatchIn(workflow)
        )
    }

    /** Identity comes from secrets: this repo is public, so a literal address leaks. */
    @Test
    fun commitIdentityComesFromSecrets() {
        for (key in listOf("GIT_USER_NAME", "GIT_USER_EMAIL")) {
            assertTrue("$key must be read from secrets", workflow.contains("secrets.$key"))
        }
        assertTrue(
            "no literal email address may be committed to the workflow",
            !Regex("""[\w.+-]+@[\w-]+\.[\w.]+""").containsMatchIn(strippedOfComments(workflow))
        )
    }

    /**
     * Comments are stripped first: the workflow explains itself by naming
     * support+bot@crowdin.com, which is Crowdin's own public bot address and not
     * a leaked identity. Only values that GitHub would actually use matter.
     */
    private fun strippedOfComments(yaml: String): String =
        yaml.lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .map { it.substringBefore(" #") }
            .joinToString("\n")

    /** The config the workflow runs must be the one in the repo, not a stale copy. */
    @Test
    fun workflowPointsAtTheRepoConfig() {
        assertTrue(
            "the action must read crowdin.yml from the repo",
            Regex("""config:\s*crowdin\.yml""").containsMatchIn(workflow)
        )
    }
}