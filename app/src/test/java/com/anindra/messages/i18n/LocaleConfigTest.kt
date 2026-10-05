package com.anindra.messages.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Android only offers an app under Per-app language when the manifest declares
 *  `android:localeConfig`. Without it the app is missing from the picker even
 *  though every locale is translated, which is exactly the report this covers. */
class LocaleConfigTest {

    private val appDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "src/main").isDirectory) dir = dir.parentFile
        dir?.let { File(it, "src/main") } ?: error("src/main not found")
    }

    private val resDir: File by lazy { LocaleCatalog.resDir() }

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement

    @Test
    fun manifestDeclaresALocaleConfig() {
        val manifest = parse(File(appDir, "AndroidManifest.xml"))
        val apps = manifest.getElementsByTagName("application")
        assertEquals("expected exactly one <application>", 1, apps.length)
        val value = (apps.item(0) as Element).getAttribute("android:localeConfig")
        assertTrue(
            "android:localeConfig is missing, so the app is hidden from " +
                "Settings -> Per-app language",
            value == "@xml/locales_config"
        )
    }

    /** The declared list is the source of truth Crowdin and the shipped
     *  `values-*` directories are both checked against, so a language added in
     *  Crowdin only has to be recorded here once. */
    @Test
    fun declaredLocalesMatchTheShippedResourceDirs() {
        val declared = LocaleCatalog.declaredLocales(resDir) - LocaleCatalog.SOURCE_LANGUAGE
        val shipped = LocaleCatalog.localeDirs(resDir)
            .map { LocaleCatalog.tagForDirName(it.name)!! }
            .toSet()

        assertEquals("declared locales drifted from the shipped resources", shipped, declared)
    }

    @Test
    fun everyDeclaredLocaleHasAMatchingResourceDir() {
        for (tag in LocaleCatalog.declaredLocales(resDir) - LocaleCatalog.SOURCE_LANGUAGE) {
            val dir = File(resDir, LocaleCatalog.dirNameFor(tag))
            assertTrue(
                "locales_config.xml declares \"$tag\" but ${dir.name}/ is missing",
                dir.isDirectory
            )
        }
    }
}
