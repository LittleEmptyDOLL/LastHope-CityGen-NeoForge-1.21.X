# Last Hope City Generation

NeoForge 1.21.1 generator for small, deterministic abandoned city blocks.

## Current generation

The Overworld is divided into 32 × 32 chunk regions. Each region has a one-in-six seed-dependent chance of containing an 8 × 8 chunk city, centered in that region. The city has two north–south and two east–west streets, with intersections and lots between them. Plans are pure functions of the world seed and chunk coordinates; visiting chunks in a different order does not change the plan. Roads, pavements and shallow supports are made in code. The current sample residential building is a small NBT template. Industrial and civic lots remain vacant until templates are supplied.

Cities currently follow terrain and are intentionally limited to individual chunks for buildings. Rough or flooded lots remain empty. Roads are not yet leveled across steep slopes; generation is intended for reasonably flat terrain. No roads link separate cities yet.

## Adding buildings

Put structure-block NBT files in `data/<namespace>/structure/<path>.nbt` (for instance `data/lasthopecitygen/structure/city/residential/ruined_house.nbt`). Put a matching metadata JSON in `data/<namespace>/city_templates/<district>/<name>.json`, where district is `residential`, `industrial`, or `civic`:

```json
{
  "template": "lasthopecitygen:city/residential/ruined_house",
  "width": 9,
  "depth": 9,
  "front": "north",
  "weight": 1
}
```

`width` and `depth` must match the actual NBT dimensions and each be at most 12 blocks. `front` is the entrance-facing direction in the unrotated template. `weight` controls selection within that district. Metadata can be added or overridden by datapacks and reloads with `/reload`. Templates can be made in game with structure blocks. The sample house can be replaced by another template of the same dimensions.

## Development

Run `./gradlew build` with JDK 21 and network access for the NeoForge dependencies. The isolated planning check can be run with any recent JDK:

```sh
javac -d /tmp/citygen-plan src/main/java/com/littleemptydoll/lasthopecitygen/worldgen/CityPlan.java src/test/java/com/littleemptydoll/lasthopecitygen/worldgen/CityPlanCheck.java
java -ea -cp /tmp/citygen-plan com.littleemptydoll.lasthopecitygen.worldgen.CityPlanCheck
```
