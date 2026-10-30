# Map performance and builder site preparation

1. [ ] Replace per-cell GUI geometry with one cached terrain texture; upload only on changed survey and release on close.
2. [ ] Split world surveying into a bounded per-tick queue shared by open maps; reuse unchanged surveys and stop jobs for closed maps. No worker threads access mutable world state.
3. [ ] Compile the performance fix before implementing site preparation.
4. [ ] Accept bounded uneven natural terrain and trees for construction; keep structures, fluid bodies, protected areas and deep voids invalid.
5. [ ] Persist builder preparation actions and execute them in small steps before template blocks; fill shallow holes with reserved foundation material and collect cleared resources only after a successful world edit.
6. [ ] Update placement preview, overhead status, notifications and docs for preparation.
7. [ ] Static review, clean assemble only, package and push JAR. No stands/runtime tests at the user's request; no measured FPS claim.

## Root-cause evidence

Version0.3 emitted one GUI fill per visible terrain cell on every render frame (up to25,921 cells), plus repeated hatch/decoration fills. It also sampled an entire survey synchronously on the server whenever its five-second cache expired. Neither workload had a per-frame/per-tick budget. Fix both sources while preserving terrain and placement data.
