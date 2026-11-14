package com.anindra.messages.ci

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CodeQL's `java/local-variable-is-never-read` flags the `other_with_cast`
 * temporary inside the `equals` that Kotlin generates for a `data object`. The
 * diagnostic carries no source region, so it cannot be cleared where it is
 * reported (github/codeql#22987); the declaration is the only lever.
 *
 * A plain `object` compares by identity, which is exactly what these sealed
 * singletons need, and generates no `equals` and therefore no unread local.
 */
class CodeQlDataObjectTest {

    private val declaration = Regex("""^\s*(?:public |internal |private |protected )?data object\s+\w+""")

    @Test
    fun mainSourcesDeclareNoDataObjects() {
        val roots = listOf("app/src/main/java", "mms/src/main/java")
        val offenders = roots.flatMap { root ->
            File(LocaleCatalog.repoRoot(), root).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.useLines { lines ->
                        lines.mapIndexedNotNull { index, line ->
                            if (declaration.containsMatchIn(line)) "${file.path}:${index + 1}" else null
                        }.toList()
                    }.asSequence()
                }
                .toList()
        }

        assertTrue(
            "data object generates an unread equals temporary that CodeQL reports " +
                "(github/codeql#22987); declare a plain object instead: $offenders",
            offenders.isEmpty()
        )
    }
}
