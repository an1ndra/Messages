package com.anindra.messages.ci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Play listing rejects a screenshot that is not a PNG of 320..3840 px on
 * both sides with an aspect ratio of at most 2:1 — which is exactly what the
 * raw 1080x2400 captures are (20:9), so `make-store-screenshots.py` frames them
 * into 1080x1920. This test holds that contract at build time.
 */
class StoreScreenshotTest {

    private val shots: List<File> by lazy {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "fastlane/metadata/android/en-US/images/phoneScreenshots") }
            .firstOrNull { it.isDirectory }
            ?: error("phoneScreenshots directory not found")
        dir.listFiles { file -> file.extension == "png" }?.sortedBy { it.name } ?: emptyList()
    }

    @Test
    fun theListingHasTheCuratedSetOfScreenshots() {
        assertEquals(
            "the listing is published as 8 phone screenshots, numbered 01..08",
            (1..8).map { "%02d".format(it) },
            shots.map { it.name.substringBefore('-') }
        )
    }

    @Test
    fun everyScreenshotIsA1080x1920Png() {
        shots.forEach { shot ->
            val header = shot.readBytes()
            assertTrue("${shot.name} is not a PNG", header.size > 24 && header.startsWithPng())
            assertEquals("${shot.name} must be a 1080x1920 listing frame", 1080 to 1920, header.pngSize())
        }
    }

    @Test
    fun everyScreenshotIsAFramedCompositeRatherThanARawCapture() {
        shots.forEach { shot ->
            assertTrue("${shot.name} is suspiciously small", shot.length() > 20_000)
        }
    }

    private fun ByteArray.startsWithPng() =
        this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() &&
            this[2] == 'N'.code.toByte() && this[3] == 'G'.code.toByte()

    private fun ByteArray.pngSize(): Pair<Int, Int> = intAt(16) to intAt(20)

    private fun ByteArray.intAt(at: Int): Int =
        (this[at].toInt() and 0xFF shl 24) or (this[at + 1].toInt() and 0xFF shl 16) or
            (this[at + 2].toInt() and 0xFF shl 8) or (this[at + 3].toInt() and 0xFF)
}
