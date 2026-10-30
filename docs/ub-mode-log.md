# /ub-mode action log — 2026-10-30

1. Installed `unclebob/ub-mode` from its default `master` branch into `/home/ubuntu/.codex/skills/ub-mode` using the system skill installer. The initial default-main attempt failed because this repository uses master; retry with the correct branch succeeded.
2. Read the installed skill and applied `/ub-mode` before the next code edit. Checked crapper, dryer and mutator: none are installed, so skipped those gates as explicitly requested.
3. Inspected action dispatch and warehouse acceptance. Reused `WarehouseMenu.accepts` in the legacy hand-deposit endpoint, keeping the acceptance decision in one place. Added an error response to invalid/unauthorized map-open requests instead of silently returning.
4. Preserved the user's no-stand constraint. No new game/client/server/test process is run. The next validation is code inspection and Java compilation; a contrived static test would not establish player-visible behavior.

## Validation and delivery

5. `./gradlew clean assemble` completed successfully with Java25; this compiles without running tests or a game stand. Static review found no blocking warehouse/terrain/save defect; founding notification was ordered after map opening.
6. Verified installed `ub-mode/SKILL.md` matches the requested repository and confirmed optional tools are absent.
7. Packaging the JAR, checking metadata/checksum and delivering the reviewed changes through GitHub. Source remains in the existing authorized `feat/settlements-mvp` delivery branch; no unrelated branch cleanup is performed.

## Map FPS fix — 2026-10-30

- Applied `/ub-mode` and systematic debugging; the user still forbids stand/runtime tests. Optional tools remain unavailable and are skipped.
- Inspected frame and survey loops: per-cell terrain fills scale with survey area every frame; full world survey runs in one tick.
- Replaced terrain geometry with one dynamic texture, uploaded on changed survey; reduced decoration and release the texture when the map closes.
- Next: bounded server survey work, compilation, then the separately requested builder leveling feature.

- Finished the shared server survey budget and cache; `assemble` succeeded before starting earthworks. Committed the performance unit as `5d669bb`.
- Added persisted site edits with bounded cut/fill/tree clearance; validated the complete plan, permissions, fluids and path before reserving the base cost plus one cobblestone per fill block. Existing jobs keep their former template cursor.
- Builder preparation checks each expected block again, checks fill support, advances only after a successful edit/already-completed edit, and reports preparation separately from actual construction. Logs are credited only after successful removal, up to warehouse capacity. Completed plans are cleared from saves.
- Updated map placement estimates, progress, overhead status, RU/EN messages and README. Excluded server edit lists from network snapshots while keeping compact totals; incremented protocol to4 so clients update together. This avoids large snapshot packets for full construction queues.
- Java compilation/assemble succeeded for the new mechanics. Requested independent read-only static review; no game or test runtime is launched.
- Static reviewer identified unsafe bare-log/stone clearance and a potentially inaccessible future entrance. Restricted excavation to soft natural ground, requiring rooted upright unstripped logs with a natural non-persistent canopy; player-placed persistent leaves are rejected. Stone may support a foundation but is not excavated. Required an already-loaded approach within one vertical block of the finished entrance. Natural-block provenance cannot be known perfectly; settlement footprints, block entities and protection remain authoritative exclusions.
- Follow-up static review confirmed both reported issues addressed. Included below-grade tree/plant clearance before shallow filling so a tree on the low side does not prevent preparation; all dig edits still finish before that column's fills.
- Final Java25 `clean assemble` passed. Inspected packaged0.4.0 mod metadata, new classes and RU/EN resources without launching Minecraft. Copied the JAR into artifacts and regenerated checksums; source and artifact are ready for the authorized GitHub push.

## Autonomous terrain and flat-world placement — 2026-10-30

