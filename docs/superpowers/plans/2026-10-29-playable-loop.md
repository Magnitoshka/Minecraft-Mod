# Playable settlement loop and medieval map

**Goal:** Deliver 0.2.0+26.3 JAR and push source/artifact to existing feature branch.
**Architecture:** Persistent construction jobs and residents remain in overworld SavedData. Builders materialize deterministic small templates progressively; one worker slot per completed production building. Clients submit building type and integer map offsets only; the server validates ownership, territory, collision, ground and payment. Appearance is stable per UUID, using local vanilla skin textures with procedural clothing palettes.
**Scope:** House (+2 beds), warehouse (+1000 capacity), lumber hut, mine and wheat field; builder, lumberjack, miner, farmer; daily survival; owner damage protection. Full specification's upgrades/army/trade remain later work.

- [x] Extend persisted Building and Resident work state; ensure previous saves initialize missing fields.
- [x] Implement safe paid construction queue and deterministic 5x5/7x5 templates, existing-building collision and path/ground checks.
- [x] Implement builders, real-tree lumber work and replanting, abstract mine production, visible wheat farming, bounded work scheduling and stock limits.
- [x] Extend jobs, bed assignment, recruitment and warehouse capacity using completed buildings.
- [x] Block owner damage on the server, including projectile attribution.
- [x] Give each resident a stable personal skin using UUID-derived local clothing variations, clean textures on disconnect.
- [x] Replace map UI with responsive parchment/wood styling, medieval buttons, concise management pages and building preview/status/profession selection.
- [x] Add OP-only administration for wallets, treasury, residents and food, with overflow/capacity/bed validation.
- [x] Update ru/en texts and README with exact play loop and limitations.
- [x] Review implementation, compile with `./gradlew clean assemble` only, inspect JAR, commit and push source + artifact + SHA256.

**User constraint:** Do not launch client/server/GameTest/stand tests. Review code and compile; user will test gameplay. No test runs are part of this plan.
