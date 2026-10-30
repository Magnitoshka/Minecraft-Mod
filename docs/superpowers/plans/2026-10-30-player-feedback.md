# Player feedback implementation queue

User constraint: code inspection and compilation only. Do not launch clients, servers, game tests or stand tests. Preserve prior mechanics, skins, owner protection and admin commands. Deliver one updated JAR and push source/artifact to the existing feature branch after the queue is complete.

1. [x] Fix construction input and visible feedback: inspect26.3 mouse constants, submit valid placement requests and display success/rejection inside the open map.
2. [x] Terrain map: server-supplied water/beach/grass/trees/material/height layer, readable relief and unknown unloaded cells; improve placement outline using known terrain.
3. [x] Construction costs: show logs and cobblestone beside every blueprint, including available amounts/shortages, with responsive paging.
4. [x] Citizen overhead activity: resting, walking, blocked and building a named structure, driven by actual server job.
5. [x] Dedicated warehouse menu: player inventory, normal click/drag/shift-click and multiple different deposits while staying in the menu; protect virtual stock and return unaccepted items on close.
6. [x] Starter banner persistence: no repeat login grant after successful founding. Audit player/world markers, refund/recovery and migration.
7. [x] Territory protection: reject another founder banner inside any existing settlement and too close to its boundary, including after world reload. Audit SavedData decoding and old-save migration.
8. [x] Reliable action notifications: acknowledge committed actions only; distinguish accepted construction queue from first actual placed block and final completion.
9. [x] Remove decorative roof banner, including identified mod-generated banners on existing halls.
10. [x] Review all changes, update ru/en text and user instructions, compile final JAR only, inspect package, commit and push.

## Findings so far

- The26.3 input API uses SDL mouse values: LEFT=1 and RIGHT=3. The0.2 map treated1 as right-click cancellation and0 as placement, so a real left click cleared blueprint selection before sending any server request. Fix all map click/drag constants.
- Server-side founding already compares settlement territories by dimension. Reported overlap and repeat starter banners may share a persistence/loading or owner-identity issue; inspect before assuming the geometry formula is wrong.
- Partial terrain/network/activity edits from the previous interrupted turn are present and will be completed in queue order.

- Persistence finding: NbtOps converts ByteTag booleans into JSON numeric0/1. Gson's default boolean adapter rejects numbers, causing SavedDataStorage to fall back to a fresh state. Added numeric-compatible boolean reader, player starter marker and refusal to replace an existing unreadable save.

## Delivery

Version0.3.0+26.3. Verified by static review and `./gradlew clean assemble`; no client/server/test stands were run. Optional crapper/dryer/mutator tools were absent and skipped. See `docs/ub-mode-log.md` for the applied skill log.
