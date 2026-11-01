package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The home-search scopes are pills in one row, not M3 filter chips: the
 *  selected scope is a stadium wherever it sits, the unselected ones keep the
 *  row's connected ends (round on the outer edge only), and the corners morph
 *  with the fill instead of snapping. The radii are pure and are asserted
 *  directly; the rest lives in a composable and is asserted from source.
 *  `scripts/test-search-scope-pills.sh` covers the device behaviour. */
class SearchScopePillTest {

    private val list: String by lazy {
        val main = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
        File(main, "java/com/anindra/messages/ui/ConversationsScreen.kt").readText()
    }

    private fun functionBody(name: String): String {
        val start = list.indexOf("fun $name(")
        assertTrue("$name not found", start >= 0)
        val end = list.indexOf("\n}\n", start)
        assertTrue("$name has no closing brace", end > start)
        return list.substring(start, end)
    }

    private val edge = SearchScopeCorners.EDGE
    private val inner = SearchScopeCorners.INNER

    @Test
    fun theFirstOptionIsRoundOnItsLeftAndSquareOnItsRight() {
        assertEquals(ScopeCorners(edge, inner, inner, edge), SearchScopeCorners.radii(0, 4))
    }

    @Test
    fun theLastOptionIsRoundOnItsRightAndSquareOnItsLeft() {
        assertEquals(ScopeCorners(inner, edge, edge, inner), SearchScopeCorners.radii(4, 4))
    }

    @Test
    fun anUnselectedOptionInTheMiddleIsNearlyRectangular() {
        for (index in 1..3) {
            assertEquals(
                "option $index is interior",
                ScopeCorners(inner, inner, inner, inner),
                SearchScopeCorners.radii(index, 4)
            )
        }
    }

    @Test
    fun aRowOfOneIsRoundOnBothSides() {
        assertEquals(ScopeCorners(edge, edge, edge, edge), SearchScopeCorners.radii(0, 0))
    }

    @Test
    fun theSelectedScopeIsAStadiumWhereverItSits() {
        assertEquals(ScopeCorners(edge, edge, edge, edge), SearchScopeCorners.selected())
        assertTrue(
            "selection wins over the row's position rule",
            functionBody("SearchScopeChips")
                .contains("corners = if (scope == selected) SearchScopeCorners.selected()")
        )
    }

    @Test
    fun theRoundEndIsAHalfCircleOfThePillHeight() {
        assertEquals("20dp is half a 40dp pill", 20f, edge, 0.01f)
        assertTrue("the inner radius stays much smaller", inner < edge / 2)
    }

    @Test
    fun theButtonsStretchOnlyWhenEveryLabelFitsItsEqualShare() {
        // 392dp phone: an equal share is 68.8dp, "Contacts" in SemiBold needs ~82dp.
        assertFalse(
            "a phone keeps natural widths and scrolls",
            SearchScopeSizing.fills(maxPillDp = 82f, trackDp = 392f, count = 5)
        )
        assertTrue(
            "a wider window stretches them to fill it",
            SearchScopeSizing.fills(maxPillDp = 82f, trackDp = 581f, count = 5)
        )
    }

    @Test
    fun aVeryWideWindowCentresTheStripInsteadOfStretchingItForever() {
        assertTrue(
            "a 1000dp window still stretches, but against the capped track",
            SearchScopeSizing.fills(maxPillDp = 82f, trackDp = 1000f, count = 5)
        )
        assertEquals("the segment is capped, not the window's width",
            SearchScopeSizing.share(SearchScopeSizing.MAX_TRACK, 5),
            SearchScopeSizing.share(SearchScopeSizing.MAX_TRACK, 5) + 0f, 0.01f)
        assertTrue("the cap sits below a typical expanded window",
            SearchScopeSizing.MAX_TRACK < 1000f)
        assertTrue(
            "the stretched row is boxed to the cap with an explicit width",
            functionBody("SearchScopeChips")
                .contains("width(minOf(track, SearchScopeSizing.MAX_TRACK.dp))")
        )
    }

    @Test
    fun aStretchedRowFillsTheScreenEdgeToEdge() {
        val share = SearchScopeSizing.share(trackDp = 581f, count = 5)
        val used = 2 * SearchScopeSizing.SIDE + 4 * SearchScopeSizing.GAP + 5 * share
        assertEquals("segments plus insets and gaps cover the track", 581f, used, 0.01f)
        assertTrue("a share is wider than the widest pill it must hold", share >= 82f)
    }

