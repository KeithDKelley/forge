package forge.adventure.data;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * Data class that will be used to read Json configuration files
 * BiomeData
 * contains general information about the game
 */
public class ConfigData {
    public int screenWidth;
    public int screenHeight;
    public String skin;
    public String font;
    public String fontColor;
    public int minDeckSize;
    public int maxNumberOfDecks;
    public float playerBaseSpeed;
    public String[] colorIds;
    public String[] colorIdNames;
    public String[] starterEditions;
    public String[] starterEditionNames;
    public ObjectMap<String, ObjectMap<String, String>> starterDecksByEdition;
    public DifficultyData[] difficulties;
    public RewardData legalCards;
    public String[] restrictedCards;
    public String[] restrictedEditions;
    public String[] restrictedBlocks;
    public String[] restrictedTokens;
    public String[] allowedEditions;
    public boolean vintageOnlyEditions = false;
    public String[] restrictedEvents;
    public String[] allowedEvents;
    public String[] allowedJumpstart;
    public String cardPrintingPreference;
    public boolean enableRewardQueries = false;
    public String[] rewardQueryMetadata;
    public String rewardQueryGlobalFilter;
    public ObjectMap<String, RewardData> rewardQueryPresets;
    public ObjectMap<String, FlagLimits> characterFlagLimits;

    public static class FlagLimits {
        public int min = Integer.MIN_VALUE;
        public int max = Integer.MAX_VALUE;
        public String trackedBy; // if set, |actual change| is added to this flag on every modification
    }
    public String defaultBasicLandSet = "JMP";
    public boolean enableGeneticAI = true;
    public String chaosDeckFormat;
    public boolean usePriceListPrices = true;

}
