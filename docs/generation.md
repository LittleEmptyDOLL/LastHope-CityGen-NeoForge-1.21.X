# City generation profile and terrain alignment

Override `data/lasthopecitygen/citygen/generation/default.json` in a datapack to tune city generation. The bundled file is the complete example. Settings are validated together when server resources load; an invalid profile is logged and the previous valid settings remain active.

| Setting | Meaning | Allowed |
| --- | --- | --- |
| `region_chance_denominator` | One candidate region in this many, chosen by seed | 1–128 |
| `city_sizes` | Weighted square footprints in chunks | 1–16 entries; even sizes 8–30, positive weights 1–1000, total at most 10000 |
| `planning.branch_spacing` | Approximate spacing of cross streets along the main road, in chunks | 3–16; default 6 |
| `planning.branch_length` | Maximum length of local access streets, in chunks | 2–24; default 7 |
| `planning.road_coverage_percent` | Maximum road cells as a percentage of the footprint, also capped by available dry land | 15–40; default 28 |
| `planning.civic_radius_percent` | Manhattan radius of the civic center as a percentage of city width | 10–45; default 23 |
| `planning.industrial_radius_percent` | Manhattan radius of the industrial cluster as a percentage of city width | 10–45; default 29 |
| `road_profile.sample_spacing` | Spacing between original terrain samples used for the road elevation | 2, 4, 8, 16 or 32 blocks |
| `road_profile.max_relief` | Maximum deviation of road sample elevation from the city-center elevation | 0–32 blocks |
| `terrain.subgrade_depth` | Maximum depth of shallow air pockets filled below normal paved terrain | 1–32 blocks |

The footprint is currently square and centered inside a 32 × 32 chunk region. The default sizes 16–30 chunks give sides of 256–480 blocks. The eligible footprint is larger than the actual street and building area: water, relief and missing templates may leave natural land. The planner first extends a main road, then cross streets from multiple points on that road, then short access streets. Each phase uses the same terrain, crossing and connected-road checks. Civic and industrial clusters are centered on suitable dry cells; residential plots fill the remainder. District centers and boundaries vary with the seed. The district radius is measured in chunk cells, not the Lost Cities block-radius setting. Older datapacks without `planning` receive the default planning values. Changing topology settings after some chunks have generated can leave boundaries between old and new plans. Test a profile in a new world.

## Search, city scale and generation cost

Each region is **32 chunks = 512 blocks** wide. A city candidate is selected independently in each region; a region without a candidate has no planned city. Adjacent city footprints do not currently join, even when both regions contain a candidate. A continuous city spanning many regions needs a separate metropolitan plan that coordinates connected roads and district borders; raising `city_sizes` beyond the 30-chunk limit would not accomplish that.

`/citygen locate` orders seed-selected candidates by distance before testing terrain. It stops as soon as a suitable city is found. This matters because a terrain check plans an entire candidate footprint. The actual plan is cached for subsequent chunk generation. Simultaneous chunks requesting the same region share one calculation, including the result when the site is unsuitable. Road elevations reuse a bounded cache of original terrain samples between adjacent chunks; the samples are keyed by the generator, its random state, spacing and world coordinates. Both caches are bounded and contain only values derived from original terrain, so chunk order cannot alter the plan. Medium plots still check their full footprint separately when placed and can be expensive.

## Terrain transition design

The current road profile smoothly interpolates nearby **original** terrain samples. Pavement and immediate shoulders are then graded inside their own chunks. This does not yet move natural terrain in adjacent non-city chunks.

Lost Cities 1.20.1 takes a different approach at the city boundary: city and natural chunks define lower and upper height limits at shared chunk corners; the limits interpolate across each normal chunk. Terrain outside the limits is moved while preserving a shallow layer of surface blocks. Its transition noise fades near constrained city edges. The modpack profile sets `terrainFixLowerMinOffset=-4`, `terrainFixLowerMaxOffset=-3`, `terrainFixUpperMinOffset=-1`, and `terrainFixUpperMaxOffset=1`. See [its terrain correction implementation](https://github.com/McJtyMods/LostCities/blob/1.20/src/main/java/mcjty/lostcities/worldgen/LostCityTerrainFeature.java) and [profile settings](https://mcjty.eu/docs/mods/lost-cities/profile_options).

The next terrain step should compute an immutable corner-height envelope around planned city cells before any chunk is modified. For each bordering natural chunk, blend from the nearest street or plot grade toward the original terrain; preserve surface blocks, cap cut/fill, exclude water crossings, and fill or support shallow cavities. Calculate the same corner values from seed, source terrain and profile in every chunk so that generation order cannot open seams. Validate this with synthetic slope, cliff, river and chunk-boundary cases before changing block placement.
