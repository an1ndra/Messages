package com.anindra.messages.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Single source of truth for the languages the app ships: `res/xml/locales_config.xml`.
 *
 *  Crowdin is driven off the same list, so tests read it instead of hardcoding
 *  locale directory names. Hardcoding meant every added language turned CI red
 *  until three files were hand-edited together. */
internal object LocaleCatalog {

    fun resDir(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "src/main").isDirectory) dir = dir.parentFile
        return dir?.let { File(it, "src/main/res") } ?: error("src/main/res not found")
    }

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement

    /** Language tags from locales_config.xml, source language included. */
    fun declaredLocales(resDir: File): Set<String> =
        parse(File(resDir, "xml/locales_config.xml")).let { root ->
            val nodes = root.getElementsByTagName("locale")
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
        }.toSet()

    /** `pt-BR` -> `values-pt-rBR`, `hi` -> `values-hi`, `en` -> `values`. */
    fun dirNameFor(tag: String): String {
        if (tag == SOURCE_LANGUAGE) return "values"
        val (lang, region) = tag.split('-', limit = 2).let {
            it[0] to it.getOrNull(1)
        }
        return if (region == null) "values-$lang" else "values-$lang-r$region"
    }

    /** Inverse of [dirNameFor] for a shipped resource directory. */
    fun tagForDirName(name: String): String? {
        if (name == "values") return SOURCE_LANGUAGE
        val qualifier = name.removePrefix("values-").takeIf { name.startsWith("values-") } ?: return null
        val m = Regex("^([a-z]{2})(?:-r([A-Z]{2}))?$").find(qualifier) ?: return null
        val region = m.groupValues[2]
        return if (region.isEmpty()) m.groupValues[1] else "${m.groupValues[1]}-$region"
    }

    /** Shipped `values-*` directories holding translatable strings. `values-night`
     *  and `values-v31` are theme qualifiers, not locales, and the base `values`
     *  is the source language rather than a target, so all three are excluded. */
    fun localeDirs(resDir: File): List<File> =
        resDir.listFiles { f: File ->
            f.isDirectory && f.name != "values" && tagForDirName(f.name) != null
        }.orEmpty().sortedBy { it.name }

    const val SOURCE_LANGUAGE = "en"
}
