# Vryndal — Editing Guide

## Directory Layout

```
forge-gui/res/adventure/Vryndal/
├── config.json          # Global plane settings (difficulty, card restrictions, screen size)
├── edit-vryndal.md      # This file
├── world/               # World-map data
│   ├── world.json       # Map dimensions, biome references, road tileset
│   ├── quests.json      # Quest templates and dialog trees
│   ├── shops.json       # Shop definitions and card reward filters
│   └── town_names_*.txt # Town name pools, one per color (black/blue/green/red/white/waste)
└── maps/                # Tiled project root
    ├── map/             # Your custom .tmx map files go here
    ├── obj/             # Plane-local copies of Forge object templates (.tx)
    └── tileset/         # Plane-local copies of all tilesets (.tsx + images)
```

---

## config.json

Global settings that apply to every session on this plane.

| Field | Type | Notes |
|---|---|---|
| `screenWidth` / `screenHeight` | int | Render resolution (default 480×270) |
| `playerBaseSpeed` | int | Player movement speed in pixels/sec |
| `minDeckSize` | int | Minimum deck size for the player |
| `maxNumberOfDecks` | int | How many decks the player may carry |
| `colorIds` / `colorIdNames` | string[] | Color identity list and display names |
| `restrictedCards` | string[] | Cards banned from appearing in this plane |
| `restrictedEditions` | string[] | Set codes whose cards are excluded entirely |
| `restrictedEvents` | string[] | Set codes excluded from draft/sealed events |
| `usePriceListPrices` | bool | Use the global price list for shops |
| `enableRewardQueries` | bool | Enables Scryfall-like `query` filters in reward data |
| `rewardQueryMetadata` | string[] | Plane-local JSONL metadata overlays for `query` filters |
| `starterEditions` / `starterEditionNames` | string[] | Sets available for jumpstart-style starts |

### Difficulties

Each entry in the `difficulties` array is a selectable difficulty. Fields:

| Field | Notes |
|---|---|
| `name` | Display name (Easy / Normal / Hard / Insane) |
| `startingLife` | Player starting life total |
| `startingShards` | Starting mana shards |
| `startingMoney` | Starting gold |
| `startingDifficulty` | Set `true` on the default difficulty |
| `enemyLifeFactor` | Multiplier on enemy life totals (1.0 = normal) |
| `rewardMaxFactor` | Multiplier on reward card quality ceiling |
| `spawnRank` | Enemy rank pool to draw from (0 = easiest) |
| `goldLoss` | Fraction of gold lost on defeat |
| `lifeLoss` | Fraction of life lost on defeat |
| `sellFactor` | Fraction of card value returned when selling |
| `shardSellRatio` | Fraction of shard value returned when selling |
| `starterDecks` | Per-color starter deck paths (JSON format) |
| `constructedStarterDecks` | Per-color pre-built `.dck` starter decks |
| `pileDecks` | Per-color pile-draft starter decks |
| `commanderDecks` | Per-color commander starter decks |
| `startItems` | Item names given to the player at game start |

---

## world/world.json

Controls the procedurally generated overworld map.

| Field | Notes |
|---|---|
| `width` / `height` | World map size in tiles (default 700×700) |
| `playerStartPosX/Y` | Fractional position (0.0–1.0) where the player spawns |
| `noiseZoomBiome` | Perlin noise zoom for biome generation; higher = larger biome patches |
| `miniMapTileSize` | Pixel size of each tile on the minimap |
| `tileSize` | World tile size in pixels |
| `roadTileset` | Atlas, tileset name, and hex color for roads |
| `maxRoadDistance` | Maximum tile distance a road will extend from a town |
| `biomesNames` | Ordered list of biome definition files to load |
| `biomesSprites` | Path to the sprite sheet used for map decoration |

Biome files live in `world/biomes/` (inherited from common; copy here to override).

---

## world/quests.json

An array of quest objects. Each quest has:

| Field | Notes |
|---|---|
| `id` | Unique integer ID |
| `isTemplate` | `true` means the quest can be issued multiple times with randomized enemies |
| `name` | Quest display name |
| `description` | Short description; use `$(enemy_1)` as a placeholder for the randomized enemy |
| `offerDialog` | Root dialog node (see Dialog Tree below) |

### Dialog Tree Structure

Each dialog node has:
- `text` — the spoken line
- `options` — array of player reply options, each with:
  - `name` — the option label shown to the player
  - `text` — (optional) NPC response when this option is chosen
  - `options` — (optional) nested follow-up choices
  - `action` — (optional) array of effect objects triggered on selection

### Dialog Actions

| Action key | Effect |
|---|---|
| `issueQuest` | Issues the quest with the given ID string |
| `advanceQuestFlag` | Increments the named quest flag |
| `setQuestFlag` | Sets a quest flag to a specific value (`{ "key": "flagName" }`) |
| `advanceMapFlag` | Increments the named map flag |
| `setMapFlag` | Sets a map flag to a specific value |
| `removeItem` | Removes an item from player inventory |
| `setColorIdentity` | Forces the player's color identity |
| `POIReference` | Links to a point-of-interest on the map |

---

## world/shops.json

An array of shop definitions. Each shop has:

