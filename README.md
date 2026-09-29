# Last Hope City Generation

NeoForge 1.21.1 generator for small, deterministic abandoned city blocks.

## Current generation

The Overworld is divided into 32 × 32 chunk regions. Each region has a one-in-six seed-dependent chance of containing an 8 × 8 chunk city, centered in that region. Before any city chunk is changed, the generator samples the original surface and water height at the center of each of its 64 cells. An ocean or small island is rejected; a coast or narrow river can be included when there is enough dry ground near the center. The same seed and terrain produce the same complete layout regardless of chunk generation order. `/citygen locate` uses this same eligibility check.

The planner grows primary and secondary streets from a central anchor, scoring a regular street direction, proximity to water, relief, distance from the anchor and stable seed noise. Each proposed 16-block step checks the region boundary, terrain slope, water crossing, spacing and existing streets. Roads cross only one or two water cells at a time and must land on dry ground; a shoreline can become a waterfront street. The resulting graph has straight streets, corners, T junctions, crossroads and dead ends according to its actual connections. Connected dry areas between streets become city blocks. Their cells are classified as building lots, open park or empty ground, beach or waterfront. Building lots with road frontage attempt a structure. On suitable level ground, two adjacent cells of the same block can form a shared 32 × 16 or 16 × 32 plot with a common street entrance. Its template is selected once from medium definitions, checked against the original terrain across both cells, and clipped to each generating chunk; without a suitable medium template, each cell can still use a small building. Low shore cells receive exposed sand without replacing water; higher shore cells can receive a connected stone promenade when a road fronts them and the local ground is suitable. Open cells remain natural.

Roads use a shared terrain profile sampled every 32 blocks, with softened shoulders, shallow cuts, short passages through steep hills, and raised decks over water or valleys. Building plots are graded to their street frontage, with a short paved approach when the selected structure fits. An unsuitable or missing structure leaves its cell natural, without an isolated fenced terrace. Small buildings fit within one chunk; medium buildings can span two. The planner currently resolves streets at 16-block cell scale; still larger plots, more detailed waterfronts, coherent gates and intercity roads are future work. Extreme terrain and in-game results still need review in a newly generated world.

## Test structure set

The small fixtures exercise 12 × 12 single-chunk lots, weighted variation, rectangular template rotation, building heights and all three planned districts. A medium apartment block exercises placement across two chunks. Every entrance faces north in the original NBT and is rotated toward the neighboring street. Barrels are empty; loot assignment is a separate generation feature.

| District | Structure ID suffix (`<district>/<size>/<name>`) | Size (W × D × H) | Weight | Distinguishing feature |
| --- | --- | --- | ---: | --- |
| Residential | `ruined_house` | 9 × 9 × 5 | 10 | Original ruined house |
| Residential | `abandoned_cabin` | 7 × 7 × 5 | 8 | Wooden cabin with damaged roof |
| Residential | `row_house` | 8 × 10 × 6 | 9 | Narrow brick house |
| Residential | `apartment` | 11 × 11 × 9 | 3 | Two levels and damaged roof |
| Residential | `residential/medium/apartment_block` | 22 × 12 × 9 | 2 | Two-chunk apartment block |
| Industrial | `workshop` | 9 × 8 × 6 | 8 | Workbench, furnace and machinery |
| Industrial | `warehouse` | 12 × 10 × 7 | 6 | Wide entrance and stacked empty barrels |
| Industrial | `substation` | 7 × 7 × 5 | 4 | Copper equipment and open roof |
| Civic | `clinic` | 10 × 9 × 6 | 7 | Medical cross and empty storage |
| Civic | `police_post` | 8 × 8 × 6 | 6 | Bars and blue facade |
| Civic | `fire_station` | 12 × 10 × 7 | 4 | Wide garage entrance and bay markings |

The automatic planner currently has no commercial or military lots, and landmarks such as a full hospital need plots larger than two chunks. The set is therefore limited to the three categories it can actually generate. `tools/generate_test_structures.py` deterministically creates ten matching NBT and JSON pairs; edit that source and regenerate both resource types together. The original `ruined_house` remains hand-authored.

## Adding buildings

Put structure-block NBT files in `data/<namespace>/structure/<path>.nbt` (for instance `data/lasthopecitygen/structure/city/residential/ruined_house.nbt`). Put a matching definition JSON in `data/<namespace>/citygen/structures/<district>/<size>/<name>.json`:

```json
{
  "type": "single",
  "category": "lasthopecitygen:residential",
  "size": "small",
  "dimensions": {"width": 9, "depth": 9, "height": 5},
  "footprint": {"offset_x": 0, "offset_z": 0, "width": 9, "depth": 9},
  "front": "north",
  "weight": 10,
  "tags": ["house", "ruined"],
  "template": "lasthopecitygen:city/residential/ruined_house"
}
```

`dimensions` must match the NBT size. `footprint` describes its occupied area within those dimensions. Small automatic placement accepts buildings no larger than 12 × 12 blocks, including after rotation. Medium two-cell plots accept a rotated template up to 28 × 12 blocks or 12 × 28 blocks. Set `size` to `medium` and put its JSON under the corresponding `medium` directory. `front` is the entrance-facing direction in the unrotated template. `weight` controls selection within a category. The catalog retains `size` and `tags` for future plot rules. Only the `single` type is implemented; `composite` and `jigsaw` are reserved. Definitions can be added or overridden by datapacks and reload with `/reload`.

An operator can find the nearest city candidate on suitable land with `/citygen locate`, check loaded definitions with `/citygen list`, and place a specific one at their position with `/citygen place lasthopecitygen:residential/small/ruined_house`. Cities appear only while new chunks generate. Rejected candidates are skipped rather than shifted within their region, so the land requirement lowers overall city frequency. Manual placement ignores plot/terrain checks and changes blocks in the world.

## Development

Run `./gradlew build` with JDK 21 and network access for the NeoForge dependencies. The isolated planning check can be run with any recent JDK:

```sh
javac -d /tmp/citygen-plan src/main/java/com/littleemptydoll/lasthopecitygen/worldgen/CityPlan.java src/main/java/com/littleemptydoll/lasthopecitygen/worldgen/CityLayout.java src/main/java/com/littleemptydoll/lasthopecitygen/worldgen/ShoreGeometry.java src/test/java/com/littleemptydoll/lasthopecitygen/worldgen/CityPlanCheck.java
java -ea -cp /tmp/citygen-plan com.littleemptydoll.lasthopecitygen.worldgen.CityPlanCheck
```

Run `python3 tools/generate_test_structures.py --check` to verify the generated fixtures without modifying them, and `python3 tools/check_structure_resources.py` to validate all eleven NBT files and their metadata, including the original house.