- User clarified that the error mentions the four-block cut even on a flat world. That was a generic site rejection covering multiple causes, not evidence of a real four-block slope. Inspected the exact Forge sources: flowers such as dandelions lack the replaceable property. Unified vegetation/flower/snow clearance separately from that property and replaced the generic site response with reason + actual block + XYZ.
- Removed MAX_CUT/MAX_FILL/MAX_TREE checks and the existing-path/step-height gates. Plan level follows the town hall; construct access first around saved building footprints, then level the footprint. Short route waypoints address finite NPC pathfinding distance. Check all clear/fill cells before reserving base materials.
- Replaced per-block deep work lists with compact original-block runs per column; persisted clear/fill cursors and routes, omitted those server plans from UI snapshots. Old0.4 prepaid edit plans remain supported. Added expected block, permission, fluid, block-entity, sturdy-support and unbreakable-block guards at actual write time.
- Foundations under snow/ice extend to stable ground, allowing water below the foundation. Fill cobble is charged only after each successful new fill block; missing stock pauses with a truthful notice and resumes automatically. No warehouse-size prepayment or arbitrary depth cutoff. Quoted remaining fill blocks are shown in building details.
- Added vegetation clearance to founding, including clearing the actual interior/doorway, and normalize banner clicks on plants/snow. Rechecked founding permissions and unbreakable floors.
- Updated preview to stop treating vegetation, snow/ice or uneven natural terrain as automatic rejection; server remains authoritative. Added detailed RU/EN failures, resource-wait status and protocol5. Optional crapper/dryer/mutator executables remain absent and are skipped; no runtime/test stand is launched.
- Compile/assemble succeeded for the implementation. Independent static review identified missing natural sandstone/clay/terracotta support; expanded the natural terrain classifier to cover those, ordinary ores and other vanilla terrain types. Natural material provenance cannot be inferred perfectly; protected cells, block entities, saved footprints and crafted structures remain exclusions.
- Independent review found the exact flat-world root cause in Minecraft26.3's VanillaBlockItemTagsProvider: DIRT now contains dirt/coarse_dirt/rooted_dirt, while grass_block/podzol/mycelium moved into GRASS_BLOCKS. Using DIRT rejected ordinary grass floors. Replaced earth/root/planting checks with SUBSTRATE_OVERWORLD (DIRT + MUD + MOSS_BLOCKS + GRASS_BLOCKS), including lumber work. Source inspection establishes the bug; no runtime reproduction is claimed.
- Made resource-waiting jobs yield to ready construction jobs, with periodic automatic retries, so a deep foundation lacking cobble does not prevent a queued mine/other site from being built.
- Final follow-up review found no remaining important issues in the reviewed logic. Allowed natural water-to-ice freezing inside a planned foundation so a frozen target does not require manual thawing. Checked RU/EN detailed-error placeholders and confirmed no MAX_CUT/MAX_FILL/MAX_TREE or legacy DIRT checks remain in the placement/worker paths.
- Final Java25 `clean assemble` succeeded for0.5.0. Inspected archive integrity, mod metadata, compact terrain classes and RU/EN resources; packaged artifacts with regenerated SHA256 checksums for the authorized GitHub delivery. No game/server/test runtime was launched.

## Builder inventories and exclusive assignments — 2026-10-30

- Applied /ub-mode; optional crapper/dryer/mutator remain absent. Preserved the user's no-stand/runtime-test constraint; inspected exact APIs and compiled only.
- Persisted one authoritative assignedBuilder per building and four64-item material slots per resident. Building reservation ledger retains actual wood species; inventory records origin to return reserved versus general materials correctly. Legacy paid jobs materialize only their remaining contract balance and paid foundation credit, without charging again.
- Builders claim distinct jobs, visit the hall's storage barrel, carry material batches and return when the next work step needs more. Base quota is consumed progressively across the template; foundation items are consumed from carried stock. Assignment, cargo and supply phase survive reload. Removed entity-initialization assignment resets. Recover cargo/release jobs on profession changes, death, starvation and admin removal.
- Added visible four-slot inventory, counts, assigned building/builder and supply statuses. Excluded private reservation details from UI packets; protocol6.
- Independent static review found a hall return waypoint could redirect a builder after entering. Fixed by checking storage proximity first and keeping the interior leg inside. Persisted reconciliation changes in both callers. Compilation/assemble passed; no game or stand executed.
- Recorded the user's subsequent requests in order: citizen yielding; stopped right-click dialogue; local independent building heights; settlement resident list;10 starter oak saplings in the existing lumber hut (confirmed by user); per-species64 sapling limit, physical hut wood/sapling storage and courier transport.

## Builder logistics and residents — resumed 2026-10-30

- Restored the saved ten-item implementation plan and continued the existing `feat/settlements-mvp` branch without discarding its uncommitted work. Read minecraft-modding, executing-plans, ub-mode, systematic-debugging and verification instructions. Optional crapper/dryer/mutator remain absent and are skipped as previously authorized.
- Preserved exclusive builder claims and four-slot cargo accounting; added traffic sidestepping/backtracking, immobility during owner conversations, resident roster and local ground-height foundations with one-block access steps and fixed existing-road height anchors.
- Added real hut barrels, a one-time ten-sapling gift with a distribution marker stored with the barrel components, bounded collection of actual dropped saplings (including delayed leaf decay), carried lumber, and a warehouse-assigned courier with persisted trip direction and visible carried stack.
- Static reviewer found four important issues: missing delayed-sapling targeting, instant production-cargo return on profession changes, conflicting old-route entrance heights, and courier oscillation on shared route prefixes. Corrected each; reviewer re-read the changes and reported no further important issue. Marked journey-phase transitions dirty after the review's minor persistence note.
- Production cargo stays carried when changing profession and is returned physically subject to capacity. Death/starvation/admin departure queues remaining production items for bounded physical drops at the hall instead of overfilling virtual resources.
- The normal Gradle command cannot start in this sandbox: the default cache is read-only and its lock listener cannot create a socket (`Operation not permitted`). A writable temporary cache confirmed the socket restriction; no escalation or game/test runtime was launched.
- Added `tools/package-cached.py`: clean compile of all37 Java sources with Java25 and the Forge event annotation validators against the exact already-cached Forge26.3-66.1.0 classes, followed by resource packaging with runtime test data excluded. The script completed successfully for0.6.0. This is not a successful Gradle `clean assemble`.
- Verified JAR archive integrity, version metadata, new classes, RU/EN localization key parity, exclusion of runtime test data and every published artifact SHA256. Updated README, changelog, release version and plan.
