package forge.itemmanager;

import forge.card.CardRules;
import forge.card.CardRulesPredicates;
import forge.card.CardSplitType;
import forge.card.CardRulesPredicates.LeafNumber;
import forge.card.MagicColor;
import forge.item.PaperCard;
import forge.item.PaperCardPredicates;
import forge.itemmanager.advancedsearchparsers.InParser;
import forge.itemmanager.advancedsearchparsers.RarityParser;
import forge.util.ComparableOp;
import forge.util.PredicateString.StringOp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public abstract class AdvancedSearchParser {
    public interface PaperCardTokenParser {
        Predicate<PaperCard> parse(String key, String operator, String value);
    }

    private static final List<PaperCardTokenParser> paperCardTokenParsers = new CopyOnWriteArrayList<>();

    public static void registerPaperCardTokenParser(PaperCardTokenParser parser) {
        if (parser != null && !paperCardTokenParsers.contains(parser)) {
            paperCardTokenParsers.add(parser);
        }
    }

    public static Predicate<CardRules> parseAdvancedRulesToken(String token) {
        boolean negated = false;
        if (token.startsWith("-")) {
            token = token.substring(1).trim();
            negated = true;
        }

        String[] operators = {"!=", "<=", ">=", "=", "<", ">", ":", "!"};
        int index = -1;
        String opUsed = null;
        for (String op : operators) {
            int idx = token.indexOf(op);
            if (idx >= 0) {
                index = idx;
                opUsed = op;
                break;
            }
        }
        if (index < 0) {
            return null;
        }

        ComparableOp op = getComparableOp(opUsed);
        if (op == null) {
            return null;
        }

        String key = token.substring(0, index).trim().toLowerCase();
        String valueStr = unquote(token.substring(index + opUsed.length()).trim()).toLowerCase();
        boolean creatureOnly = false;

        Predicate<CardRules> predicate = null;
        switch (key) {
            case "o":
            case "oracle":
                switch (opUsed) {
                    case ":":
                    case "=":
                        predicate = CardRulesPredicates.rules(StringOp.CONTAINS_IC, valueStr);
                        break;
                }
                break;

            case "power":
            case "pow":
                if (valueStr.matches("\\d+")) {
                    try {
                        int power = Integer.parseInt(valueStr);
                        creatureOnly = true;
                        predicate = CardRulesPredicates.power(op, power);
                    }
                    catch (NumberFormatException ignored) {}
                } else {
                    switch(valueStr) {
                        case "toughness":
                        case "tou":
                            creatureOnly = true;
                            predicate = c -> {
                                int toughness = c.getIntToughness();
                                return new LeafNumber(LeafNumber.CardField.POWER, op, toughness).test(c);
                            };
                            break;
                    }
                }
                break;

            case "toughness":
            case "tou":
                if (valueStr.matches("\\d+")) {
                    try {
                        int toughness = Integer.parseInt(valueStr);
                        creatureOnly = true;
                        predicate = CardRulesPredicates.toughness(op, toughness);
                    }
                    catch (NumberFormatException ignored) {}
                } else {
                    switch(valueStr) {
                        case "power":
                        case "pow":
                            creatureOnly = true;
                            predicate = c -> {
                                int power = c.getIntPower();
                                return new LeafNumber(LeafNumber.CardField.TOUGHNESS, op, power).test(c);
                            };
                            break;
                    }
                }
                break;

            case "pt":
            case "powtou":
                if (valueStr.matches("\\d+")) {
                    try {
                        int power = Integer.parseInt(valueStr);
                        creatureOnly = true;
                        predicate = CardRulesPredicates.pt(op, power);
                    }
                    catch (NumberFormatException ignored) {}
                }
                break;

            case "loy":
            case "loyalty":
                if (valueStr.matches("\\d+")) {
                    try {
                        int loyalty = Integer.parseInt(valueStr);
                        predicate = CardRulesPredicates.loyalty(op, loyalty);
                    }
                    catch (NumberFormatException ignored) {}
                }
                break;

            case "cmc":
            case "mv":
            case "manavalue":
                try {
                    int cmcValue = Integer.parseInt(valueStr);
                    predicate = CardRulesPredicates.cmc(op, cmcValue);
                }
                catch (NumberFormatException ignored) {}
                break;

            case "t":
            case "type":
                switch (opUsed) {
                    case ":":
                    case "=": 
                        predicate = CardRulesPredicates.joinedType(StringOp.CONTAINS_IC, valueStr);
                        break;

                    case "!=":
                        predicate = CardRulesPredicates.joinedType(StringOp.CONTAINS_IC, valueStr).negate();
                        break;
                }
                break;

            case "kw":
            case "keyword":
                switch (opUsed) {
                    case ":":
                    case "=":
                        predicate = CardRulesPredicates.hasKeyword(valueStr);
                        break;

                    case "!=":
                        predicate = CardRulesPredicates.hasKeyword(valueStr).negate();
                        break;
                }
                break;

            case "c":
            case "color":
                predicate = parseColorToken(opUsed, valueStr, false);
                break;

            case "id":
            case "identity":
            case "coloridentity":
            case "color_identity":
                predicate = parseColorToken(opUsed, valueStr, true);
                break;

            case "is":
                if (opUsed.equals(":")) {
                    switch(valueStr) {
                        case "meld":
                            predicate = CardRulesPredicates.isSplitType(CardSplitType.Meld);
                            break;
                        
                        case "flip":
                            predicate = CardRulesPredicates.isSplitType(CardSplitType.Flip);
                            break;

                        case "split":
                            predicate = CardRulesPredicates.isSplitType(CardSplitType.Split);
                            break;

                        case "modal":
                            predicate = CardRulesPredicates.isSplitType(CardSplitType.Modal);
                            break;

                        case "transform":
                            predicate = CardRulesPredicates.isSplitType(CardSplitType.Transform);
                            break;

                        case "vanilla":
                            predicate = CardRulesPredicates.isVanilla();
                            break;

                        case "custom":
                            predicate = card -> card.isCustom();
                            break;
                    }
                }
                break;
        }

        if (predicate == null) {
            return null;
        }

        if (negated) {
            predicate = predicate.negate();
        }

        if (creatureOnly) {
            predicate = CardRulesPredicates.IS_CREATURE.and(predicate);
        }

        return predicate;
    }

    public static Predicate<PaperCard> parseAdvancedPaperCardToken(String token) {
        boolean negated = false;
        if (token.startsWith("-")) {
            token = token.substring(1).trim();
            negated = true;
        }

        String[] operators = {"!=", "<=", ">=", "=", "<", ">", ":", "!"};
        int index = -1;
        String opUsed = null;
        for (String op : operators) {
            int idx = token.indexOf(op);
            if (idx >= 0) {
                index = idx;
                opUsed = op;
                break;
            }
        }
        if (index < 0) {
            return null;
        }

        String key = token.substring(0, index).trim().toLowerCase();
        String valueStr = unquote(token.substring(index + opUsed.length()).trim()).toLowerCase();

        Predicate<PaperCard> predicate = null;
        switch (key) {
            case "set":
            case "s":
            case "edition":
            case "e":
                switch (opUsed) {
                    case ":":
                    case "=":
                        predicate = PaperCardPredicates.printedInSet(valueStr);
                        break;
                    
                    case "!=":
                        predicate = PaperCardPredicates.printedInSet(valueStr).negate();
                        break;
                }
                break;

            case "in":
                switch (opUsed) {
                    case ":":
                    case "=":
                        predicate = InParser.handle(valueStr);
                        break;
                    
                    case "!=":
                        predicate = InParser.handle(valueStr).negate();
                        break;
                }
                break;

            case "r":
            case "rarity":
                switch (opUsed) {
                    case "!":
                    case ":":
                    case "=":
                        predicate = RarityParser.handleExact(valueStr);
                        break;

                    case "!=":
                        predicate = RarityParser.handleExact(valueStr);
                        if (predicate != null) {
                            predicate = predicate.negate();
                        }
                        break;

                    case ">":
                        predicate = RarityParser.handleGreater(valueStr);
                        break;

                    case ">=":
                        predicate = RarityParser.handleGreaterOrEqual(valueStr);
                        break;

                    case "<":
                        predicate = RarityParser.handleLess(valueStr);
                        break;

                    case "<=":
                        predicate = RarityParser.handleLessOrEqual(valueStr);
                        break;
                }
                break;



            case "name":
                switch(opUsed) {
                    case "!":
                        predicate = PaperCardPredicates.searchableName(StringOp.EQUALS_IC, valueStr);
                        break;

                    case "!=":
                        predicate = PaperCardPredicates.searchableName(StringOp.EQUALS_IC, valueStr).negate();
                        break;

                    case "=":
                    case ":":
                        predicate = PaperCardPredicates.searchableName(StringOp.CONTAINS_IC, valueStr);
                        break;
                }
                break;

            case "is":
                if (opUsed.equals(":")) {
                    switch(valueStr) {
                        case "foil":
                            predicate = PaperCardPredicates.isFoil(true);
                            break;

                        case "nonfoil":
                            predicate = PaperCardPredicates.isFoil(false);
                            break;
                    }
                }
                break;
        }

        if (predicate == null) {
            for (PaperCardTokenParser parser : paperCardTokenParsers) {
                predicate = parser.parse(key, opUsed, valueStr);
                if (predicate != null) {
                    break;
                }
            }
            if (predicate == null) {
                return null;
            }
        }

        if (negated) {
            predicate = predicate.negate();
        }

        return predicate;
    }

    static String unquote(String s) {
        if (s != null && s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static ComparableOp getComparableOp(String opUsed) {
        ComparableOp op = null;
        switch (opUsed) {
            case "!":
            case ":":
            case "=":  op = ComparableOp.EQUALS; break;
            case "!=": op = ComparableOp.NOT_EQUALS; break;
            case ">=": op = ComparableOp.GT_OR_EQUAL; break;
            case ">":  op = ComparableOp.GREATER_THAN; break;
            case "<=": op = ComparableOp.LT_OR_EQUAL; break;
            case "<":  op = ComparableOp.LESS_THAN; break;
        }
        return op;
    }

    private static Predicate<CardRules> parseColorToken(String opUsed, String valueStr, boolean colorIdentity) {
        if (valueStr.matches("\\d+")) {
            try {
                byte colorCnt = Byte.parseByte(valueStr);

                return switch (opUsed) {
                    case ":", "=" -> colorIdentity
                            ? card -> card.getColorIdentity().countColors() == colorCnt
                            : CardRulesPredicates.hasCntColors(colorCnt);
                    case "!", "!=" -> colorIdentity
                            ? card -> card.getColorIdentity().countColors() != colorCnt
                            : CardRulesPredicates.hasCntColors(colorCnt).negate();
                    case ">=" -> colorIdentity
                            ? card -> card.getColorIdentity().countColors() >= colorCnt
                            : CardRulesPredicates.hasAtLeastCntColors(colorCnt);
                    case ">" -> colorIdentity
                            ? card -> card.getColorIdentity().countColors() > colorCnt
                            : CardRulesPredicates.hasMoreCntColors(colorCnt);
                    case "<=" -> colorIdentity
                            ? card -> card.getColorIdentity().countColors() <= colorCnt
                            : CardRulesPredicates.hasAtMostCntColors(colorCnt);
                    case "<" -> colorIdentity
                            ? card -> card.getColorIdentity().countColors() < colorCnt
                            : CardRulesPredicates.hasLessCntColors(colorCnt);
                    default -> null;
                };
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }

        switch(valueStr) {
            case "c":
            case "colorless":
                return switch (opUsed) {
                    case ":", "=", "<=" -> colorIdentity
                            ? card -> card.getColorIdentity().isColorless()
                            : CardRulesPredicates.IS_COLORLESS;
                    case "!=", ">" -> colorIdentity
                            ? card -> !card.getColorIdentity().isColorless()
                            : CardRulesPredicates.IS_COLORLESS.negate();
                    default -> null;
                };

            case "m":
            case "multi":
            case "multicolor":
                return switch (opUsed) {
                    case "!", ":", "=", ">=", ">" -> colorIdentity
                            ? card -> card.getColorIdentity().isMulticolor()
                            : CardRulesPredicates.IS_MULTICOLOR;
                    case "!=" -> colorIdentity
                            ? card -> !card.getColorIdentity().isMulticolor()
                            : CardRulesPredicates.IS_MONOCOLOR;
                    case "<" -> colorIdentity
                            ? card -> card.getColorIdentity().countColors() <= 1
                            : CardRulesPredicates.hasAtMostCntColors((byte)1);
                    default -> null;
                };

            default:
                byte givenMask = getColorMaskFromString(valueStr);
                return switch (opUsed) {
                    case ":", ">=" -> card -> {
                        byte cardMask = colorIdentity ? card.getColorIdentity().getColor() : card.getColor().getColor();
                        return (cardMask & givenMask) == givenMask;
                    };
                    case "!", "=" -> card -> {
                        byte cardMask = colorIdentity ? card.getColorIdentity().getColor() : card.getColor().getColor();
                        return cardMask == givenMask;
                    };
                    case "!=" -> card -> {
                        byte cardMask = colorIdentity ? card.getColorIdentity().getColor() : card.getColor().getColor();
                        return cardMask != givenMask;
                    };
                    case ">" -> card -> {
                        byte cardMask = colorIdentity ? card.getColorIdentity().getColor() : card.getColor().getColor();
                        return (cardMask & givenMask) == givenMask && (cardMask & ~givenMask) != 0;
                    };
                    case "<=" -> card -> {
                        byte cardMask = colorIdentity ? card.getColorIdentity().getColor() : card.getColor().getColor();
                        return (cardMask & ~givenMask) == 0;
                    };
                    case "<" -> card -> {
                        byte cardMask = colorIdentity ? card.getColorIdentity().getColor() : card.getColor().getColor();
                        return (cardMask & ~givenMask) == 0 && cardMask != givenMask;
                    };
                    default -> null;
                };
        }
    }

    private static byte getColorMaskFromString(String valueStr) {
        valueStr = valueStr.toLowerCase();

        switch (valueStr) {
            case "white": return MagicColor.WHITE;
            case "blue": return MagicColor.BLUE;
            case "black": return MagicColor.BLACK;
            case "red": return MagicColor.RED;
            case "green": return MagicColor.GREEN;

            // Ravnica guilds + Strixhaven colleges
            case "azorius": return MagicColor.WHITE | MagicColor.BLUE;
            case "dimir": return MagicColor.BLUE | MagicColor.BLACK;
            case "rakdos": return MagicColor.BLACK | MagicColor.RED;
            case "gruul": return MagicColor.RED | MagicColor.GREEN;
            case "selesnya": return MagicColor.WHITE | MagicColor.GREEN;
            case "silverquill":
            case "orzhov": return MagicColor.WHITE | MagicColor.BLACK;
            case "prismari":
            case "izzet": return MagicColor.BLUE | MagicColor.RED;
            case "witherbloom":
            case "golgari": return MagicColor.BLACK | MagicColor.GREEN;
            case "lorehold":
            case "boros": return MagicColor.WHITE | MagicColor.RED;
            case "quandrix":
            case "simic": return MagicColor.BLUE | MagicColor.GREEN;

            // Alara Shards + New Capenna Families
            case "brokers":
            case "bant": return MagicColor.WHITE | MagicColor.BLUE | MagicColor.GREEN;
            case "obscura":
            case "esper": return MagicColor.WHITE | MagicColor.BLUE | MagicColor.BLACK;
            case "maestros":
            case "grixis": return MagicColor.BLUE | MagicColor.BLACK | MagicColor.RED;
            case "riveteers":
            case "jund": return MagicColor.BLACK | MagicColor.RED | MagicColor.GREEN;
            case "cabaretti":
            case "naya": return MagicColor.WHITE | MagicColor.RED | MagicColor.GREEN;

            // Tarkir Clans + Ikoria Triomes
            case "indatha":
            case "abzan": return MagicColor.WHITE | MagicColor.BLACK | MagicColor.GREEN;
            case "raugrin":
            case "jeskai": return MagicColor.WHITE | MagicColor.BLUE | MagicColor.RED;
            case "zagoth":
            case "sultai": return MagicColor.BLUE | MagicColor.BLACK | MagicColor.GREEN;
            case "savai":
            case "mardu": return MagicColor.WHITE | MagicColor.BLACK | MagicColor.RED;
            case "ketria":
            case "temur": return MagicColor.BLUE | MagicColor.RED | MagicColor.GREEN;

            // Four-color Identities
            case "chaos": return MagicColor.BLACK | MagicColor.GREEN | MagicColor.RED | MagicColor.BLUE;
            case "aggression": return MagicColor.BLACK | MagicColor.GREEN | MagicColor.RED | MagicColor.WHITE;
            case "altruism": return MagicColor.GREEN | MagicColor.RED | MagicColor.BLUE | MagicColor.WHITE;
            case "growth": return MagicColor.BLACK | MagicColor.GREEN | MagicColor.BLUE | MagicColor.WHITE;
            case "artifice": return MagicColor.BLACK | MagicColor.RED | MagicColor.BLUE | MagicColor.WHITE;

            case "all": return MagicColor.ALL_COLORS;

            default:
                byte mask = 0;
                for (char c : valueStr.toCharArray()) {
                    byte color = MagicColor.fromName(c);
                    if (color == 0) {
                        continue;
                    }
                    mask |= color;
                }
                return mask;
        }
    }
}
