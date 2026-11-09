# Underground gems and discovery hints

Status: implemented and release-built on both targets; shared NeoForge deployment
verified. Running Minecraft instance deployment is deferred until fully closed.
Temporary graphics, mining, deterministic rules, new/old chunk placement, limited
villager knowledge, profile migration, and new ruin-note chest hints are present.
Verification details and known limits are recorded below; no live-world deployment
is implied by this plan.

## Goal and scope

Restore the idea of an older mod that scattered valuable gems across the map.
Players discover deposits through exploration and incomplete villager/ruin hints,
not an exact location guide. Target both Forge 1.20.1 and NeoForge 26.2.

Confirmed five types: ruby, opal, sapphire, topaz, and peridot (olive/lime green).
Do not reuse gems already in Minecraft;
check installed companion mods for identity/naming conflicts before implementation.
Real gem prices vary greatly with quality; game values will be explicit
balance choices, not a universal real-world price ranking.

## 1. Persistent world rules

- Generate one authoritative rule set per world using its seed and a fixed
  algorithm version. Initialize when missing on server startup; an administrator
  command may inspect/initialize it, but must not silently reroll an existing world.
- Persist `gem_biomes.json` in world-scoped storage, never one global file shared
  by unrelated worlds. Preserve it across restarts, visits, and mod updates.
- Each gem gets exactly three distinct, randomly selected eligible biome IDs.
  Choose from the world's installed/available biome registry. Overworld biomes
  only, excluding caves, nonterrain placeholders, and vanilla/modded rare biomes
  except four approved BOP exceptions: Jade Cliffs, Mystic Grove, Ominous Woods,
  and Volcanic Plains. Other nonrare overworld biomes from any mod, oceans, and
  rivers remain eligible; no namespace allowlist. Different gems may share a
  biome. Editable selection policy: [gem_biome_config.json](gem_biome_config.json).
- Only place a gem where the actual block-position biome is allowed, including
  at chunk edges and underground biome transitions.
- Store biome IDs, center Y, band minimum/maximum, generation version, and rarity
  parameters. Confirmed: each of the three biomes gets a separate center Y.
- Ordinary center Y: randomly selected from -50 through +50.
- Ocean exception: choose the ocean biome's band below its ocean-floor/base
  elevation, not relative to sea level. Retain one persisted center/band per
  selected biome; define the reference floor elevation before implementation
  because actual seabed heights vary within a biome. The full +/-15 band should
  fit below that reference floor. Actual placement must also satisfy the
  five-block burial rule beneath the local seabed, excluding the water column.
  Select from valid centers rather than clamp after rolling or reroll per chunk.
- Elevated-biome exception: potentially allow centers up to Y=100, based on an
  explicit verified allowlist/tag. `biomesoplenty:highland` is a verified biome
  ID, but its reference elevation still needs determining. Jade Cliffs is
  verified as `biomesoplenty:jade_cliffs` and is eligible despite its rare tag.
- Distribution is thickest at the center, declining toward center +/-15.
  Proposed triangular weighting; the exact outermost-level probability needs
  defining so hints never advertise a level with zero placement probability.
- Never expose the full JSON or exact rule table to ordinary players or the LLM.
  The file is an administrator-side record, not secret from server operators.
- Malformed or missing-existing configuration must surface a clear error, not
  silently reroll locations. Back up the configuration before deliberate changes.

Illustrative shape only, not final field names or chosen rules:

```json
{
  "generation_version": 1,
  "gems": {
    "ruby": {
      "biomes": [
        {"id": "example:biome_a", "center_y": 10, "min_y": -5, "max_y": 25},
        {"id": "example:biome_b", "center_y": 30, "min_y": 15, "max_y": 45},
        {"id": "example:biome_c", "center_y": 70, "min_y": 55, "max_y": 85}
      ]
    }
  }
}
```

## 2. Deposits, rarity, drops, and XP

- Underground only. User permits placement everywhere, including claimed land,
  using eligible stone types, sand, dirt, and similar terrain materials.
  Define the exact replaceable-block tags/list. Confirmed: at least five blocks
  below the terrain surface; cave-exposed ore is allowed. Surface-height lookup
  must not mistake a cave floor for the terrain surface.
  Do not overwrite air, existing ores, block entities, containers, or crafted
  blocks. A material-only check cannot distinguish player-placed stone/dirt.
- Desired rarity: medium common inside the correct biome and height band,
  comparable to coal/iron locally, but rare across the world due to restrictions.
