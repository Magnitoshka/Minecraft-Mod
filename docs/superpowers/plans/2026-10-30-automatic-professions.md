# Automatic professions — combined0.8.0 release

User added automatic profession selection during death-loot work: unemployed residents should take a profession when needed. Deliver both completed tasks together as0.8.0. The later road restriction is explicitly queued only, not part of this release.

- [x] Run staggered per-settlement checks (default5 seconds), loaded-only/no pathfinding. Consider only spawned unemployed residents with no carried/assigned cargo, and keep manually assigned professions unchanged.
- [x] Use actual food reserve/available fields, outstanding construction/repair jobs, hut goods needing delivery, wood/cobblestone reserve targets and free completed workplaces. Share existing WorldState.assign validation and slot limits; notify the owner.
- [x] Track automatic assignments. Return only temporary automatic builders to unemployment when all jobs end and their physical cargo return is finished. Keep other workers' profession/XP; a manual assignment clears the automatic marker.
- [ ] Add server configuration, localization and UI automatic marker. Review correctness alongside death-loot handling; compile/package without game/server/simulation/tests and deliver0.8.0.
