package com.anindra.messages

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Issue #332: the app must not request network access.
 *
 * SMS is offline, and MMS goes through the platform's own MMS service, which is
 * what the app hands the composed PDU to — nothing here opens a socket. A stray
 * INTERNET, ACCESS_NETWORK_STATE or CHANGE_NETWORK_STATE would show up in the
 * F-Droid permission diff and break the app's whole "no network access"
 * promise, so every manifest that merges into the APK is checked, not just the
 * app's own. The merged APK is checked for real by
 * `scripts/test-no-network-permission.sh`.
 */
class NoNetworkPermissionTest {

    private val networkPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
    )

    @Test
    fun noManifestDeclaresANetworkPermission() {
        val root = repoRoot()
        for (rel in listOf("app/src/main/AndroidManifest.xml", "mms/src/main/AndroidManifest.xml")) {
            val offending = declaredPermissions(parse(File(root, rel))).intersect(networkPermissions)
            assertTrue(
                "$rel declares network permissions: $offending — SMS is offline and MMS " +
                    "runs through the platform service, so none is needed",
                offending.isEmpty(),
            )
        }
    }

    @Test
    fun theManifestsAreFoundSoTheCheckIsNotVacuous() {
        val root = repoRoot()
        for (rel in listOf("app/src/main/AndroidManifest.xml", "mms/src/main/AndroidManifest.xml")) {
            assertTrue("$rel not found", File(root, rel).isFile)
            assertTrue("$rel declares no permissions at all", declaredPermissions(parse(File(root, rel))).isNotEmpty())
        }
    }

    private fun declaredPermissions(manifest: Element): Set<String> {
        val nodes = manifest.getElementsByTagName("uses-permission")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            // A permission merged in by a dependency and stripped with
            // tools:node="remove" is not requested by the APK, so it does not count.
            .filter { it.getAttributeNS(TOOLS_NS, "node") != "remove" }
            .map { it.getAttributeNS(ANDROID_NS, "name") }
            .toSet()
    }

    private fun parse(file: File): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    /** The repo root, found by walking up until both module manifests exist. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "mms/src/main/AndroidManifest.xml").isFile &&
                File(dir, "app/src/main/AndroidManifest.xml").isFile
            ) {
                return dir
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate the repo root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val TOOLS_NS = "http://schemas.android.com/tools"
    }
}