- Coal/iron do not have one universal blocks-per-chunk value: their generation
  varies by altitude, biome, deposit size, and attempt count. Calibrate against
  actual eligible host-rock volume in the gem band rather than promise a number.
- Confirmed initial target: 6-15 ore-block targets per compact vein, starting
  around 1-2 vein attempts per eligible chunk, concentrated near center Y.
  This is not a 6-15 block spatial radius or a guaranteed actual block count.
- Host-rock, biome, cave, and protection checks can reduce actual vein size.
- Diamond-tier pickaxe or better is required to collect gems (confirmed).
- Base mined drop: one gem. Fortune uses diamond-style `ore_drops`; Fortune III
  yields 1-4 gems. Silk Touch yields the matching ore variant, not a loose gem.
- XP similar to redstone: 1-5 XP for ordinary mining, none with Silk Touch.
- Do not assume new gem ores may bypass existing FarmUpCraft mining/tool rules.
  Verify and integrate ore tags, stone/deepslate variants, harvest tools, loot,
  Fortune, Silk Touch, placed-ore tracking, market values, and recipe/JEI surfaces.

### Required new blocks, items, and temporary graphics

These gems and their ore blocks do not exist yet in FarmUpCraft. Creating them
is part of implementation, not an assumed prerequisite supplied by another mod.

- Register five gem items and five corresponding ore blocks with block items:
  ruby, opal, sapphire, topaz, and peridot. Add stone/deepslate ore variants where
  appropriate; settle how ores replacing dirt/sand are represented.
- Create original temporary Minecraft-style textures, initially 16x16:
  gem item icons and matching ore textures. Distinguish them by shape/pattern
  as well as color: red ruby, iridescent/light opal, blue sapphire,
  golden/orange topaz, olive/lime peridot.
- Supply blockstates, block models, item models/client item definitions,
  localization, and creative-tab entries using each target's resource format.
  Placeholder art must render correctly in-world, in inventory, and in-hand;
  no missing-texture blocks or missing-model items.
- Define actual block properties: hardness, blast resistance, sound, correct
  mining tool/tier, collision, and XP behavior. Diamond-tier harvesting is
  required; temporary graphics do not mean temporary broken mining.
- Provide complete loot tables: one base gem, Fortune support, and the agreed
  Silk Touch/XP behavior. Add mining and interoperability tags without making
  optional companion mods required.
- Use stable FarmUpCraft IDs so replacing temporary art later does not change
  saved blocks/items. No crafting recipes or decorative storage blocks are
  implied unless separately requested.
- Verify registration and assets on both loaders before world-generation tests:
  place/mine every variant, inspect item/ore rendering, and check loot/tool rules.

## 3. New chunks and existing chunks

Use normal world generation for new terrain and incremental population for old
terrain as chunks load. No whole-world migration or unexplored-chunk loading.

- Track `gems_generation_version` completion per chunk, not a guess about age.
- New chunks: generate the deposits and save completion only after the complete
  chunk-owned placement pass. Ensure chunk-load events do not race this pass.
- Old chunks: if unmarked, queue bounded work on the server thread; save completion
  after successful placement. Skip already completed chunks.
- Work budgets bound per-tick sampling and placement. Unloaded chunks defer work;
  never force-load neighbors just to complete a deposit.
- Implementation detail: old chunks wait for a loaded 3x3 neighborhood to use
  exact vanilla zoomed block-biome sampling at edges. Deposit coordinates remain
  neighbor-independent; fresh generation uses its existing generation region.
  An isolated old chunk may stay pending. Initial tick budgets: 256 samples,
  64 placements, eight considered chunks.
- Use deterministic candidates and replace-only-host checks for idempotence.
  New placement now rejects cave-air exposure for a seeded 50% of exposed
  targets across all five gems. Try up to twelve buried alternatives one/two
  blocks away in the same chunk/biome/band; skip if none qualifies. Never move
  existing ores or repopulate completed chunks. Hidden targets are unchanged.
  If interrupted before the marker is saved, resuming must not duplicate or move
  deposits. Existing gem blocks must not change candidate calculation/randomness.
- Confirmed: no claim-based exclusions; allowed everywhere with eligible host
  materials. Natural stone, sand, and dirt can be player-built, so this policy
  can alter such builds. User explicitly permits replacement even if eligible
  materials were player-placed. No player-placement exclusion is intended.
