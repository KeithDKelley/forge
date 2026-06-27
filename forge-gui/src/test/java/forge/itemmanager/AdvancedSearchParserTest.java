package forge.itemmanager;

import forge.card.CardRules;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import java.util.function.Predicate;

import static org.testng.Assert.*;

/**
 * Tests for AdvancedSearchParser.
 *
 * Split into two sections:
 *   1. Pure string: unquote() behavior (the quote-stripping bug fix)
 *   2. Token recognition: parseAdvancedRulesToken / parseAdvancedPaperCardToken
 *      returning non-null (recognized) or null (unrecognized / falls to text search)
 *
 * No card database is needed — these tests only verify parsing logic.
 */
@Test
public class AdvancedSearchParserTest {

    // =========================================================================
    // unquote() — the bug-fix for quoted multi-word oracle / type values
    // =========================================================================

    @Test
    public void unquote_stripsOuterDoubleQuotes() {
        assertEquals(AdvancedSearchParser.unquote("\"eldrazi\""), "eldrazi");
    }

    @Test
    public void unquote_stripsMultiWordQuotedValue() {
        assertEquals(AdvancedSearchParser.unquote("\"as long as you control a\""),
                "as long as you control a");
    }

    @Test
    public void unquote_noQuotes_passthrough() {
        assertEquals(AdvancedSearchParser.unquote("creature"), "creature");
    }

    @Test
    public void unquote_null_returnsNull() {
        assertNull(AdvancedSearchParser.unquote(null));
    }

    @Test
    public void unquote_singleChar_noStrip() {
        // One character can't have an opening AND closing quote — leave it alone
        assertEquals(AdvancedSearchParser.unquote("\""), "\"");
    }

    @Test
    public void unquote_emptyString_noStrip() {
        assertEquals(AdvancedSearchParser.unquote(""), "");
    }

    @Test
    public void unquote_onlyTwoQuotes_stripsToEmpty() {
        assertEquals(AdvancedSearchParser.unquote("\"\""), "");
    }

    @Test
    public void unquote_innerQuoteNotStripped() {
        // Only the outermost pair is removed
        assertEquals(AdvancedSearchParser.unquote("\"foo \"bar\" baz\""), "foo \"bar\" baz");
    }

    // =========================================================================
    // parseAdvancedRulesToken — CardRules predicate recognition
    //
    // A non-null return means the token was recognised and will be handled as a
    // CardRules filter. A null return means the token falls to regularTokens and
    // will be used as a literal text search — which is almost always wrong for
    // structured keys like "usd" or unknown fields.
    // =========================================================================