| Field | Notes |
|---|---|
| `name` | Internal identifier used in map object properties |
| `description` | Display name shown in-game |
| `spriteAtlas` | Atlas file for the shop sprite (relative to plane root) |
| `sprite` | Sprite name within the atlas |
| `overlaySprite` | Color overlay sprite name |
| `rewards` | Array of card reward pool definitions (see below) |

### Reward Pool Entry

| Field | Notes |
|---|---|
| `count` | Number of cards drawn from this pool |
| `query` | Scryfall-like search expression, enabled by `enableRewardQueries` |
| `cardText` | Regex matched against oracle text to filter eligible cards |
| `colors` | (optional) Array of color names to further restrict the pool |

`query` also supports Vryndal metadata from `data/card_metadata.jsonl`, including `tag:foo`, `sf:path.to.value`, and numeric comparisons like `sf:prices.usd<1`.

---

## world/town_names_*.txt

One town name per line. Forge randomly picks from this file when generating towns of the matching color identity:

| File | Color |
|---|---|
| `town_names_white.txt` | White-aligned towns |
| `town_names_blue.txt` | Blue-aligned towns |
| `town_names_black.txt` | Black-aligned towns |
| `town_names_red.txt` | Red-aligned towns |
| `town_names_green.txt` | Green-aligned towns |
| `town_names_waste.txt` | Colorless / wasteland towns |

---

## maps/ — Tiled Project

### Tiled Project Setup

1. Open Tiled and create a new project saved inside `maps/`.
2. Add `maps/obj/` as a project folder — this exposes all Forge object templates.
3. Add `maps/tileset/` as a project folder — this exposes all tilesets.
4. Save new `.tmx` map files under `maps/map/`.

### Object Templates (maps/obj/*.tx)

These define interactive elements placed on maps. Key objects:

| File | Purpose |
|---|---|
| `enemy.tx` | Enemy encounter spawn point |
| `portal.tx` | Transition between maps |
| `entry.tx` / `entry_up/down/left/right.tx` | Player entry points from portals |
| `exit.tx` | Map exit trigger |
| `door_up/down/left/right.tx` | Directional doors |
| `gate.tx` | Locked gate |
| `shop.tx` | Standard shop |
| `RotatingShop.tx` | Shop with rotating inventory |
| `inn.tx` | Rest/heal location |
| `quest.tx` | Quest giver NPC |
| `treasure.tx` | Treasure chest |
| `gold.tx` | Gold pickup |
| `item.tx` | Item pickup |
| `booster.tx` | Booster pack reward |
| `scroll.tx` | Scroll pickup |
| `manashards.tx` | Mana shard pickup |
| `shardtrader.tx` | NPC that trades shards |
| `spellsmith.tx` | NPC that upgrades cards |
| `arena.tx` | PvE arena encounter |
| `waypoint.tx` | Fast-travel waypoint |
| `town_center.tx` | Town hub anchor |
| `dialog.tx` | Pure dialog NPC |
| `collision.tx` | Invisible collision block |
| `block.tx` | Generic blocking tile |

**Important:** Edit only the copies in `maps/obj/`. Never modify the originals in `common/maps/obj/` or changes will affect every plane.

### Tilesets (maps/tileset/)

| Tileset | Use case |
|---|---|
| `main.tsx` | Primary overworld terrain and collision |
| `main-nocollide.tsx` | Overworld decoration layer (no collision) |
| `buildings.tsx` | Town and building sprites |
| `buildings-nocollide.tsx` | Building decoration overlays |
| `buildingsbosses.tsx` | Boss location building sprites |
| `large_buildings.tsx` | Oversized landmark buildings |
| `desertbuildingtiles.tsx` | Desert/arid region buildings |
| `rivers.tsx` | River terrain with collision |
| `rivers-nocolide.tsx` | River decoration overlays |
| `FarmFood.tsx` | Farm and food decoration tiles |
| `chips.tsx` | Small chip/detail tiles |
| `dungeon.tsx` | Standard dungeon interior |
| `dungeon-nocollide.tsx` | Dungeon decoration layer |
| `dungeon/DarkAbbeyTiles.tsx` | Dark abbey interior variant |
| `dungeon/animatedtiles.tsx` | Animated dungeon tiles (torches, water) |
| `dungeon/dungeon_bis.tsx` | Alternate dungeon palette |
| `phyrexia/phyrexiantiles.tsx` | Phyrexian interior terrain |
| `phyrexia/Copperhost_Tiles.tsx` | Copperhost layer tiles |
| `phyrexia/DrossPitTiles.tsx` | Dross Pit layer tiles |
| `phyrexia/FurnaceTiles.tsx` | Furnace layer tiles |
| `phyrexia/GitaxianTilesheet.tsx` | Gitaxian detail tiles |

**Important:** Edit only the copies in `maps/tileset/`. Never modify the originals in `common/maps/tileset/`.

---

## Key Rules

- **Always modify Vryndal-local files** — the copies under `Vryndal/maps/obj/` and `Vryndal/maps/tileset/`. The originals in `common/` are shared across all planes.
- **Tiled `.tmx` map files** belong in `maps/map/`. Subdirectories are fine for organization.
- **Biome and sprite overrides** go in a `world/` subdirectory structure mirroring common; copy only the files you intend to change.
- **Shop names in `shops.json`** must match the `name` property set on `shop.tx` / `RotatingShop.tx` objects in your Tiled maps.
- **Quest IDs** must be unique integers within `quests.json`.
