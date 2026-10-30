# Builder logistics and resident improvements

Execute in order. Preserve no-runtime/no-stand constraint; inspect code, compile/package JAR and push when a delivery is ready. Apply /ub-mode; skip missing crapper/dryer/mutator.

1. [x] Each builder exclusively claims a different unfinished building; assignment survives reload and is released when completed or profession/resident changes.
2. [x] Each builder has four persistent inventory slots, each max64. Fetch base materials and additional foundation cobble physically at storage before work; return for supplies when needed. Preserve paid jobs without charging twice; keep material accounting explicit.
3. [x] Fix residents meeting head-on: yield and move around instead of endless pushing; account for narrow paths.
4. [x] Right-click conversation stops the resident until closed; show actual carried inventory and counts in their card.
5. [x] Building height follows the chosen local terrain, above or below the hall; level the full footprint and prepare an appropriate access route rather than enforcing hall elevation.
6. [x] Settlement tab lists all residents with profession/activity and opens their cards.
7. [x] Lumber hut contains a physical storage and receives10 oak saplings once, on completion (user confirmed existing lumber hut, not a new builder hut).
8. [x] Lumberjack collects saplings into hut storage, stops collecting each tree species when stored saplings reach64; stores harvested wood there.
9. [x] Courier/warehouse master fetches wood and saplings from hut storage into settlement warehouse.
10. [ ] Static review, clean assemble, artifact metadata/checksum, documentation and GitHub push. No runtime validation claim.

## Resumed implementation / validation

- Implemented items3–9 and reviewed all work against0.5.0. Preserved existing builder assignments and prepaid contract accounting.
- Static review fixed delayed sapling targeting, production cargo bypassing capacity on profession change, existing road height conflicts at entrances, and courier oscillation on shared road prefixes. Journey direction is persisted and marks data dirty on transition.
- Gradle is blocked by the managed sandbox: its file lock listener cannot create a socket (`Operation not permitted`). No sandbox escalation or runtime launch performed.
- Full Java25 compilation with Forge event annotation validators and packaging run via `tools/package-cached.py`; see `docs/ub-mode-log.md` for result and delivery status. This is not a successful Gradle clean assemble.
- Delivery: local implementation/artifact commit `c2a8504`. GitHub push attempted and failed because `github.com` cannot be resolved. Item10 remains incomplete for the unavailable Gradle check and remote delivery; compiled/package-verified JAR0.6.0 is available locally.