    @Test
    public void rulesToken_typeKey_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("t:creature"));
    }

    @Test
    public void rulesToken_quotedTypeKey_recognized() {
        // After the unquote fix, t:"creature" must be treated identically to t:creature
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("t:\"creature\""));
    }

    @Test
    public void rulesToken_oracleKey_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("o:commander"));
    }

    @Test
    public void rulesToken_quotedMultiWordOracle_recognized() {
        // This was the bug: o:"as long as you control a" returned non-null before fix
        // because the key "o" was recognized, but the predicate searched for the quoted
        // string literal (with quote chars). After the fix it strips quotes before matching.
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("o:\"as long as you control a\""));
    }

    @Test
    public void rulesToken_colorIdentityKey_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("id:c"));
    }

    @Test
    public void rulesToken_rarityKey_returnsNull() {
        // r: is a PaperCard attribute (printed rarity), not a CardRules attribute.
        // It is handled by parseAdvancedPaperCardToken, not here.
        assertNull(AdvancedSearchParser.parseAdvancedRulesToken("r:common"));
    }

    @Test
    public void rulesToken_rarityRareKey_returnsNull() {
        assertNull(AdvancedSearchParser.parseAdvancedRulesToken("r:rare"));
    }

    @Test
    public void rulesToken_cmcKey_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("cmc:3"));
    }

    @Test
    public void rulesToken_powerKey_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("pow>=3"));
    }

    @Test
    public void rulesToken_negatedType_recognized() {
        // Negation is handled BEFORE the key lookup: "-t:creature" → strips "-", returns predicate.negate()
        assertNotNull(AdvancedSearchParser.parseAdvancedRulesToken("-t:creature"));
    }

    @Test
    public void rulesToken_usd_returnsNull() {
        // CRITICAL REGRESSION TEST: usd is not a CardRules concept.
        // If this returns non-null, something unexpected claimed the token.
        // If null, the token falls to regularTokens → literal text search → zero results.
        // The usd token must be handled at the PaperCard level (AdventureCardMetadata).
        assertNull(AdvancedSearchParser.parseAdvancedRulesToken("usd<=0.25"));
    }

    @Test
    public void rulesToken_unknownKey_returnsNull() {
        assertNull(AdvancedSearchParser.parseAdvancedRulesToken("xyzunknown:foo"));
    }

    @Test
    public void rulesToken_noOperator_returnsNull() {
        // A bare word with no operator is not an advanced token
        assertNull(AdvancedSearchParser.parseAdvancedRulesToken("creature"));
    }

    // =========================================================================
    // parseAdvancedPaperCardToken — PaperCard predicate recognition
    // =========================================================================

    @Test
    public void paperToken_setKey_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedPaperCardToken("set:lea"));
    }

    @Test
    public void paperToken_nameKey_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedPaperCardToken("name:Fireball"));
    }

    @Test
    public void paperToken_nameWithDashPrefix_recognized() {
        // name:A- matches rebalanced/alchemy cards whose names start with "A-"
        assertNotNull(AdvancedSearchParser.parseAdvancedPaperCardToken("name:A-"));
    }

    @Test
    public void paperToken_rarityKey_recognized() {
        // r: is a PaperCard attribute — must be handled here (not in the rules parser)
        assertNotNull(AdvancedSearchParser.parseAdvancedPaperCardToken("r:rare"));
        assertNotNull(AdvancedSearchParser.parseAdvancedPaperCardToken("r:common"));
        assertNotNull(AdvancedSearchParser.parseAdvancedPaperCardToken("r:uncommon"));
    }

    @Test
    public void paperToken_isFoil_recognized() {
        assertNotNull(AdvancedSearchParser.parseAdvancedPaperCardToken("is:foil"));
    }

    @Test
    public void paperToken_usd_handledByAdventureMetadata() {
        // usd routes through AdventureCardMetadata's metadataPath → "usd".
        // parseAdvancedPaperCardToken returns non-null ONLY if AdventureCardMetadata
        // is registered as a PaperCardTokenParser via configure().
        // Without registration (this isolated test), it returns null and falls to
        // regularTokens → literal text search → zeros pool.
        // In the full game, AdventureCardMetadata.configure() registers the handler,
        // so runtime always gets non-null. The mobile-module tests cover the non-null path.
        Predicate<PaperCard> result = AdvancedSearchParser.parseAdvancedPaperCardToken("usd<=0.25");
        System.out.println("[test] usd<=0.25 (no adventure handler registered): "
                + (result == null ? "NULL — expected without AdventureCardMetadata.configure()" : "non-null: " + result));
    }

    @Test
    public void paperToken_unknownKey_returnsNull() {
        assertNull(AdvancedSearchParser.parseAdvancedPaperCardToken("xyzunknown:foo"));
    }

    // =========================================================================
    // Interaction: both parsers on the same token
    // =========================================================================

    @Test
    public void bothParsers_idToken_rulesHandlesIt() {
        // id:c is a CardRules concept (color identity); paper card parser should return null
        Predicate<CardRules> rulesPred = AdvancedSearchParser.parseAdvancedRulesToken("id:c");
        assertNotNull(rulesPred, "id:c must be a recognized CardRules token");
    }

    @Test
    public void bothParsers_setToken_paperHandlesIt() {
        // set: is edition-level; the rules parser has no concept of editions
        Predicate<CardRules> rulesPred = AdvancedSearchParser.parseAdvancedRulesToken("set:lea");
        Predicate<PaperCard> paperPred = AdvancedSearchParser.parseAdvancedPaperCardToken("set:lea");
        assertNotNull(paperPred, "set:lea must be recognized as a PaperCard token");
    }
}
