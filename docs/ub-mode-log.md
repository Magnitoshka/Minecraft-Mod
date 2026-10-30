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
