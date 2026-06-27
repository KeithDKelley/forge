package forge.itemmanager;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Tests for SFilterUtil's tokenizer and implicit-AND insertion.
 * These methods are purely string logic and require no card database.
 */
@Test
public class SFilterUtilQueryTest {

    // -------------------------------------------------------------------------
    // tokenize() tests
    // -------------------------------------------------------------------------

    @Test
    public void tokenize_singleToken() {
        assertEquals(SFilterUtil.tokenize("t:creature"), list("t:creature"));
    }

    @Test
    public void tokenize_leadingDashBecomesNot() {
        assertEquals(SFilterUtil.tokenize("-t:creature"), list("not", "t:creature"));
    }

    @Test
    public void tokenize_twoDashNegations() {
        assertEquals(SFilterUtil.tokenize("-t:creature -t:artifact"),
                list("not", "t:creature", "not", "t:artifact"));
    }

    @Test
    public void tokenize_quotedMultiWordValuePreservesQuotes() {
        // Quotes are preserved IN the token so the downstream parser (AdvancedSearchParser)
        // can strip them via unquote(). This is by design — the tokenizer does NOT unquote.
        assertEquals(SFilterUtil.tokenize("o:\"as long as you control a\""),
                list("o:\"as long as you control a\""));
    }

    @Test
    public void tokenize_negatedQuotedValue() {
        assertEquals(SFilterUtil.tokenize("-o:\"as long as you control a\""),
                list("not", "o:\"as long as you control a\""));
    }

    @Test
    public void tokenize_spacesSplitTokens() {
        assertEquals(SFilterUtil.tokenize("id:c t:creature r:common"),
                list("id:c", "t:creature", "r:common"));
    }

    @Test
    public void tokenize_parenthesesAreOwnTokens() {
        assertEquals(SFilterUtil.tokenize("(t:creature or t:artifact)"),
                list("(", "t:creature", "or", "t:artifact", ")"));
    }

    @Test
    public void tokenize_negatedParenGroup() {
        // -(...) → "not" then "(" as separate tokens
        assertEquals(SFilterUtil.tokenize("-(t:creature or t:artifact)"),
                list("not", "(", "t:creature", "or", "t:artifact", ")"));
    }

    @Test
    public void tokenize_pipeBecomesOr() {
        assertEquals(SFilterUtil.tokenize("t:creature|t:artifact"),
                list("t:creature", "or", "t:artifact"));
    }

    @Test
    public void tokenize_spaceInsideQuotesNotSplit() {
        // Spaces inside quotes must NOT produce new tokens
        List<String> tokens = SFilterUtil.tokenize("o:\"foo bar baz\"");
        assertEquals(tokens.size(), 1);
        assertEquals(tokens.get(0), "o:\"foo bar baz\"");
    }

    @Test
    public void tokenize_complexGlobalFilter() {
        // The actual Vryndal global filter — verifies the key structural tokens
        String filter = "id:c -t:planeswalker -(o:\"as long as you control a\" and o:planeswalker)";
        List<String> tokens = SFilterUtil.tokenize(filter);
        assertEquals(tokens.get(0), "id:c");
        assertEquals(tokens.get(1), "not");
        assertEquals(tokens.get(2), "t:planeswalker");
        assertEquals(tokens.get(3), "not");
        assertEquals(tokens.get(4), "(");
        assertEquals(tokens.get(5), "o:\"as long as you control a\"");
        assertEquals(tokens.get(6), "and");
        assertEquals(tokens.get(7), "o:planeswalker");
        assertEquals(tokens.get(8), ")");
        assertEquals(tokens.size(), 9);
    }

    @Test
    public void tokenize_emptyString() {
        assertEquals(SFilterUtil.tokenize(""), list());
    }

    @Test
    public void tokenize_onlySpaces() {
        assertEquals(SFilterUtil.tokenize("   "), list());
    }

    @Test
    public void tokenize_dashMidTokenNotNegation() {
        // A dash that is NOT at the start of a token boundary stays as part of the token
        List<String> tokens = SFilterUtil.tokenize("name:A-");
        assertEquals(tokens, list("name:A-"));
    }

    // -------------------------------------------------------------------------
    // insertImplicitAndTokens() tests
    // -------------------------------------------------------------------------

    @Test
    public void insertImplicit_emptyList() {
        assertEquals(SFilterUtil.insertImplicitAndTokens(list()), list());
    }

    @Test
    public void insertImplicit_singleToken() {
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("t:creature")), list("t:creature"));
    }

    @Test
    public void insertImplicit_twoTerms_getsAnd() {
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("id:c", "t:creature")),
                list("id:c", "and", "t:creature"));
    }

    @Test
    public void insertImplicit_threeTerms_twoAnds() {
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("id:c", "t:creature", "r:common")),
                list("id:c", "and", "t:creature", "and", "r:common"));
    }

    @Test
    public void insertImplicit_afterNot_noAndInserted() {
        // "not t:creature" should NOT become "not AND t:creature"
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("not", "t:creature")),
                list("not", "t:creature"));
    }

    @Test
    public void insertImplicit_afterOr_noAndInserted() {
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("t:creature", "or", "t:artifact")),
                list("t:creature", "or", "t:artifact"));
    }

    @Test
    public void insertImplicit_afterAnd_noAndInserted() {
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("t:creature", "and", "t:artifact")),
                list("t:creature", "and", "t:artifact"));
    }

    @Test
    public void insertImplicit_afterOpenParen_noAndInserted() {
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("(", "t:creature", ")")),
                list("(", "t:creature", ")"));
    }

    @Test
    public void insertImplicit_beforeCloseParen_noAndInserted() {
        // Last token before ")" must NOT get an AND
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("not", "(", "t:creature", "or", "t:artifact", ")")),
                list("not", "(", "t:creature", "or", "t:artifact", ")"));
    }

    @Test
    public void insertImplicit_tokenAfterCloseParen_getsAnd() {
        // After ")" the next term should get an implicit AND
        assertEquals(SFilterUtil.insertImplicitAndTokens(list("(", "t:creature", ")", "r:common")),
                list("(", "t:creature", ")", "and", "r:common"));
    }

    // -------------------------------------------------------------------------
    // Round-trip: tokenize then insertImplicit
    // -------------------------------------------------------------------------

    @Test
    public void roundtrip_simpleQuery() {
        List<String> tokens = SFilterUtil.insertImplicitAndTokens(
                SFilterUtil.tokenize("id:c t:creature"));
        assertEquals(tokens, list("id:c", "and", "t:creature"));
    }

    @Test
    public void roundtrip_negatedGroup() {
        List<String> tokens = SFilterUtil.insertImplicitAndTokens(
                SFilterUtil.tokenize("t:creature -(t:eldrazi or t:phyrexian)"));
        // Expected: t:creature AND not ( t:eldrazi or t:phyrexian )
        assertEquals(tokens, list("t:creature", "and", "not", "(", "t:eldrazi", "or", "t:phyrexian", ")"));
    }

    @Test
    public void roundtrip_presetToken() {
        // Verifies that "preset:name extra" tokenizes and gets implicit AND correctly
        List<String> tokens = SFilterUtil.insertImplicitAndTokens(
                SFilterUtil.tokenize("preset:colorless-creature -t:eldrazi"));
        assertEquals(tokens, list("preset:colorless-creature", "and", "not", "t:eldrazi"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static List<String> list(String... items) {
        return Arrays.asList(items);
    }
}
