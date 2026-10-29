# City generation profile and terrain alignment

Override `data/lasthopecitygen/citygen/generation/default.json` in a datapack to tune city generation. The bundled file is the complete example. Settings are validated together when server resources load; an invalid profile is logged and the previous valid settings remain active.

| Setting | Meaning | Allowed |
| --- | --- | --- |
| `region_chance_denominator` | One candidate region in this many, chosen by seed | 1–128 |
| `city_sizes` | Weighted square footprints in chunks | 1–16 entries; even sizes 8–28, positive weights 1–1000, total at most 10000 |
| `road_profile.sample_spacing` | Spacing between original terrain samples used for the road elevation | 2, 4, 8, 16 or 32 blocks |
| `road_profile.max_relief` | Maximum deviation of road sample elevation from the city-center elevation | 0–32 blocks |
| `terrain.subgrade_depth` | Maximum depth of shallow air pockets filled below normal paved terrain | 1–32 blocks |

The footprint is currently square and centered inside a 32 × 32 chunk region. With sizes 20–28 chunks, its approximate half-width is 160–224 blocks; this is comparable in scale to the 135–230 block city-radius interval in the modpack's `last_hope` Lost Cities profile, but is not the same shape or occurrence algorithm. Changing topology settings after some chunks have generated can leave boundaries between old and new plans. Test a profile in a new world.

## Terrain transition design

The current road profile smoothly interpolates nearby **original** terrain samples. Pavement and immediate shoulders are then graded inside their own chunks. This does not yet move natural terrain in adjacent non-city chunks.

Lost Cities 1.20.1 takes a different approach at the city boundary: city and natural chunks define lower and upper height limits at shared chunk corners; the limits interpolate across each normal chunk. Terrain outside the limits is moved while preserving a shallow layer of surface blocks. Its transition noise fades near constrained city edges. The modpack profile sets `terrainFixLowerMinOffset=-4`, `terrainFixLowerMaxOffset=-3`, `terrainFixUpperMinOffset=-1`, and `terrainFixUpperMaxOffset=1`. See [its terrain correction implementation](https://github.com/McJtyMods/LostCities/blob/1.20/src/main/java/mcjty/lostcities/worldgen/LostCityTerrainFeature.java) and [profile settings](https://mcjty.eu/docs/mods/lost-cities/profile_options).

The next terrain step should compute an immutable corner-height envelope around planned city cells before any chunk is modified. For each bordering natural chunk, blend from the nearest street or plot grade toward the original terrain; preserve surface blocks, cap cut/fill, exclude water crossings, and fill or support shallow cavities. Calculate the same corner values from seed, source terrain and profile in every chunk so that generation order cannot open seams. Validate this with synthetic slope, cliff, river and chunk-boundary cases before changing block placement.
