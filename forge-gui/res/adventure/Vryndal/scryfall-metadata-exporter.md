# Scryfall Metadata Exporter Plan

Vryndal can already read side-loaded reward query metadata from:

```text
data/card_metadata.jsonl
```

The missing piece is a developer/content-author tool that populates that file from Scryfall bulk data. End users should not need to run this tool; the generated JSONL file should be committed and shipped with the normal Forge resource distribution.

## Goal

Create a Java utility that generates `forge-gui/res/adventure/Vryndal/data/card_metadata.jsonl` from Scryfall's `default_cards` bulk export.

The runtime engine should stay simple:

- Read the plane-local JSONL metadata file.
- Match rows to `PaperCard` objects.
- Allow reward `query` filters to use fields such as `tag:foo`, `sf:legalities.commander:legal`, and `sf:prices.usd<1`.

The exporter should handle downloading, matching, shaping, and sorting the metadata.

## Preferred Location

Prefer implementing the tool in Java, likely under `adventure-editor`, because this is content-authoring tooling rather than game runtime behavior.

Possible class location:

```text
adventure-editor/src/main/java/forge/adventure/editor/tools/ScryfallMetadataExporter.java
```

Alternative location if editor dependencies are awkward:

```text
forge-gui-mobile/src/forge/adventure/tools/ScryfallMetadataExporter.java
```

## Export Workflow

1. Fetch Scryfall bulk metadata from:

```text
https://api.scryfall.com/bulk-data
```

2. Find the `default_cards` entry and use its `download_uri`.
3. Download/cache the bulk JSON locally.
4. Build a lookup of Forge card prints.
5. Match Scryfall cards to Forge cards primarily by:

```text
scryfall set code + collector number
```

6. Use card name as a sanity check, not as the primary print identity.
7. Emit deterministic line-delimited JSON to:

```text
forge-gui/res/adventure/Vryndal/data/card_metadata.jsonl
```

8. Sort output by set, collector number, and name so source-control diffs stay readable.

## Row Shape

Recommended JSONL row:

```json
{"match":{"scryfallSet":"ogw","collectorNumber":"184","name":"Wastes"},"tags":["basic-wastes"],"sf":{"oracle_id":"...","games":["paper","mtgo"],"legalities":{"commander":"legal"},"prices":{"usd":0.25}}}
```

`match` fields are used by the runtime loader. `sf` should hold Scryfall-derived data. `tags` should hold Vryndal-authored/computed tags for reward design.

## Suggested Scryfall Fields

Include useful gameplay, legality, identity, print, and economy metadata:

- `id`
- `oracle_id`
- `name`
- `lang`
- `set`
- `collector_number`
- `layout`
- `games`
- `legalities`
- `keywords`
- `produced_mana`
- `promo`
- `promo_types`
- `prices`
- `rarity`
- `reprint`
- `reserved`
- `digital`
- `variation`
- `border_color`
- `frame`
- `security_stamp`
- `finishes`

Avoid copying bulky or unstable fields unless needed, especially image URI maps, purchase URIs, related URIs, and large nested payloads.

## Tags

The exporter should support Vryndal-local tags. These can be computed directly or loaded from a small authoring config.

Possible tag config:

```text
forge-gui/res/adventure/Vryndal/data/card_metadata_tags.json
```

Examples:

```json
{
  "tags": [
    { "tag": "basic-wastes", "query": "name!Wastes" },
    { "tag": "vryndal-reward-ok", "query": "-sf:digital:true -sf:promo_types:alchemy" },
    { "tag": "cheap-single", "query": "sf:prices.usd<1" }
  ]
}
```

This may require the exporter to reuse the same query parser after loading Scryfall data, or it can start with simpler hard-coded/computed tag rules.

## Packaging Note

The generated `card_metadata.jsonl` should be committed under `forge-gui/res/adventure/Vryndal/data/`.

The Forge installer/distribution copies `forge-gui/res/**` into packaged builds, excluding only `res/cardsfolder/**`. That means a populated Vryndal metadata file should ship with the plane resources automatically. It is not expected that end users run the exporter.

## Open Questions

- Should the exporter preserve all matched Scryfall fields by default, or use an allowlist?
- Should unmatched Scryfall cards be ignored silently, summarized, or written to a report?
- Should Forge cards without a Scryfall match be reported as warnings?
- Where should the Scryfall cache live, and should it be gitignored?
- Should Vryndal tags be purely authored, purely computed, or both?
