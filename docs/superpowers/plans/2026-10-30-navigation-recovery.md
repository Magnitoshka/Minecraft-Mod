# Navigation arrival and stalled-route recovery —0.7.2

User supplied https://prnt.sc/ROp-YTjLzMnu and observed that removing the block beneath a stuck resident makes it fall and resume movement. Screenshot inspected: builders report blocked on cleared snowy terrain. User additionally requests automatic replanning after standing too long, with large-village performance in mind.

Confirmed from exact Forge26.3 sources: `moveTo(x,y,z,speed)` creates a path with reachRange1; our strict arrival checks demand less than one block. PathNavigation.moveTo also retains an identical completed path (sameAs), then rejects it because its cursor is done. Changing position can alter the node list and escape this loop. Vanilla waypoint completion tolerance can also finish before our exact exterior work position is reached.

- [x] Centralize citizen movement: consistent reach tolerances, discard finished path before fresh planning, retarget changed goals, and physically align short final clear/supported segments for precise exterior arrival. Keep collision guards and never teleport or remove world blocks.
- [x] Add per-resident progress tracking and stalled-route retry with configurable timeout, deterministic jitter and the existing global path-calculation budget. Pause during conversation/sleep and abandon superseded goals; waiting on budget must not be reported as an obstruction.
- [x] Route work, night beds, idle walks, traffic detours and safety sidesteps through the same planning entrypoint. Preserve partial paths for long travel and require full paths for short detours.
- [x] Read-only review, clean Java25 assemble, archive/checksum/localization validation, docs/context update and0.7.2 delivery. No game/server/tests. Keep the separate death-loot task queued.

## Static review

Reviewer identified stale mandatory goals surviving exhausted budget/failed planning, and an initial height calculation limited to half-block collision shapes. Added explicit mandatory versus optional-detour operations and actual supported surface heights. Re-review found no further confirmed important regressions. No runtime/game/test processes were used. User explicitly repeated the ban on simulation stands during this work.

## Delivery

Release commit `2634c2b` pushed to origin/feat/settlements-mvp. GitHub confirms243027 bytes and artifact blob `4973fa3417103a998849720a3eb3c19ed3fa026f`, matching local0.7.2. Clean assemble/archive/checksum checks passed; no runtime/game/simulation/tests were launched.
