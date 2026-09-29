#!/usr/bin/env python3
"""Check structure NBT syntax, block positions and matching catalog metadata."""

from __future__ import annotations

import gzip
import json
from pathlib import Path
import struct


DATA = Path(__file__).resolve().parents[1] / "src/main/resources/data"
NAMESPACE = "lasthopecitygen"


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0

    def take(self, count: int) -> bytes:
        result = self.data[self.offset:self.offset + count]
        if len(result) != count:
            raise ValueError("Truncated NBT")
        self.offset += count
        return result

    def unpack(self, fmt: str):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(">" + fmt)))[0]

    def string(self) -> str:
        return self.take(self.unpack("H")).decode("utf-8")

    def payload(self, tag: int):
        if tag in (1, 2, 3, 4, 5, 6):
            return self.unpack({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[tag])
        if tag == 7:
            return self.take(self.unpack("i"))
        if tag == 8:
            return self.string()
        if tag == 9:
            item = self.unpack("B")
            length = self.unpack("i")
            if length < 0 or (item == 0 and length):
                raise ValueError("Invalid NBT list")
            return [self.payload(item) for _ in range(length)]
        if tag == 10:
            result = {}
            while (item := self.unpack("B")) != 0:
                name = self.string()
                if name in result:
                    raise ValueError(f"Duplicate NBT key {name}")
                result[name] = self.payload(item)
            return result
        if tag in (11, 12):
            length = self.unpack("i")
            if length < 0:
                raise ValueError("Negative array length")
            return [self.unpack("i" if tag == 11 else "q") for _ in range(length)]
        raise ValueError(f"Invalid NBT tag {tag}")


def read_nbt(path: Path) -> dict:
    reader = Reader(gzip.decompress(path.read_bytes()))
    assert reader.unpack("B") == 10
    assert reader.string() == ""
    root = reader.payload(10)
    assert reader.offset == len(reader.data), path
    return root


def check(path: Path) -> None:
    definition = json.loads(path.read_text())
    namespace, template = definition["template"].split(":", 1)
    nbt_path = DATA / namespace / "structure" / f"{template}.nbt"
    assert nbt_path.is_file(), nbt_path
    root = read_nbt(nbt_path)
    width, height, depth = root["size"]
    assert (width, height, depth) == tuple(definition["dimensions"][key]
                                          for key in ("width", "height", "depth")), path
    assert height > 0, path
    assert definition["category"] == f"{NAMESPACE}:{path.parent.parent.name}", path
    assert definition["front"] == "north" and definition["weight"] > 0, path
    assert definition["type"] == "single" and definition["size"] == path.parent.name, path
    if definition["size"] == "small":
        assert width <= 12 and depth <= 12, path
    elif definition["size"] == "medium":
        assert 12 < max(width, depth) <= 28 and min(width, depth) <= 12, path
    else:
        raise AssertionError(path)
    footprint = definition["footprint"]
    assert 0 <= footprint["offset_x"] and 0 <= footprint["offset_z"], path
    assert 0 < footprint["width"] <= width - footprint["offset_x"], path
    assert 0 < footprint["depth"] <= depth - footprint["offset_z"], path
    palette = root["palette"]
    assert palette and all(state["Name"].startswith("minecraft:") for state in palette), path
    positions = set()
    for block in root["blocks"]:
        x, y, z = block["pos"]
        assert 0 <= x < width and 0 <= y < height and 0 <= z < depth, path
        assert 0 <= block["state"] < len(palette), path
        assert (x, y, z) not in positions, path
        positions.add((x, y, z))
    assert positions and isinstance(root["entities"], list), path


def main() -> None:
    definitions = sorted((DATA / NAMESPACE / "citygen/structures").rglob("*.json"))
    assert len(definitions) == 11, f"Expected eleven test structures, got {len(definitions)}"
    for path in definitions:
        check(path)
    print(f"Validated {len(definitions)} structure definitions and NBT templates")


if __name__ == "__main__":
    main()