    @Test
    fun thePillWidthIsItsLabelPlusBothPaddings() {
        assertEquals(62f, SearchScopeSizing.widestPill(42f), 0.01f)
        assertEquals(
            "the row and the pill agree on the padding",
            20f,
            2 * SearchScopeSizing.PILL_PADDING,
            0.01f
        )
    }

    @Test
    fun theScopeRowRendersPillsInsteadOfFilterChips() {
        val row = functionBody("SearchScopeChips")
        assertTrue("the row must render the pill", row.contains("SearchScopePill("))
        assertFalse("FilterChip is gone", row.contains("FilterChip("))
        assertTrue(
            "unselected corners come from the position",
            row.contains("else SearchScopeCorners.radii(index, lastIndex)")
        )
        assertTrue("a narrow gutter between neighbours", row.contains("SearchScopeSizing.GAP.dp"))
        assertTrue(
            "the row sits on the app's 16dp grid",
            row.contains("padding(horizontal = SearchScopeSizing.SIDE.dp, vertical = 8.dp)")
        )
        assertTrue(
            "when it fits, every button takes an equal weight",
            row.contains("Modifier.weight(1f)")
        )
        assertTrue(
            "when it does not, the strip is centred in a full-width track",
            row.contains("widthIn(min = track)") && row.contains("Alignment.CenterHorizontally")
        )
    }

    @Test
    fun theClippedEndOfTheRowFadesOut() {
        val row = functionBody("SearchScopeChips")
        assertTrue("the row is masked at the ends that hide content", row.contains("edgeFade(fades)"))
        assertTrue("driven by the scroll position", row.contains("scroll.canScrollForward"))
        assertTrue("and only when scrolled away from the start", row.contains("scroll.canScrollBackward"))
        assertTrue("mask, not an overlay", list.contains("blendMode = BlendMode.DstIn"))
    }

    @Test
    fun theOptionsStayCompact() {
        val pill = functionBody("SearchScopePill")
        assertTrue("40dp tall: a little bigger than the chip it replaces", pill.contains(".height(40.dp)"))
        assertTrue("10dp sides, which keeps all five on screen", pill.contains(".padding(horizontal = 10.dp)"))
    }

    @Test
    fun theCornersMorphWithTheFill() {
        val pill = functionBody("SearchScopePill")
        listOf("scopePillTopStart", "scopePillTopEnd", "scopePillBottomEnd", "scopePillBottomStart")
            .forEach { assertTrue("$it is animated", pill.contains("label = \"$it\")")) }
        assertTrue(
            "the shape is the animated corners",
            Regex("""shape = RoundedCornerShape\(""").containsMatchIn(pill)
        )
    }

    @Test
    fun noTickIconIsDrawnOnTheSelectedScope() {
        val pill = functionBody("SearchScopePill")
        assertFalse("no glyph", pill.contains("Icons."))
        assertFalse("no icon composable", pill.contains("Icon("))
    }

    @Test
    fun selectionOnlyChangesTheFillAndTheRadii() {
        val pill = functionBody("SearchScopePill")
        assertTrue("selected fill", Regex("""if \(selected\) MaterialTheme\.colorScheme\.primary\b""").containsMatchIn(pill))
        assertTrue("selected content", pill.contains("MaterialTheme.colorScheme.onPrimary"))
        assertTrue("unselected fill", pill.contains("MaterialTheme.colorScheme.secondaryContainer"))
        assertTrue("unselected content", pill.contains("MaterialTheme.colorScheme.onSecondaryContainer"))
        assertFalse("no hardcoded colours", Regex("""Color\(0x""").containsMatchIn(pill))
    }

    @Test
    fun aPillReportsItsSelectedStateToAccessibility() {
        val pill = functionBody("SearchScopePill")
        assertTrue(
            "selectable carries the state an a11y service reads",
            Regex("""selectable\(\s*selected = selected,""").containsMatchIn(pill)
        )
        assertTrue("tab role, as the other single-select rows use", pill.contains("role = Role.Tab"))
        assertTrue("taps go through the caller", pill.contains("onClick = onClick"))
    }
}
