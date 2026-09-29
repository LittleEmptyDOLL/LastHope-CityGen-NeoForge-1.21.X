#!/usr/bin/env python3
"""Build the intentionally simple NBT fixtures used to exercise city lots.

Run this file to regenerate the checked-in resources, or pass --check to compare
the byte-for-byte output without changing anything. Python 3, no dependencies.
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
from dataclasses import dataclass
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/lasthopecitygen"
NAMESPACE = "lasthopecitygen"
DATA_VERSION = 3955  # Minecraft 1.21.1


@dataclass(frozen=True)
class Building:
    name: str
    district: str
    width: int
    depth: int
    height: int
    weight: int
    tags: tuple[str, ...]
    style: str
    size: str = "small"


BUILDINGS = (
    Building("abandoned_cabin", "residential", 7, 7, 5, 8,
             ("house", "wooden", "ruined"), "cabin"),
    Building("row_house", "residential", 8, 10, 6, 9,
             ("house", "urban", "ruined"), "row_house"),
    Building("apartment", "residential", 11, 11, 9, 3,
             ("apartments", "urban", "ruined"), "apartment"),
    Building("apartment_block", "residential", 22, 12, 9, 2,
             ("apartments", "medium", "ruined"), "apartment", "medium"),
    Building("workshop", "industrial", 9, 8, 6, 8,
             ("workshop", "industrial", "ruined"), "workshop"),
    Building("warehouse", "industrial", 12, 10, 7, 6,
             ("warehouse", "storage", "ruined"), "warehouse"),
    Building("substation", "industrial", 7, 7, 5, 4,
             ("utility", "power", "ruined"), "substation"),
    Building("clinic", "civic", 10, 9, 6, 7,
             ("medical", "clinic", "ruined"), "clinic"),
    Building("police_post", "civic", 8, 8, 6, 6,
             ("police", "security", "ruined"), "police_post"),
    Building("fire_station", "civic", 12, 10, 7, 4,
             ("fire_station", "service", "ruined"), "fire_station"),
)


STYLES = {
    # floor, wall, frame, roof, window, worn wall, trim
    "cabin": ("spruce_planks", "spruce_planks", "stripped_spruce_log",
              "dark_oak_planks", "glass", "mossy_cobblestone", "cobblestone"),
    "row_house": ("oak_planks", "bricks", "stone_bricks", "deepslate_tiles",
                  "glass", "cracked_stone_bricks", "smooth_stone"),
    "apartment": ("stone_bricks", "bricks", "polished_andesite", "deepslate_tiles",
                  "light_gray_stained_glass", "cracked_stone_bricks", "stone_bricks"),
    "workshop": ("smooth_stone", "stone_bricks", "iron_block", "oxidized_copper",
                 "glass", "cracked_stone_bricks", "polished_andesite"),
    "warehouse": ("smooth_stone", "light_gray_concrete", "gray_concrete",
                  "gray_concrete", "glass", "mossy_stone_bricks", "stone_bricks"),
    "substation": ("smooth_stone", "iron_block", "copper_block", "stone_slab",
                   "iron_bars", "oxidized_copper", "stone_bricks"),
    "clinic": ("smooth_stone", "white_concrete", "light_gray_concrete",
               "gray_concrete", "cyan_stained_glass", "calcite", "red_concrete"),
    "police_post": ("stone_bricks", "stone_bricks", "blue_concrete",
                    "deepslate_tiles", "glass", "cracked_stone_bricks", "iron_block"),
    "fire_station": ("smooth_stone", "bricks", "red_concrete", "gray_concrete",
                     "glass", "cracked_stone_bricks", "smooth_stone"),
}


def blocks_for(building: Building) -> dict[tuple[int, int, int], str]:
    w, d, h = building.width, building.depth, building.height
    floor, wall, frame, roof, glass, worn, trim = STYLES[building.style]
    # Write air explicitly, so /citygen place also clears the room when tested.
    blocks = {(x, y, z): "air" for x in range(w) for y in range(h) for z in range(d)}

    def put(x: int, y: int, z: int, block: str) -> None:
        if not (0 <= x < w and 0 <= y < h and 0 <= z < d):
            raise ValueError((building.name, x, y, z))
        blocks[x, y, z] = block

    for x in range(w):
        for z in range(d):
            put(x, 0, z, floor)
            if 0 < x < w - 1 and 0 < z < d - 1:
                put(x, h - 1, z, roof)
    for y in range(1, h):
        for x in range(w):
            for z in (0, d - 1):
                if y == h - 1 or x in (0, w - 1) or x % 4 == 0:
                    block = frame
                elif y in (2, 6) and x % 4 in (1, 2):
                    block = glass
                else:
                    block = worn if (x * 5 + y * 3 + z) % 13 == 0 else wall
                put(x, y, z, block)
        for z in range(1, d - 1):
            for x in (0, w - 1):
                block = frame if y == h - 1 or z % 4 == 0 else (
                    glass if y in (2, 6) and z % 4 in (1, 2) else wall)
                put(x, y, z, block)

    # The entrance always faces north. The city generator can rotate the NBT.
    center = (w - 1) // 2
    entrance_width = 3 if building.style in ("warehouse", "fire_station") else 2
    first = center - (entrance_width - 1) // 2
    for x in range(first, first + entrance_width):
        for y in range(1, min(h - 1, 4 if entrance_width == 3 else 3)):
            put(x, y, 0, "air")
        put(x, 0, 0, trim)
    for x in range(first - 1, first + entrance_width + 1):
        put(x, min(h - 2, 3 if entrance_width == 3 else 2), 0,
            "red_concrete" if building.style == "fire_station" else frame)
    # Restore the opening after adding its lintel (two clear blocks minimum).
    for x in range(first, first + entrance_width):
        for y in (1, 2):
            put(x, y, 0, "air")

    # Each archetype has an unmistakable feature, visible both from the street
    # and inside the building; fixtures use neutral, unfilled containers.
    if building.style == "cabin":
        put(1, 1, d - 2, "barrel")
        put(w - 2, 1, d - 2, "crafting_table")
        put(1, h - 1, 1, "air")
    elif building.style == "row_house":
        for x in range(1, w - 1):
            put(x, 1, d - 2, "oak_planks")
        put(1, 2, d - 2, "barrel")
        put(w - 2, h - 1, d - 2, "air")
    elif building.style == "apartment":
        for x in range(1, w - 1):
            for z in range(1, d - 1):
                if x != w - 2 or z != d - 2:
                    put(x, 4, z, "stone_bricks")
        for y in range(1, 5):
            put(w - 2, y, d - 2, "ladder")
        put(2, h - 1, d - 2, "air")
        put(3, h - 1, d - 2, "air")
    elif building.style == "workshop":
        put(2, 1, d - 2, "furnace")
        put(3, 1, d - 2, "crafting_table")
        put(w - 2, 1, d - 2, "barrel")
        for z in range(2, d - 2):
            put(w - 2, 1, z, "iron_block")
    elif building.style == "warehouse":
        for x in (1, w - 2):
            for z in (3, 5, 7):
                put(x, 1, z, "barrel")
                put(x, 2, z, "barrel")
        put(w - 3, h - 1, d - 2, "air")
    elif building.style == "substation":
        for x in (2, w - 3):
            for z in (3, d - 2):
                put(x, 1, z, "copper_block")
                put(x, 2, z, "redstone_lamp")
        for x in range(1, w - 1):
            put(x, h - 1, 2, "air")
    elif building.style == "clinic":
        for x in (2, w - 3):
            put(x, 1, d - 2, "white_concrete")
            put(x, 2, d - 2, "barrel")
        for x in range(w // 2 - 1, w // 2 + 2):
            put(x, h - 1, 0, "red_concrete")
        for z in range(1, 4):
            put(w // 2, h - 1, z, "red_concrete")
    elif building.style == "police_post":
        for x in range(1, w - 1):
            put(x, 1, d - 2, "iron_bars")
        put(2, 1, 2, "barrel")
        put(w - 2, h - 1, 1, "blue_concrete")
    elif building.style == "fire_station":
        for z in range(1, d - 1):
            put(1, 0, z, "yellow_concrete")
            put(w - 2, 0, z, "yellow_concrete")
        put(w - 2, 1, d - 2, "barrel")
        put(w - 3, 1, d - 2, "barrel")
        put(2, h - 1, d - 2, "air")

    return blocks


def short(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag: int, name: str, payload: bytes) -> bytes:
    return bytes((tag,)) + short(name) + payload


def number(value: int) -> bytes:
    return struct.pack(">i", value)


def int_list(values: tuple[int, ...]) -> bytes:
    return bytes((3,)) + number(len(values)) + b"".join(map(number, values))


def compound(*entries: bytes) -> bytes:
    return b"".join(entries) + b"\0"


def nbt(building: Building) -> bytes:
    blocks = blocks_for(building)
    palette = sorted({block for block in blocks.values()})
    states = {block: index for index, block in enumerate(palette)}
    palette_entries = [compound(named(8, "Name", short("minecraft:" + block)))
                       for block in palette]
    block_entries = [compound(named(3, "state", number(states[block])),
                              named(9, "pos", int_list(pos)))
                     for pos, block in sorted(blocks.items())]
    raw = bytes((10,)) + short("") + compound(
        named(3, "DataVersion", number(DATA_VERSION)),
        named(9, "size", int_list((building.width, building.height, building.depth))),
        named(9, "palette", bytes((10,)) + number(len(palette_entries)) + b"".join(palette_entries)),
        named(9, "blocks", bytes((10,)) + number(len(block_entries)) + b"".join(block_entries)),
        named(9, "entities", bytes((10,)) + number(0)),
    )
    output = io.BytesIO()
    with gzip.GzipFile(fileobj=output, mode="wb", mtime=0, filename="", compresslevel=9) as stream:
        stream.write(raw)
    return output.getvalue()


def definition(building: Building) -> bytes:
    payload = {
        "type": "single",
        "category": f"{NAMESPACE}:{building.district}",
        "size": building.size,
        "dimensions": {"width": building.width, "depth": building.depth, "height": building.height},
        "footprint": {"offset_x": 0, "offset_z": 0,
                      "width": building.width, "depth": building.depth},
        "front": "north",
        "weight": building.weight,
        "tags": list(building.tags),
        "template": f"{NAMESPACE}:city/{building.district}/{building.name}",
    }
    return (json.dumps(payload, ensure_ascii=False, indent=2) + "\n").encode()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify generated files without modifying them")
    args = parser.parse_args()
    count = 0
    for building in BUILDINGS:
        if building.size == "small" and max(building.width, building.depth) > 12:
            raise ValueError(f"{building.name} exceeds a small lot")
        if building.size == "medium" and (building.width > 28 or building.depth > 28
                                           or min(building.width, building.depth) > 12):
            raise ValueError(f"{building.name} exceeds a two-chunk plot")
        files = {
            DATA / "structure/city" / building.district / f"{building.name}.nbt": nbt(building),
            DATA / "citygen/structures" / building.district / building.size / f"{building.name}.json": definition(building),
        }
        for path, content in files.items():
            if args.check:
                if not path.is_file() or path.read_bytes() != content:
                    raise SystemExit(f"Missing or outdated fixture: {path.relative_to(ROOT)}")
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
            count += 1
    print(f"{'Verified' if args.check else 'Generated'} {count} structure resources")


if __name__ == "__main__":
    main()
