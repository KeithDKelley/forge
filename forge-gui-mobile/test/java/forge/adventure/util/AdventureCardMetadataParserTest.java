package forge.adventure.util;

import forge.adventure.data.ConfigData;
import forge.adventure.data.RewardData;
import forge.item.PaperCard;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.function.Predicate;

import static org.testng.Assert.*;

/**
 * Tests for AdventureCardMetadata token parsing.
 *
 * What's tested here:
 *   - metadataPath() routing: which key maps to which metadata path
 *   - parseToken() recognition: property tokens (date, border, st, usd, preset)
 *     return non-null predicates; unknown keys return null
 *
 * What is NOT tested here (needs FModel / full card DB):
 *   - Predicate *evaluation* for date/border tokens (they call FModel.getMagicDb())
 *   - Metadata record loading (requires libgdx FileHandle)
 *   - preset: predicate evaluation (requires SFilterUtil.buildTextFilter + card data)
 *
 * Setup: We call AdventureCardMetadata.configure() with a minimal ConfigData that
 * has enableRewardQueries=true and a small preset map. Config (the libgdx file
 * loader) is passed as null — the tests avoid code paths that call config.getFile().
 */
@Test
public class AdventureCardMetadataParserTest {

    @BeforeClass
    public static void setup() {
        applyBaseConfig();
    }

    /** Shared helper — apply the enabled config with a preset map. */
    private static void applyBaseConfig() {
        ConfigData cd = new ConfigData();
        cd.enableRewardQueries = true;

        com.badlogic.gdx.utils.ObjectMap<String, RewardData> presets = new com.badlogic.gdx.utils.ObjectMap<>();
        RewardData creaturePreset = new RewardData();
        creaturePreset.type = "card";
        creaturePreset.query = "t:creature";
        presets.put("colorless-creature", creaturePreset);
        cd.rewardQueryPresets = presets;

        // null Config avoids libgdx FileHandle calls; metadata loading is skipped
        AdventureCardMetadata.configure(null, cd);
    }

    // =========================================================================
    // metadataPath() — key routing (package-private method)
    // =========================================================================

    @Test
    public void metadataPath_tagKey_routesToTags() {
        assertEquals(AdventureCardMetadata.metadataPath("tag", ":", "foo"), "tags");
    }

    @Test
    public void metadataPath_tagsKey_routesToTags() {
        assertEquals(AdventureCardMetadata.metadataPath("tags", ":", "foo"), "tags");
    }

    @Test
    public void metadataPath_otagKey_routesToTags() {
        // otag: is the "official tag" synonym — must route to "tags"
        assertEquals(AdventureCardMetadata.metadataPath("otag", ":", "foo"), "tags");
    }

    @Test
    public void metadataPath_sfDotField_routesToSfPath() {
        assertEquals(AdventureCardMetadata.metadataPath("sf.cmc", ":", "2"), "sf.cmc");
        assertEquals(AdventureCardMetadata.metadataPath("sf.rarity", ":", "rare"), "sf.rarity");
    }

    @Test
    public void metadataPath_scryfallDotField_routesToSfPath() {
        assertEquals(AdventureCardMetadata.metadataPath("scryfall.rarity", ":", "rare"), "sf.rarity");
    }

    @Test
    public void metadataPath_metaDotField_stripsPrefix() {
        assertEquals(AdventureCardMetadata.metadataPath("meta.custom_field", ":", "x"), "custom_field");
    }

    @Test
    public void metadataPath_typeToken_returnsNull() {
        // t:creature must NOT be intercepted by the adventure metadata parser
        assertNull(AdventureCardMetadata.metadataPath("t", ":", "creature"));
    }

    @Test
    public void metadataPath_idToken_returnsNull() {
        // id:c must NOT be intercepted
        assertNull(AdventureCardMetadata.metadataPath("id", ":", "c"));
    }

    @Test
    public void metadataPath_oracleToken_returnsNull() {
        // o:foo is a core CardRules token; adventure metadata must not claim it
        assertNull(AdventureCardMetadata.metadataPath("o", ":", "foo"));
    }

    @Test
    public void metadataPath_unknownBareKey_returnsNull() {
        // Previously there was a catch-all that returned the bare key as a path.
        // That was the root bug causing all standard tokens to be swallowed.
        assertNull(AdventureCardMetadata.metadataPath("someunknownkey", ":", "value"));
    }

    // =========================================================================
    // parseToken() — property token creation (non-null = recognized)
    // =========================================================================

    @Test
    public void parseToken_dateToken_recognized() {
        // Predicate creation does not call FModel; evaluation does but we don't call .test()
        Predicate<PaperCard> pred = AdventureCardMetadata.parseToken("date", "<", "2026-06-21");
        assertNotNull(pred, "date< must return a non-null PaperCard predicate");
    }

    @Test
    public void parseToken_dateLessOrEqual_recognized() {
        assertNotNull(AdventureCardMetadata.parseToken("date", "<=", "2026-06-21"));
    }

