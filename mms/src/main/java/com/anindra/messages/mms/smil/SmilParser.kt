package com.anindra.messages.mms.smil

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

/**
 * Reads a SMIL part sent by a carrier.
 *
 * Tolerant by design: inbound slideshows carry `<meta>`, region layouts and
 * `region` attributes that we never emit, and rejecting a message over its
 * presentation metadata would be a bad trade. Never throws — a malformed
 * slideshow is a carrier's bug, not a reason to fail the whole message.
 */
object SmilParser {

    /**
     * The part is hostile input. External entity resolution is off in every form
     * the JDK offers — features, expandEntityReferences, and a resolver that
     * answers nothing — because a `file:///etc/passwd` entity is otherwise a
     * successful parse.
     */
    private fun newFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            disableFeature("http://xml.org/sax/features/external-general-entities")
            disableFeature("http://xml.org/sax/features/external-parameter-entities")
            disableFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd")
        }

    /** The feature names differ between parsers; a parser lacking one is not fatal. */
    private fun DocumentBuilderFactory.disableFeature(name: String) {
        try {
            setFeature(name, false)
        } catch (ignored: Exception) {
        }
    }

    fun parse(bytes: ByteArray): SmilDocument? {
        val root = try {
            val builder = newFactory().newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(ByteArrayInputStream(ByteArray(0))) }
            // Xerces prints parse errors to stderr by default; on a handset that
            // is logcat noise for a part the caller already tolerates as broken.
            builder.setErrorHandler(SilentErrorHandler)
            builder.parse(ByteArrayInputStream(bytes)).documentElement
        } catch (ignored: Exception) {
            return null
        } ?: return null

        if (root.localNameOrNull() != "smil") return null

        val compatibility = metaContent(root, "mms-compatibility")
        var parDurationMillis = SmilDocument.DEFAULT_PAR_DURATION_MILLIS
        var foundDuration = false
        val pars = mutableListOf<SmilPar>()

        elements(root).filter { it.localNameOrNull() == "body" }.forEach { body ->
            elements(body).filter { it.localNameOrNull() == "par" }.forEach { par ->
                if (!foundDuration) {
                    SmilClock.parse(par.getAttribute("dur"))?.let {
                        parDurationMillis = it
                        foundDuration = true
                    }
                }
                val items = elements(par).mapNotNull { child ->
                    val src = child.getAttribute("src")
                    if (src.isEmpty()) null
                    else SmilItem.forElementName(child.localNameOrNull() ?: return@mapNotNull null, src)
                }
                if (items.isNotEmpty()) pars.add(SmilPar(items))
            }
        }

        if (pars.isEmpty()) return null
        return SmilDocument(pars, parDurationMillis, compatibility)
    }

    /** `<meta>` sits under `<head>`, and carriers nest it further than that. */
    private fun metaContent(root: Element, name: String): String? =
        elements(root).filter { it.localNameOrNull() == "meta" }.firstOrNull {
            it.getAttribute("name") == name
        }?.getAttribute("content")?.takeIf { it.isNotBlank() }

    private fun elements(node: Node): List<Element> {
        val found = mutableListOf<Element>()
        val children = node.childNodes
        (0 until children.length).forEach { index ->
            val child = children.item(index)
            if (child is Element) {
                found.add(child)
                found.addAll(elements(child))
            }
        }
        return found
    }

    /** Namespaced documents report `localName`; one parsed without a prefix may not. */
    private fun Node.localNameOrNull(): String? = localName ?: nodeName.takeIf { it != "#document" }

    private object SilentErrorHandler : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit
        override fun error(exception: SAXParseException) = Unit
        override fun fatalError(exception: SAXParseException) = Unit
    }
}
