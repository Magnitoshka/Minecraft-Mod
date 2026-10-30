# Safe construction and automatic building repairs

**Goal:** Fix both queued defects in0.7.1 without launching game/server/tests.
**Root causes confirmed by code:** CitizenWork writes construction/fill blocks without checking living entities and treats proximity within3–4 blocks as a sufficient work position (including the future interior). BuilderAssignments selects only incomplete buildings; completed templates are never inspected again.
**Architecture:** Shared placement safety guards all construction/earthwork/repair writes and moves the builder to a safe work position. Separate persistent repair tasks preserve completed tiers, original contract ledgers and inventory-bearing blocks. A bounded loaded-only inspector discovers missing template units, including a migrated hall record; existing builder assignment and four-slot cargo carry repair resources physically.

- [x] Add collision/undermining checks before mutation/payment/progress, precise exterior work positions and bounded safe relocation; apply to normal construction, upgrades, earthworks, repairs and hut barrel migration.
- [x] Add persistent repair unit indices, material-carry origin, budgeted completed-building inspection, hall registration for old/new settlements and paired bed repair. Ignore template air and farm crops; preserve nonempty replacement blocks, fluids, inventories, dynamic states and permissions.
- [x] Let builders claim repair jobs after new work, fetch logs/cobblestone in four-slot inventory, restore only damaged units, charge only successful writes and release/reset assignment on completion. Repairing the hall barrel can fetch from virtual hall stock even while that barrel is absent. Do not repeat sapling gifts or reapply hall upgrades.
- [x] Add repair/safety statuses and notices, map repair progress, upgrade/repair exclusion, configuration, localization and protocol update as needed. Keep completed building benefits active during maintenance.
- [ ] Read-only review, fresh Java25 clean assemble, actual archive/class/localization/checksum inspection, update README/queue/context/log, commit/push0.7.1 artifact and provide direct download. No runtime verification claim.

Repair balance: one configured log for a wooden/interior block or one configured cobblestone for a masonry/metal fixture; both halves of a bed are one paired repair unit. These are abstract construction materials consistent with the existing building contracts, not recovery of lost container contents. Inspection/repair must never overwrite player-added blocks or clear template air.

## Review

Read-only review identified lost support when replacing a solid block with a nonempty partial shape, and unbudgeted discovery across all settlements every tick. Compared old/new support geometry and moved discovery into a budgeted incremental iterator. Re-review found no further confirmed important defects; compilation passed for the updated sources. The later death-loot request is queued separately and does not change this two-fix release scope.