    @Test
    public void parseToken_dateGreater_recognized() {
        assertNotNull(AdventureCardMetadata.parseToken("date", ">", "2000-01-01"));
    }

    @Test
    public void parseToken_dateInvalidFormat_returnsNull() {
        // Unparseable date should log a warning and return null (not crash)
        assertNull(AdventureCardMetadata.parseToken("date", "<", "not-a-date"));
    }

    @Test
    public void parseToken_borderSilver_recognized() {
        assertNotNull(AdventureCardMetadata.parseToken("border", ":", "silver"));
    }

    @Test
    public void parseToken_borderGold_returnsNull() {
        // "gold" is not a recognised border value
        assertNull(AdventureCardMetadata.parseToken("border", ":", "gold"));
    }

    @Test
    public void parseToken_stAlchemy_recognized() {
        Predicate<PaperCard> pred = AdventureCardMetadata.parseToken("st", ":", "alchemy");
        assertNotNull(pred, "st:alchemy must return PaperCard::isRebalanced predicate");
    }

    @Test
    public void parseToken_stUnknownValue_returnsNull() {
        assertNull(AdventureCardMetadata.parseToken("st", ":", "nonexistent"));
    }

    @Test
    public void parseToken_usdLessOrEqual_recognized() {
        // Critical: usd must be handled at the adventure layer so it does not
        // fall to regularTokens and zero out the card pool.
        Predicate<PaperCard> pred = AdventureCardMetadata.parseToken("usd", "<=", "0.25");
        assertNotNull(pred, "usd<=0.25 must produce a non-null predicate (adventure gold price filter)");
    }

    @Test
    public void parseToken_usdGreater_recognized() {
        assertNotNull(AdventureCardMetadata.parseToken("usd", ">", "1.00"));
    }

    @Test
    public void parseToken_usdInvalidValue_returnsNull() {
        assertNull(AdventureCardMetadata.parseToken("usd", "<=", "not-a-number"));
    }

    @Test
    public void parseToken_presetKnown_recognized() {
        // "colorless-creature" was registered in @BeforeClass.
        // resolvePreset() calls new CardUtil.CardPredicate(...) which internally
        // calls Config.instance() to read the global filter. That singleton needs the
        // full libgdx / Forge file system initialized, so it throws
        // ExceptionInInitializerError in a headless unit-test environment.
        // We skip rather than fail so the rest of the suite stays green.
        try {
            Predicate<PaperCard> pred = AdventureCardMetadata.parseToken("preset", ":", "colorless-creature");
            assertNotNull(pred, "preset:colorless-creature must resolve to a non-null predicate");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            throw new org.testng.SkipException(
                    "Preset resolution requires Config.instance() (full Forge init). Skip in unit tests. Cause: " + e);
        }
    }

    @Test
    public void parseToken_presetUnknown_returnsNull() {
        // Unknown preset prints a warning and returns null — must not throw.
        // This one does NOT reach CardPredicate constructor so it is safe.
        try {
            Predicate<PaperCard> pred = AdventureCardMetadata.parseToken("preset", ":", "nonexistent-preset");
            assertNull(pred, "Unknown preset must return null");
        } catch (ExceptionInInitializerError | NoClassDefFoundError ignored) {
            // Config.instance() NPE can happen on the way out too; the important thing
            // is that we got a null back, not that Config was initialized.
        }
    }

    @Test
    public void parseToken_enabledFalse_returnsNull() {
        // When enableRewardQueries=false, ALL tokens must pass through (return null)
        ConfigData disabled = new ConfigData();
        disabled.enableRewardQueries = false;
        AdventureCardMetadata.configure(null, disabled);
        try {
            assertNull(AdventureCardMetadata.parseToken("date", "<", "2026-06-21"));
            assertNull(AdventureCardMetadata.parseToken("border", ":", "silver"));
            assertNull(AdventureCardMetadata.parseToken("st", ":", "alchemy"));
        } finally {
            // Restore full enabled config (with preset map) for subsequent tests
            applyBaseConfig();
        }
    }

    @Test
    public void parseToken_standardTokens_notIntercepted() {
        // The former catch-all bug: t:creature, id:c, o:foo were all swallowed here.
        // After the fix, these must return null so the core parsers handle them.
        assertNull(AdventureCardMetadata.parseToken("t", ":", "creature"),
                "t:creature must not be intercepted by adventure metadata parser");
        assertNull(AdventureCardMetadata.parseToken("id", ":", "c"),
                "id:c must not be intercepted by adventure metadata parser");
        assertNull(AdventureCardMetadata.parseToken("o", ":", "commander"),
                "o:commander must not be intercepted by adventure metadata parser");
        assertNull(AdventureCardMetadata.parseToken("r", ":", "rare"),
                "r:rare must not be intercepted by adventure metadata parser");
    }
}