- Back up Rimfog2 before enabling old-chunk population; do not modify live world
  files externally or replace a running instance's mod JAR.

### Matching deposits across chunk borders

Both generation paths use the same world-coordinate deposit algorithm.
An origin chunk deterministically defines a complete vein; each destination
chunk examines all nearby origin chunks within the maximum deposit reach and
places only the part inside its own borders.

Example: A covers X=0..15; B covers X=16..31. A's seeded vein spans X=13..19.

```text
                       A | B
Planned vein:   13 14 15 | 16 17 18 19
Visit A first:   *  *  * | not touched
Visit B later:  retained |  *  *  *  *
```

B reproduces A's complete vein from A's origin seed and places X=16..19.
No unfinished-vein ledger or neighbor loading is required. This works regardless
of visit order and whether A/B are old or new. Biome/material/protection checks
may intentionally interrupt the vein. Fix maximum reach and generation version.

## 4. Villager knowledge and LLM conversations

- All villagers should support conversations: preserve custom LLM profiles;
  supply a generic code-backed profile for villagers without a readable profile.
- One-time migration for existing custom profiles in the user's village: add
  gem knowledge without replacing personality, history, or unrelated fields.
  Back up profiles and record migration version; do not repeatedly rewrite them.
- Assign one stable, mutually exclusive knowledge outcome per villager:
  ordinary hint with probability 1/10, rare hint with probability 1/30,
  otherwise no gem knowledge (13/15). These are rates across all villagers,
  not a rare roll conditional on receiving an ordinary hint.
  A knowledgeable villager knows one stable hint about one gem. Repeat it when
  asked; admit ignorance about other gems. Villagers with no knowledge admit it.
  Do not assign additional facts by repeated questioning.
- Generic villagers use the same stable outcome distribution; define whether it
  lives in entity persistent data or the existing NPC database.
- Recognize questions about gems, mining locations, and specific gem names.
  Ordinary conversation may occasionally include an assigned hint.
- Build hints from authoritative gem rules and the villager's limited knowledge.
  Scan nearby already-loaded biomes only; never locate/load unexplored chunks.
  Define radius and whether biome evidence is sampled at the ore's height.
- Movement can change "north of here": resolve relative-direction hints against
  current location rather than reuse stale relative text from a profile.
- Confirmed phrasing: the LLM varies wording by villager personality and local
  context, but code chooses the fact, direction/height type, and random order of
  a rare biome pair. The LLM may rephrase the hint but must include both rare
  alternatives with explicit uncertainty, never pick which is correct.
  Ordinary hints are direction OR height, not both; direction falls back to a
  truthful height hint if no eligible biome is nearby. Initial loaded-only
  local search radius is 128 blocks.
  Matching biome directly below the villager at the assigned underground height
  suppresses compass hints and uses the existing loose height fallback.
- Both knowledgeable and ignorant gem conversation replies use the LLM.
  Whole-word gem names (singular/plural) or gem/gems trigger the secondary prompt;
  knowledgeable villagers also have a 5% unsolicited topic roll before the LLM
  call on non-triggering messages. Otherwise no gem prompt/fact is included.
  Only common and active mode sections are sent, never the entire gem file.
  Editable base and mode-sectioned gem prompts live in shared
  `config/farmupcraft/prompts/*.txt`, reload on each request, and log the exact
  expanded prompt. Ordinary conversation and ignorant replies have no code
  content validation; the ordinary-conversation geology filter is removed. Knowledgeable
  replies retain only basic length, gem name, numeric level, and direction
  checks, without required Overworld wording or rumor/above-below/approximation-specific checks.
  Invalid knowledgeable replies are logged and retried once with the same
  prompt, then show an explicit error. These checks do not prevent additional
  invented facts or exhaustively verify natural-language semantics.
- LLM must not invent geology. Supply only validated permitted hints, not the
  entire secret rule file. For reliable enforcement, render the factual hint
  server-side or validate structured selections before displaying them.
- If no known fact applies, explicitly admit not knowing; do not substitute a
  guessed biome, height, or direction (prompt instruction, not code-enforced).
  Empty replies, service errors, and timeouts log the failure and use the spoken
  fallback "I'm a bit busy right now. Could we talk later?".

### Hint forms requested

A. Direction without biome name:
   "I heard the miners are finding rubies north of here." North, northwest etc
   Require a matching eligible biome in the loaded local sample. This establishes
   habitat direction, not proof that a specific unmined deposit was found.

