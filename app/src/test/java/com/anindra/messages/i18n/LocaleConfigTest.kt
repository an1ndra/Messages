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

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement

    /** values-hi-rIN -> hi-IN; resource qualifiers and BCP-47 differ in case and
     *  use -r for the region, so this is the mapping Android expects. */
    private fun dirToLocale(dirName: String): String {
        val q = dirName.removePrefix("values-")
        val m = Regex("^([a-z]{2})(?:-r([A-Z]{2}))?$").find(q)
            ?: error("unexpected locale dir $dirName")
        val lang = m.groupValues[1]
        val region = m.groupValues[2]
        return if (region.isEmpty()) lang else "$lang-$region"
    }

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

    @Test
    fun declaredLocalesMatchTheShippedResourceDirs() {
        val declared = parse(File(appDir, "res/xml/locales_config.xml"))
            .let { root ->
                val n = root.getElementsByTagName("locale")
                (0 until n.length).map { (n.item(it) as Element).getAttribute("android:name") }
            }.toSet()

        val shipped = File(appDir, "res")
            .listFiles { f: File ->
                f.isDirectory && f.name.matches(Regex("values(-[a-z]{2}(-r[A-Z]{2})?)?"))
            }
            .orEmpty()
            .map { if (it.name == "values") "en" else dirToLocale(it.name) }
            .toSet()

        assertEquals("declared locales drifted from the shipped resources", shipped, declared)
    }
}
