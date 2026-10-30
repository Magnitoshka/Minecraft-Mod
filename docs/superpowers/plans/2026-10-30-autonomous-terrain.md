# Autonomous construction terrain

User requests: remove artificial cut/fill depth limits; allow ice, snow, grass and flowers; automatically prepare ground and access; investigate generic site rejection even on a flat world. No game/server/test runs; inspect and compile only.

1. [x] Replace the misleading generic four-block rejection with precise plan failures (block, position, reason); inspect all placement gates.
2. [x] Use a common town-hall grade and plan a walkable approach around known structures; no pre-existing flatness/path requirement.
3. [x] Store terrain work as compact column runs rather than one object per depth block. Validate world bounds, loaded chunks, ownership protection, liquids and structures before payment. No arbitrary depth/tree-height cutoff.
4. [x] Clear vegetation including flowers and ferns; excavate natural terrain/snow/ice, provide stable foundations below snow/ice through water, preserve unbreakable blocks and structures. Keep natural-tree checks.
5. [x] Persist column and route progress, make small work steps from reachable already-prepared access points; consume additional fill cobble incrementally so warehouse capacity cannot impose a depth limit. Preserve prepaid0.4 jobs.
6. [x] Align map preview, founding vegetation handling, status and RU/EN notices with actual server rules. Keep large plans/routes out of UI packets.
7. [x] Independent static review; Java25 clean assemble, inspect JAR, version/package/checksum and push. No claim of runtime reproduction or measured FPS.

Delivery:0.5.0+26.3, protocol5. Exact flat-world grass-tag root cause found in local vanilla26.3 sources; independent static recheck passed. Java25 clean assemble succeeded; archive metadata/classes/translations inspected. No Minecraft/runtime stand was launched.