B. Loose height bound:
   "Rubies are being found below level 60."
   Choose a truthful, deliberately loose above/below bound for the known biome's
   band. Do not disclose center Y or both exact band limits.

Confirmed: 1/30 of all villagers get a stable rare hint, either C or D,
in addition to the separate 1/10 ordinary-hint outcome.
Confirmed split: half of rare villagers get C, half get D (1/60 each overall).
Do not reroll eligibility per question.

C. Two named biomes, exactly one correct:
   "We found some recently in BIOME1 or BIOME2."
   Confirmed: always one eligible biome and one ineligible biome for that gem,
   with their order randomized each delivery. Choose actual installed overworld
   biome names; never accidentally choose another valid gem biome as the decoy.
   This is an intentional exception to fully truthful hints. Recommend uncertain
   wording so the false alternative is not presented as confirmed fact.
   Repeating one stable pair must not reveal which one is true through rerolling
   different decoys; only their presentation order changes.

D. A single possible level:
   "I found some recently at level 45."
   Choose a Y with nonzero placement chance inside the valid band. This reveals
   an exact possible Y, not the center or whole band, but invents a personal
   discovery. Prefer "You could try around level 45" unless fabricated in-world
   recollections are intentionally permitted.

Do not reveal exact deposit coordinates, all three biomes, or the full band.
Clarify how repeated questioning works: unlimited distinct truthful hints can
eventually reconstruct the secret rules. Recommend stable limited knowledge
per villager with repeat/cooldown limits, not freshly rerolled facts each request.

## 5. Ruin chest hints

- Only newly placed FarmUpCraft special ruin-note chests receive a hint roll.
  Ordinary world/structure chests and reused existing chests are not eligible.
  Use exactly the same mutually exclusive distribution as villagers:
  1/10 ordinary hint, 1/30 rare hint, 13/15 no hint.
  Rare hints split equally between C and D (1/60 each overall).
  Use the same authoritative generator and disclosure policy.
- Roll once per chest/structure identity, persist the result (including no hint),
  and preserve it across rescans/restarts. Do not overwrite player chest contents.
- Confirmed format: paper note. Integrate with existing ruin-note handling.
  Do not migrate or rewrite already-existing special ruin chests.
- Direction hints must reference the ruin's location, not a moving reader.
  Decide behavior if no eligible biome evidence is loaded near the chest.
- Example exact roll: one uniform outcome in 0..59, six ordinary outcomes,
  one C outcome, one D outcome, and 52 no-hint outcomes. Persist once; do not
  use independent sequential rolls that could overlap or alter these rates.

## Remaining tuning and operational follow-up

The implementation uses the confirmed defaults above. These are follow-up
observations, not blockers or unimplemented requirements:

- Measure practical ore density during play before changing the initial balance.
- Audit new optional biome providers when their cave/rare tags are incomplete;
  administrators can edit the explicit fallback exclusions.
- Observe live LLM wording quality and service load with generic villager
  profiles. Disposable tests do not certify response latency or personality.
- Back up Rimfog2 before its first gem-enabled launch and verify the lifecycle
  profile migration there; do not edit a running world's files externally.

## Installed biome exclusion audit (2026-11-07)

Completed against the local NeoForge 26.2 pack, not a generic online biome list:

- BOP `BiomesOPlenty-neoforge-26.2-26.2.0.0.28.jar`:
  `data/c/tags/worldgen/biome/is_rare.json`,
  `data/c/tags/worldgen/biome/is_cave.json`,
  `data/minecraft/tags/worldgen/biome/is_overworld.json`, and compiled
  `BOPRareOverworldBiomeBuilder`/`BOPOverworldRegionRare` generation definitions.
- NeoForge 26.2.0.93 universal JAR: common rare/cave biome tags for vanilla biomes.
- Actual biome JSON definitions across all installed mod JARs, plus
  Threateningly Mobs' custom-dimension biome-source definition.
- The Rimfog2 world datapack directory had no entries during this inspection.
  Recheck resolved tags/world biome source at implementation time; installed
  definitions alone do not prove every biome occurs in this world's terrain.
- No numerical biome-frequency survey was performed. "Rare" below is the
  provider's category, not a measured percentage or automatically "super rare."

### Spider Nest: resolved

`biomesoplenty:spider_nest` exists and BOP explicitly lists it in `c:is_cave`.
It is NOT listed in BOP's `c:is_rare`. Exclude it under the agreed cave rule,
without relying on an unsupported rarity claim.

