# Terrain map and live citizen activity

**Goal:** Show coastline, surface material and relief under settlement overlays; display current builder activity above citizens.
**Approach:** Owner-checked server terrain snapshots sent separately from economy updates. Cache loaded-only surface cells, colors, ground heights and simple site-clearance flags; bounded binary packet. Client aligns tiles, height shading and hover details with the existing map transform, and rejects known water/uneven footprints in previews. Citizens send localized current activity in their visible name component.

- [x] Add loaded-only terrain sampler and expiring cache; never force chunks for map sampling.
- [x] Add bounded binary terrain message on open and every five seconds only for viewers; retain separate economy packets.
- [x] Render terrain tiles below settlement symbols, shoreline, slope shading, legend/hover coordinates and height; mark unknown data.
- [x] Use known terrain clearance/height data to improve building outlines, retaining authoritative server validation.
- [x] Bind builder activity to actual job and show localized live overhead status.
- [x] Review code, compile only, package0.3.0, update README/changelog, push source/JAR.

No client/server/test stands, per the user's instruction.

Merged into the authoritative player-feedback queue in `2026-10-30-player-feedback.md`.
