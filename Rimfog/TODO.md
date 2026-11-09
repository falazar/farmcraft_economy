# Rimfog TODO

Need to get another orange, and try and cook every food possible!

## New-content compatibility

- [x] Add the pale oak wood family to the NeoForge wood market catalog,
  including stripped logs/wood, leaves, saplings, doors, fences and stairs.
- [x] Add bamboo planks and cherry/mangrove boats (including chest boats)
  to both wood market catalogs. Existing worlds need `/market importtxt wood`
  after installing the updated jar; imports preserve existing prices and stock.
- [x] Review Creaking / Pale Garden gameplay and pale oak planting rules.
  Pale oak saplings (`pale_oak`) are restricted to `minecraft:pale_garden` on both loaders, matching `cherry` (`cherry_grove`), `magic` (`mystic_grove`), and `umbran` (`ominous_woods`).
- [ ] Review other Pale Garden market candidates: Creaking heart, resin
  clumps/bricks/blocks, pale moss, hanging moss, and both eyeblossoms.
- [ ] Review other verified current-version market omissions:
  torchflower seeds/flowers, pitcher pods/plants,
  armadillo scutes, wolf armor, breeze rods, wind charges, mace, heavy core,
  polished tuff/tuff bricks, copper bulbs/grates, and dried ghast.
  Catalog absence is not automatically a bug: decide which items should be
  tradable, their category/pricing, and which should stay exploration-only.
  This is a targeted check, not an exhaustive newer-content audit.
- [ ] Separate runtime availability from world obtainability:
  - Resolve optional/version-specific content by registry ID; skip unavailable
    IDs with diagnostics. Do not require newer vanilla content in Forge 1.20.1.
  - An installed/registered item does not prove its biome or structure exists
    in a particular save. Older generated chunks are not automatically
    populated with new world-generation features; unexplored terrain may
    provide them if the active world generator permits it.
  - Avoid mandatory progression requirements for worldgen-dependent content
    without an alternative. Decide whether markets use all supported items
    or only discovered items; current imports check registry availability,
    not whether the item can be obtained in that world.
  - Test current worlds, upgraded worlds, absent optional mods, and both
    supported Minecraft versions before enabling new requirements.
