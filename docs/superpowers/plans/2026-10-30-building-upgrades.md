# Building upgrades and settlement progression

> Execute inline using executing-plans. Keep the current authorized delivery branch. Preserve the author's instruction: inspect code and compile/package only; no game/server/test runtime.

**Goal:** Deliver0.7.0 with safe paid upgrades, settlement levels/unlocks, tiered building templates, and visible requirements/costs on the map.
**Architecture:** Existing Building records remain stable identities. Persist completed/target tier, old template identity and immutable contract prices; builders consume the existing reservation ledger. The server validates upgrade preflight and replaces only matching old template blocks, preserving inventories/beds and player changes. Settlement tier changes only after its hall job finishes. Old0.6 jobs retain their exact template/cost interpretation.
**Tech stack:** Forge26.3-66.1.0, Java25, Gradle; SavedData and existing network/UI.

- [x] Add building tier/template/contract data and all specified building kinds with settlement unlock tiers. Centralize server-configured costs, level radii, warehouse capacities, work speed and workplace counts. Keep existing two-bed legacy houses compatible; new templates use specification1/2/4 beds.
- [x] Add cached tiered templates plus the exact existing hall template. Upgrades keep footprint and inventories; new later-stage buildings receive recognizable structural/workstation templates without enabling their later-stage professions.
- [x] Add server upgrade operation: validate owner/dimension/complete building/maximum tier/cargo return/prerequisites/materials/protection and old-vs-new template before any charge. Reserve a fixed cost, reset only the work cursor, leave completed tier unchanged until verified completion. Resume safely after reload and never consume a previous contract twice.
- [x] Complete builder integration and apply level effects on verified finish. Hall upgrades require8/20 residents and specified completed building sets. Increase territory conservatively within non-overlap constraints. Preserve all buildings as collision exclusions while upgrading.
- [x] Add map buttons for hall/building upgrades, current/target tier, requirements and server-authoritative prices. Add all catalogue entries with unlock hints, RU/EN texts and protocol bump.
- [x] Static review upgrade repair/container/bed handling, old-save compatibility, resource conservation, job ownership, unlock checks and growth. Run Java25 clean assemble only; inspect actual JAR classes/resources and checksums. Document remaining later-stage work, commit/push source and artifact, provide direct download.

Scope: this release completes a usable progression/upgrade slice of stage2. Custom downloaded house artwork, configurable field-zone sizing, and professions/combat/trade for newly unlocked buildings remain subsequent work. Existing footprints remain stable during upgrades.

## Review

- Read-only reviewer found a configured-price deadlock when one work step required more material than a load. Added persisted stagedWood/stagedStone: physical deliveries accumulate at the site until the next quota is covered, then a successful piece consumes it. Review confirmed conservation across reservation, cargo, staging, repair cursors, reassignment and reload. No further important defect reported.
- Runtime/game/tests remain prohibited by the author's instruction. Verification is source review, fresh standard Gradle clean assemble and archive/resource inspection.

## Delivery

- Java25 `./gradlew clean assemble --offline` passed; no runtime or tests launched. Validated59 classes,224 matching RU/EN localization keys,0.7.0 metadata, staging code, archive integrity and all artifact SHA256 checksums.
- Source and223794-byte JAR published in `0e0b024` to `origin/feat/settlements-mvp`. GitHub contents API confirms the artifact Git blob matches the local file (`01dd79e1218807231f6642c746e970c45b88320b`).
