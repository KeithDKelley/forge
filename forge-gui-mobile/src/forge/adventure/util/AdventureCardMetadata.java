package forge.adventure.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import forge.adventure.data.ConfigData;
import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.itemmanager.AdvancedSearchParser;
import forge.model.FModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plane-local metadata overlay used by Adventure reward queries.
 */
public final class AdventureCardMetadata {
    private static final String[] OPERATORS = {"!=", "<=", ">=", "=", "<", ">", ":", "!"};
    private static final String DEFAULT_METADATA_PATH = "data/card_metadata.jsonl";

    private static Config config;
    private static ConfigData configData;
    private static boolean registered;
    private static boolean loaded;
    private static final Map<String, List<Record>> byPrint = new HashMap<>();
    private static final Map<String, List<Record>> byName = new HashMap<>();

    private AdventureCardMetadata() {
    }

    public static void configure(Config config0, ConfigData configData0) {
        config = config0;
        configData = configData0;
        loaded = false;
        byPrint.clear();
        byName.clear();
        if (!registered) {
            AdvancedSearchParser.registerPaperCardTokenParser(AdventureCardMetadata::parseToken);
            registered = true;
        }
    }

    public static Predicate<PaperCard> parseToken(String key, String operator, String value) {
        if (configData == null || !configData.enableRewardQueries) {
            return null;
        }
        String path = metadataPath(key, operator, value);
        String op = operator;
        String expected = value;

        if (isMetadataNamespace(key)) {
            ParsedMetadataToken parsed = parseNamespacedValue(value);
            if (parsed == null) {
                return null;
            }
            path = parsed.path;
            op = parsed.operator;
            expected = parsed.value;
        }

        if (path == null || path.isEmpty()) {
            return null;
        }

        final String finalPath = path;
        final String finalOp = op;
        final String finalExpected = unquote(expected);
        return card -> recordsFor(card).stream().anyMatch(record -> record.matches(finalPath, finalOp, finalExpected));
    }

