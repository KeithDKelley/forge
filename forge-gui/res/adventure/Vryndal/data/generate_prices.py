#!/usr/bin/env python3
"""
Streaming Scryfall all-cards.json → card_prices_usd.jsonl
Each line in the Scryfall bulk file is one JSON object (compact format).
Reads line-by-line; uses C-level json.loads for speed.
"""
import json
import sys

SKIP_LAYOUTS = {"token", "emblem", "art_series", "double_faced_token",
                "planar", "scheme", "vanguard", "reversible_card"}

def main():
    input_file  = sys.argv[1]
    output_file = sys.argv[2]

    min_prices   = {}   # name → minimum USD float
    no_price_set = set() # names seen but never with a USD price

    total = 0
    skipped = 0

    print(f"Streaming {input_file} ...", flush=True)
    with open(input_file, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line in ("[", "]"):
                continue
            # Strip trailing comma (all lines except last have one)
            if line.endswith(","):
                line = line[:-1]
            try:
                card = json.loads(line)
            except json.JSONDecodeError:
                continue

            total += 1
            if total % 100_000 == 0:
                print(f"  {total:,} processed ...", flush=True)

            if card.get("digital", False):
                skipped += 1
                continue
            layout = card.get("layout", "")
            if layout in SKIP_LAYOUTS:
                skipped += 1
                continue

            name = card.get("name")
            if not name:
                skipped += 1
                continue

            usd_str = card.get("prices", {}).get("usd")
            if usd_str is None:
                no_price_set.add(name)
                continue
            try:
                usd = float(usd_str)
            except (ValueError, TypeError):
                no_price_set.add(name)
                continue

            if name not in min_prices or usd < min_prices[name]:
                min_prices[name] = usd

    # Names with no USD price in ANY printing
    names_with_no_price = no_price_set - min_prices.keys()

    print(f"\nTotal entries read              : {total:,}")
    print(f"Skipped (digital/tokens/etc.)   : {skipped:,}")
    print(f"Unique names with price         : {len(min_prices):,}")
    print(f"Unique names with NO price ever : {len(names_with_no_price):,}")

    if names_with_no_price:
        sample = sorted(names_with_no_price)[:20]
        print(f"\nSample (first 20 no-price cards): {sample}")

    print(f"\nWriting {output_file} ...", flush=True)
    with open(output_file, "w", encoding="utf-8") as f:
        for name, price in sorted(min_prices.items()):
            f.write(json.dumps({"name": name, "usd": price}, ensure_ascii=False) + "\n")

    print(f"Wrote {len(min_prices):,} records.")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(f"Usage: python3 {sys.argv[0]} <all-cards.json> <output.jsonl>")
        sys.exit(1)
    main()
