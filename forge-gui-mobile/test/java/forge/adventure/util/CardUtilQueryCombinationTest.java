package forge.adventure.util;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests for CardUtil.CardPredicate.buildEffectiveQuery().
 *
 * This method combines a per-plane global filter with an individual reward's
 * query, producing the final query string fed to SFilterUtil.buildTextFilter.
 *
 * The package-private overload
 *   buildEffectiveQuery(boolean enableRewardQueries, String globalFilter, String rewardQuery)
 * is tested here without any Config/libgdx dependency.
 */
@Test
public class CardUtilQueryCombinationTest {

    // Convenience shorthand
    private static String combine(boolean enabled, String global, String local) {
        return CardUtil.CardPredicate.buildEffectiveQuery(enabled, global, local);
    }

    // =========================================================================
    // enableRewardQueries=false — always null regardless of inputs
    // =========================================================================

    @Test
    public void disabled_nullGlobal_nullLocal_returnsNull() {
        assertNull(combine(false, null, null));
    }

    @Test
    public void disabled_withGlobal_returnsNull() {
        assertNull(combine(false, "id:c", null));
    }

    @Test
    public void disabled_withLocal_returnsNull() {
        assertNull(combine(false, null, "t:creature"));
    }

    @Test
    public void disabled_withBoth_returnsNull() {
        assertNull(combine(false, "id:c", "t:creature"));
    }

    // =========================================================================
    // enableRewardQueries=true — combination logic
    // =========================================================================

    @Test
    public void enabled_bothNull_returnsNull() {
        assertNull(combine(true, null, null));
    }

    @Test
    public void enabled_bothEmpty_returnsNull() {
        assertNull(combine(true, "", ""));
    }

    @Test
    public void enabled_globalOnly_returnsGlobal() {
        assertEquals(combine(true, "id:c -t:planeswalker", null), "id:c -t:planeswalker");
    }

    @Test
    public void enabled_globalOnly_withWhitespace_trimmed() {
        assertEquals(combine(true, "  id:c  ", null), "id:c");
    }

    @Test
    public void enabled_localOnly_returnsLocal() {
        assertEquals(combine(true, null, "t:creature"), "t:creature");
    }

    @Test
    public void enabled_localOnly_emptyGlobal_returnsLocal() {
        assertEquals(combine(true, "", "t:creature"), "t:creature");
    }

    @Test
    public void enabled_both_wrapsInParens() {
        String result = combine(true, "id:c", "t:creature");
        assertEquals(result, "(id:c) (t:creature)");
    }

    @Test
    public void enabled_both_withWhitespace_trimmed() {
        String result = combine(true, "  id:c  ", "  t:creature  ");
        assertEquals(result, "(id:c) (t:creature)");
    }

    @Test
    public void enabled_both_presetInLocal_wrapsCorrectly() {
        // A realistic example: global filter + reward query that references a preset
        String global = "id:c -t:planeswalker date<2026-06-21";
        String local  = "preset:colorless-creature -t:eldrazi";
        String result = combine(true, global, local);
        assertEquals(result, "(" + global + ") (" + local + ")");
    }

    @Test
    public void enabled_globalOnly_blankLocal_returnsGlobal() {
        // A reward with no query: should use only the global filter
        assertEquals(combine(true, "id:c", "   "), "id:c");
    }

    @Test
    public void enabled_blankGlobal_withLocal_returnsLocal() {
        assertEquals(combine(true, "   ", "t:artifact"), "t:artifact");
    }

    // =========================================================================
    // Structural invariants
    // =========================================================================

    @Test
    public void resultHasParensAroundBoth_whenBothPresent() {
        String result = combine(true, "GLOBAL", "LOCAL");
        assertTrue(result.startsWith("(GLOBAL)"), "Global part must be wrapped in parens");
        assertTrue(result.endsWith("(LOCAL)"), "Local part must be wrapped in parens");
    }

    @Test
    public void resultIsNotWrapped_whenOnlyOnePresent() {
        String onlyGlobal = combine(true, "GLOBAL", null);
        assertFalse(onlyGlobal.startsWith("("), "Single part must NOT be wrapped in parens");

        String onlyLocal = combine(true, null, "LOCAL");
        assertFalse(onlyLocal.startsWith("("), "Single part must NOT be wrapped in parens");
    }
}
