# Last Hope City Generation

NeoForge 1.21.1 generator for small, deterministic abandoned city blocks.

## Current generation

The Overworld is divided into 32 × 32 chunk regions. Each region has a one-in-six seed-dependent chance of containing an 8 × 8 chunk city, centered in that region. Its connected street grid has straight sections, crossroads, T junctions, corners and short optional spurs. Lots are built only when they touch a road; other cells remain open. Plans are pure functions of the world seed and chunk coordinates, independent of the order in which chunks generate. The current sample residential building is a small NBT template. Industrial and civic lots remain vacant until templates are supplied.

Roads use a terrain profile sampled every 32 blocks and smoothed between those points, with cut and fill for their pavement. Building plots are leveled to their street frontage, and a short paved approach joins the building to the neighboring road chunk. Each building still fits within one chunk. Cells on water or requiring more than ten blocks of cut or fill remain empty, so very rough terrain can interrupt streets. No roads link separate cities yet.

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

`dimensions` must match the NBT size. `footprint` describes its occupied area within those dimensions. Current automatic placement accepts buildings no larger than 12 × 12 blocks, including after rotation. `front` is the entrance-facing direction in the unrotated template. `weight` controls selection within a category. The catalog retains `size` and `tags` for future plot rules. Only the `single` type is implemented; `composite` and `jigsaw` are reserved. Definitions can be added or overridden by datapacks and reload with `/reload`.

An operator can find the nearest planned city with `/citygen locate`, check loaded definitions with `/citygen list`, and place a specific one at their position with `/citygen place lasthopecitygen:residential/small/ruined_house`. Cities appear only while new chunks generate; the locate command reports the seed-based plan and cannot guarantee that all cells survive water or steep terrain checks. Manual placement ignores plot/terrain checks and changes blocks in the world.

## Development

Run `./gradlew build` with JDK 21 and network access for the NeoForge dependencies. The isolated planning check can be run with any recent JDK:

```sh
javac -d /tmp/citygen-plan src/main/java/com/littleemptydoll/lasthopecitygen/worldgen/CityPlan.java src/test/java/com/littleemptydoll/lasthopecitygen/worldgen/CityPlanCheck.java
java -ea -cp /tmp/citygen-plan com.littleemptydoll.lasthopecitygen.worldgen.CityPlanCheck
```