Other caves to exclude:

- Vanilla: `minecraft:lush_caves`, `minecraft:dripstone_caves`,
  `minecraft:sulfur_caves`, `minecraft:deep_dark`.
- BOP: `biomesoplenty:glowing_grotto` (also tagged rare).
- Alex's Caves: `alexscaves:abyssal_chasm`, `alexscaves:candy_cavity`,
  `alexscaves:forlorn_hollows`, `alexscaves:magnetic_caves`,
  `alexscaves:primordial_caves`, `alexscaves:toxic_caves`.
  These are the six installed cave-biome definitions; do not treat absence of
  a common cave tag as proof they are surface gem candidates.

### BOP's complete rare-tag list

All IDs below use the `biomesoplenty:` namespace:

| Biome ID path | Classification |
| --- | --- |
| dead_forest | Provider-tagged rare |
| fungal_jungle | Provider-tagged rare; also explicit rare-region selection |
| hot_springs | Provider-tagged rare |
| jade_cliffs | Provider-tagged rare |
| muskeg | Provider-tagged rare |
| mystic_grove | Provider-tagged rare; also explicit rare-region selection |
| old_growth_dead_forest | Provider-tagged rare |
| ominous_woods | Provider-tagged rare; also explicit rare-region selection |
| origin_valley | Provider-tagged rare |
| snowblossom_grove | Provider-tagged rare |
| tropics | Provider-tagged rare |
| volcanic_plains | Provider-tagged rare |
| volcano | Provider-tagged rare |
| wintry_origin_valley | Provider-tagged rare |
| glowing_grotto | Provider-tagged rare AND cave |

Additional generation-backed candidate: `biomesoplenty:auroral_garden`.
It is absent from BOP's rare tag, but the rare-overworld region instantiates
`BOPRareOverworldBiomeBuilder`, whose explicit rare biome array includes it.
It is excluded under the final rare-exclusion policy.

### NeoForge's vanilla rare-tag list

All IDs below use the `minecraft:` namespace:

```text
sunflower_plains
flower_forest
old_growth_birch_forest
old_growth_spruce_taiga
bamboo_jungle
sparse_jungle
eroded_badlands
savanna_plateau
windswept_savanna
ice_spikes
windswept_gravelly_hills
pale_garden
mushroom_fields
deep_dark
```

This is broader than only exceptionally rare biomes: flower forests, sparse
jungles, and savanna plateaus may be familiar in this world. Do not silently
equate this entire tag with the user's phrase "super rare."

### Other biome definitions that must not enter the random pool

- TerraBlender `terrablender:deferred_placeholder` is not a playable biome choice.
- BOP Nether/End biomes are outside the agreed dimension scope; their existence
  in the biome registry does not make them eligible.
- Threateningly Mobs defines `distortion_land`, `cursed_nest`, and `star_desert`.
  Its overworld tag contribution is empty; its distortion-dimension source
  explicitly uses the first two. None has demonstrated overworld eligibility,
  so do not include any merely because it is registered.

### Final selection policy (confirmed 2026-11-07)

Use confirmed overworld candidates only; exclude caves and nonterrain placeholders.
Exclude vanilla and modded rare biomes using resolved `c:is_rare` plus the
explicit audited list (including Auroral Garden). Only four rare exceptions
stay eligible: `biomesoplenty:jade_cliffs`, `biomesoplenty:mystic_grove`,
`biomesoplenty:ominous_woods`, and `biomesoplenty:volcanic_plains`.
All other nonrare overworld biomes from any mod remain eligible.
Discover eligibility from the world's actual overworld biome source and resolved
tags, with verified cave exclusions for providers whose tags are incomplete.
Do not require a mod to belong to a fixed supported-mod allowlist.
Apply optional-ID handling for absent mods, never choose missing biomes, and
require at least three eligible biome choices before creating the world rules.
Port the selected semantic policy to Forge after inspecting that target's
installed biome IDs/tags; this audit verifies NeoForge only.

### Editable biome policy JSON

[gem_biome_config.json](gem_biome_config.json) is the initial editable policy
deliverable. Its defaults are installed at `config\gem_biome_config.json` on first
run and read by the mod without overwriting administrator edits.

- `allowed_dimensions`: allowed generation dimensions; still require actual
  biome-source membership, not merely a registered biome ID.