    private static String metadataPath(String key, String operator, String value) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if ("tag".equals(normalized) || "tags".equals(normalized)) {
            return "tags";
        }
        if (normalized.startsWith("sf.")) {
            return "sf." + normalized.substring(3);
        }
        if (normalized.startsWith("scryfall.")) {
            return "sf." + normalized.substring("scryfall.".length());
        }
        if (normalized.startsWith("meta.")) {
            return normalized.substring(5);
        }
        if (!isMetadataNamespace(normalized) && (operator.equals(":") || operator.equals("=") || operator.equals("!")
                || operator.equals("!=") || operator.equals("<") || operator.equals("<=")
                || operator.equals(">") || operator.equals(">="))) {
            return normalized;
        }
        return null;
    }

    private static boolean isMetadataNamespace(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return "sf".equals(normalized) || "scryfall".equals(normalized) || "meta".equals(normalized);
    }

    private static ParsedMetadataToken parseNamespacedValue(String value) {
        for (String op : OPERATORS) {
            int idx = value.indexOf(op);
            if (idx > 0) {
                return new ParsedMetadataToken(value.substring(0, idx).trim(), op,
                        value.substring(idx + op.length()).trim());
            }
        }
        return null;
    }

    private static List<Record> recordsFor(PaperCard card) {
        ensureLoaded();
        List<Record> records = new ArrayList<>();
        addAll(records, byPrint.get(printKey(card.getEdition(), card.getCollectorNumber())));
        CardEdition edition = FModel.getMagicDb().getEditions().get(card.getEdition());
        if (edition != null) {
            addAll(records, byPrint.get(printKey(edition.getScryfallCode(), card.getCollectorNumber())));
        }
        addAll(records, byName.get(normalize(card.getName())));
        return records;
    }

    private static void addAll(List<Record> records, List<Record> toAdd) {
        if (toAdd != null) {
            records.addAll(toAdd);
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (config == null) {
            return;
        }
        String[] paths = configData.rewardQueryMetadata;
        if (paths == null || paths.length == 0) {
            paths = new String[]{DEFAULT_METADATA_PATH};
        }
        for (String path : paths) {
            load(path);
        }
    }

    private static void load(String path) {
        FileHandle handle = config.getFile(path);
        if (handle == null || !handle.exists()) {
            return;
        }
        JsonReader reader = new JsonReader();
        String[] lines = handle.readString("UTF-8").split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                index(new Record(reader.parse(line)));
            } catch (Exception e) {
                System.err.println("Ignoring malformed card metadata in " + path + ":" + (i + 1) + " - " + e.getMessage());
            }
        }
    }

    private static void index(Record record) {
        JsonValue match = record.match();
        String name = firstString(match, "forgeName", "name", "cardName");
        if (name != null) {
            byName.computeIfAbsent(normalize(name), k -> new ArrayList<>()).add(record);
        }

        String collector = firstString(match, "collectorNumber", "collector_number", "number");
        if (collector == null) {
            return;
        }

        String forgeEdition = firstString(match, "forgeEdition", "edition", "set");
        if (forgeEdition != null) {
            byPrint.computeIfAbsent(printKey(forgeEdition, collector), k -> new ArrayList<>()).add(record);
        }

        String scryfallSet = firstString(match, "scryfallSet", "scryfall_set", "scryfallCode", "scryfall_code");
        if (scryfallSet != null) {
            byPrint.computeIfAbsent(printKey(scryfallSet, collector), k -> new ArrayList<>()).add(record);
        }
    }

    private static String firstString(JsonValue root, String... paths) {
        for (String path : paths) {
            JsonValue value = valueAt(root, path);
            if (value != null && value.isValue()) {
                return value.asString();
            }
        }
        return null;
    }

    private static JsonValue valueAt(JsonValue root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }
        JsonValue current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.has(part)) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private static String printKey(String set, String collectorNumber) {
        return normalize(set) + "|" + normalizeCollector(collectorNumber);
    }

    private static String normalizeCollector(String collectorNumber) {
        return collectorNumber == null ? "" : collectorNumber.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String unquote(String text) {
        if (text == null) {
            return "";
        }
        text = text.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private record ParsedMetadataToken(String path, String operator, String value) {
    }

    private static final class Record {
        private final JsonValue root;
        private final Set<String> tags;

        private Record(JsonValue root) {
            this.root = root;
            this.tags = readTags(root);
        }

        private JsonValue match() {
            return root.has("match") ? root.get("match") : root;
        }

        private boolean matches(String path, String operator, String expected) {
            if ("tags".equals(path)) {
                boolean found = tags.contains(normalize(expected));
                return operator.equals("!=") ? !found : found;
            }
            JsonValue value = valueAt(root, path);
            if (value == null && !path.startsWith("sf.")) {
                value = valueAt(root, "sf." + path);
            }
            if (value == null && !path.startsWith("scryfall.")) {
                value = valueAt(root, "scryfall." + path);
            }
            return compare(value, operator, expected);
        }

        private static Set<String> readTags(JsonValue root) {
            Set<String> result = new HashSet<>();
            JsonValue tags = valueAt(root, "tags");
            if (tags != null) {
                if (tags.isArray()) {
                    for (JsonValue tag : tags) {
                        result.add(normalize(tag.asString()));
                    }
                } else if (tags.isValue()) {
                    result.add(normalize(tags.asString()));
                }
            }
            return result;
        }

        private static boolean compare(JsonValue value, String operator, String expected) {
            if (value == null) {
                return operator.equals("!=");
            }
            if (value.isArray()) {
                for (JsonValue child : value) {
                    if (compare(child, operator.equals("!=") ? "=" : operator, expected)) {
                        return !operator.equals("!=");
                    }
                }
                return operator.equals("!=");
            }
            if (value.isObject()) {
                return false;
            }

            Double actualNumber = number(value.asString());
            Double expectedNumber = number(expected);
            if (actualNumber != null && expectedNumber != null) {
                int cmp = Double.compare(actualNumber, expectedNumber);
                return switch (operator) {
                    case ":", "=", "!" -> cmp == 0;
                    case "!=" -> cmp != 0;
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    case ">" -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    default -> false;
                };
            }

            String actual = normalize(value.asString());
            String target = normalize(expected);
            return switch (operator) {
                case ":", "=" -> actual.contains(target);
                case "!" -> actual.equals(target);
                case "!=" -> !actual.contains(target);
                default -> false;
            };
        }

        private static Double number(String value) {
            try {
                return Double.parseDouble(value);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
