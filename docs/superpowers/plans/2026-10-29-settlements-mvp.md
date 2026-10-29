# Settlements stage 1 implementation plan

**Goal:** Deliver an installable Forge 26.3 / 66.1.0 JAR implementing the stage 1 settlement loop.
**Architecture:** Server owns all settlements and wallets in overworld SavedData. A pure Java domain model handles ownership, storage, food, recruitment and placement; Forge adapters handle entities, buildings, inventory and validated menu actions. The map only receives snapshots while open.
**Tech stack:** Official Forge MDK, ForgeGradle 7, Java 25, JUnit 5.

- [x] Bootstrap official MDK and compile against the exact game/Forge sources.
- [x] Test domain invariants first: territory boundaries/dimensions, unique names, capacity, atomic transfers, recruitment, starvation at day three, identity persistence.
- [x] Implement domain model and configuration defaults from specification sections 8 and 16.
- [x] Implement SavedData, founder banner and one-time starter kit, safe placement of a small hall with three beds and three custom residents.
- [x] Implement server ticks, daily food/mood, recruitment/immigration, sleep and citizen interaction; permit builder assignment only in this stage.
- [x] Implement owner-checked open-menu snapshots, map on M, treasury transfers, rename, storage inventory transfers and additional founder banner purchase.
- [x] Add Russian/English localization, item model and resident renderer using vanilla assets.
- [x] Build JAR, run domain tests and dedicated-server smoke test. Document installation and acceptance checklist, then copy distributable to artifacts/.

## Scope
Stage 2–8 mechanics (construction AI, production, combat, AI settlements, trade, raids and quests) remain future work per specification. Stage 1 must use its own citizen entity, persist server data, and never allow a client to choose another owner's economy operations. Runtime smoke testing complements unit tests; interactive rendering will require the user's game client.

## Delivery note

User requested stopping stand/runtime tests during final checks. No further game/server tests are run after that instruction. Final verification is code review and `./gradlew clean assemble`; the deliverable includes source and JAR in `artifacts/`.