- `excluded_biomes`: unconditional exclusions, including cave fallbacks and the
  placeholder. Cave tags also apply when `exclude_cave_biomes` is true.
- `rare_biomes` and `rare_biome_tags`: explicit audited fallback/supplement and
  resolved rare tags, applied when `exclude_rare_biomes` is true.
- `rare_biome_exceptions`: bypass ONLY rarity exclusion, never cave, dimension,
  placeholder, or unconditional exclusions. Edit this list to keep more rare
  biomes. Nonrare biomes do not need listing here.
- Missing optional-mod biome IDs/tags are not selected and must not prevent
  bare-NeoForge loading. Invalid schema/IDs require explicit diagnostics.
- This policy is separate from `gem_biomes.json`, the world's already-rolled
  three-biome/height assignments. Policy edits must not silently reroll existing
  assignments or repopulate completed chunks; define explicit migration and
  command confirmation before changing an existing world's rules.

## Implementation and acceptance checklist

### Current verification and operating notes

- Both loader compilations pass; seven common gem rules/config/migration tests
  pass. NeoForge real-level gem-content/gameplay tests pass.
- Native-world acceptance passes on both targets, including fresh terrain,
  interrupted old-population resumption, persisted rules, and completion.
- Mixed new-generation/old-population border tests match all 91 ore positions
  and types in both visit orders, including a nine-target crossing vein.
  A completed retry adds zero ores; a controlled partial-decoration save with
  45 ores recovers the same 91. This simulates interruption, not a process kill.
  Both final native runs report no save errors. The separate GameTest run passed
  assertions but logged Windows atomic-save errors and does not certify persistence.
- New-world terrain reference anchors: Crag70, StonyPeaks120, SnowySlopes100,
  Highland100, JadeCliffs120; other non-ocean biomes default to64. These replace
  the previous Highland/Jade sampling for new rules only. Gem center ceilings
  are50/100/80/80/100 respectively, with actual per-column burial still checked.
  Existing saved assignments, depths, and references remain unchanged.
- Flat worlds with fewer than three eligible biomes explicitly disable gems;
  existing missing/corrupt initialized rules remain errors, not a reroll.
- Real graphical client/server acceptance verified five gem icons and ten ore
  variants. Real survival mining verified diamond tier, one base gem, Fortune,
  Silk Touch, and 1-5 XP. Generic-profile/chest hooks and invalid hint facts
  have acceptance coverage; live LLM wording quality is not certified.
- Profile backup/unknown-field preservation/idempotence is unit-tested, not a
  live Rimfog2 profile migration. Exhaustive conversation routing is not tested.
- Config: `config\gem_biome_config.json`. Rules:
  `<world>\farmupcraft\gems\gem_biomes.json`. Profile backups:
  `npcData\gem-migration-v1-backups`.
- Initial market prices: ruby/sapphire100, opal80, topaz/peridot60, only when
  missing; existing prices preserved.
- Both release JAR builds and packaged gameplay/content checks passed.
  NeoForge shared destination was backed up and SHA-256 verified; its instance
  destination was not overwritten because Minecraft was running.

1. Agree on rule schema and balance; inspect installed biome registry/elevation
   candidates and existing ore, NPC profile, and ruin chest patterns.
2. Implement persistent seed-based rules and bounded border-aware placement.
3. Add new/old chunk lifecycle hooks and completion markers.
4. Create the missing gem items/ore blocks, complete block properties and resource
   definitions, original temporary textures/models, loot, localization, creative
   entries, and mining/economy integration. Verify rendering and real mining.
5. Add limited villager knowledge, generic fallback profiles, backed-up one-time
   custom-profile migration, and enforced truthful/rumor hint policy.
6. Add once-only ruin chest rolls using the shared hint generator.
7. Test reproducibility, rarity distribution/height taper, valid biomes, exact
   drops/XP, chunk borders in both visit orders, old/new combinations, restart
   mid-pass, completed chunks, unloading, no forced generation, protected builds,
   missing optional mods/biomes, and configuration corruption.
8. Test exact mutually exclusive 1/10 ordinary and 1/30 rare outcomes for both
   villagers and eligible newly placed chests, including 1/60 each for C/D,
   exclusion of existing/reused/ordinary chests, and no rerolls across controlled
   inputs, profile preservation, no-knowledge answers, misleading LLM outputs,
   hint leakage/repetition, and loaded-biome-only sampling.
9. Verify both target builds and disposable worlds before enabling Rimfog2.
