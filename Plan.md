# FarmUpCraft NeoForge 26.2 Port Plan

## Goal and scope

- [ ] Keep the existing Forge 1.20.1 build working.
- [ ] Add a separate NeoForge 26.2 build that produces its own mod jar.
- [ ] Share only code that is genuinely independent of Minecraft and the loader; keep loader-specific code and metadata in each target.

**Confirmed target:** Minecraft 26.2 on NeoForge. The initial isolated target uses NeoForge 26.2.0.93, Java 25, Gradle 9.2.1, and ModDevGradle 2.0.148, matching the official 26.2 MDK.

## Current-code snapshot

The Forge `forge-1.20.1/src/main/java` tree contains **139 Java files**. Import-family counts overlap:

- 123 files import `net.minecraft.*`.
- 56 files import `net.minecraftforge.*`.
- 73 files import `com.mojang.*`.
- 3 files import Sponge Mixin APIs.
- 3 files import JEI APIs; 6 import JourneyMap APIs.
- 132 files import at least one of Minecraft, Forge, Mojang, or Mixin APIs.

These are indicators of what needs review, not counts of files that must be rewritten. An import may remain unchanged while the calls using it need updating.

## Proposed end state

The Forge build and source tree have been moved into their own directory after a successful baseline build and NeoForge client launch. Each target is an independent Gradle build. The intended organization is:

```text
farmcraft_economy/
├── common/                 # Only verified loader/game-independent code
├── forge-1.20.1/           # Existing Forge target
└── neoforge-26.2/          # New NeoForge target
```

Each target owns its Gradle configuration, loader metadata, version-specific resources, and platform integrations. The common module should not depend on Minecraft or either loader API.

## Work chunks

### 0. Protect the current baseline

- [x] Preserve existing work before moving or reorganizing files: the Forge files were clean before relocation; the uncommitted plan and NeoForge target were left intact.
- [x] Run and record the Forge 1.20.1 baseline: `build -x copyJar` passed on JDK 17.0.20.1+1 and Gradle 8.1.1, including reobfuscation.
- [ ] Record the current modpack dependencies and the core behaviors that must keep working.
- [x] Keep the Forge 1.20.1 source/build intact while bootstrapping NeoForge.
- [x] Move Forge sources, Gradle configuration/wrapper, and the existing development run directory into `forge-1.20.1`.
- [x] Verify the Forge build again after relocation: `build -x copyJar` passed, including reobfuscation; all 139 Java source files remain present. All 336 relocated tracked Forge files match their original Git-index content. The independent NeoForge build also still passes. Runtime gameplay regression testing remains outstanding.

**Build environment:** a checksum-verified portable JDK 17 was installed for Forge. Supply its path with `-Dorg.gradle.java.home` to override the stale user-level Java-home setting without modifying other projects' settings. The initial setup took several minutes; a retry with bounded Java network timeouts completed successfully. See the README for build commands. No Gradle or Java target upgrade was needed for Forge.

**Done when:** there is a known-good reference for the old target and no existing local work is at risk.

### 1. Confirm target and dependency availability

- [x] Verify the initial 26.2 toolchain against the official MDK: NeoForge 26.2.0.93, Java 25, Gradle 9.2.1, and ModDevGradle 2.0.148.
- [ ] Check whether compatible 26.2 NeoForge releases exist for JEI, The One Probe, Pam's HarvestCraft 2 (Food Core, Crops, Food Extended, Trees), Biomes O' Plenty, JourneyMap, Villager Names, Collective, and TerraBlender.
- [ ] Decide which integrations are required, optional, replaceable, or can be omitted from the NeoForge target.
- [ ] Record the selected artifact versions and any unavailable integrations before porting their code.

**Done when:** every required dependency has a verified compatible artifact or an agreed replacement.

#### Content dependency research (2026-10-28)

The content-dependency research agent checked public Modrinth metadata and
version histories for exact Minecraft 26.2 / NeoForge matches. Direct
CurseForge file listings could not be fully verified because of access limits.
Treat absent releases as blockers requiring a decision, not proof that no
unlisted or future port exists.

| Dependency | Reported exact-target release | Next step |
| --- | --- | --- |
| Pam's HarvestCraft 2 Food Core | No matching release in checked Modrinth history | Decide whether to wait, replace, or omit its dependent features |
| Pam's HarvestCraft 2 Crops | No matching release in checked Modrinth history | Same; do not assume old crop IDs exist in the new target |
| Pam's HarvestCraft 2 Food Extended | No matching release in checked Modrinth history | Decide whether to wait, replace, or omit |
| Pam's HarvestCraft 2 Trees | No matching release in checked Modrinth history | Decide whether to wait, replace, or omit |
| Biomes O' Plenty | `26.2.0.0.28` | Verify artifact dependencies before adding |
| TerraBlender | `26.2.0.0.2` | Verify artifact dependencies before adding |
| GlitchCore (additional BOP dependency) | `26.2.0.0.0` | Include in dependency verification for BOP |

Primary metadata sources:

- [HarvestCraft Food Core](https://api.modrinth.com/v2/project/Z9OywhE4)
- [HarvestCraft Crops](https://api.modrinth.com/v2/project/5xO6VNlk)
- [HarvestCraft Food Extended](https://api.modrinth.com/v2/project/bdPeAbPS)
- [HarvestCraft Trees](https://api.modrinth.com/v2/project/KDzWXxuG)
- [Biomes O' Plenty](https://api.modrinth.com/v2/project/HXF82T3G)
- [TerraBlender](https://api.modrinth.com/v2/project/kkmrDlKT)
- [GlitchCore](https://api.modrinth.com/v2/project/s3dmwKy5)

#### Integration dependency research (2026-10-28)

The integration-dependency agent reported matching NeoForge / Minecraft 26.2
releases on Modrinth. These are candidates, not yet installed or runtime-tested
with FarmUpCraft. CurseForge artifact IDs remain unverified.

| Dependency | Reported candidate | Conversion or verification needed |
| --- | --- | --- |
| JEI | `30.48.0.242` / `30.48.0.243` | Artifact-level compatibility check pending; `.244` has a contradictory changelog mentioning 26.1.2 |
| The One Probe | `26.2_neo-15.0.1` | Check required dependencies and actual API usage before adding |
| JourneyMap | `26.2-6.0.10` | Port integration to the matching API |
| JourneyMap API | `info.journeymap:journeymap-api-neoforge:26.2-2.0.0` | Upstream documents waypoint changes and replacement of `ClientPlugin` with `JourneyMapPlugin` |
| Villager Names | `26.2.0-8.9` | Requires matching Collective |
| Collective | `26.2.0-8.67` | Verify alongside Villager Names |

Sources:

- [JEI release metadata](https://api.modrinth.com/v2/project/jei/version)
- [The One Probe release metadata](https://api.modrinth.com/v2/project/the-one-probe/version)
- [JourneyMap release metadata](https://api.modrinth.com/v2/project/journeymap/version)
- [JourneyMap API Maven metadata](https://maven.blamejared.com/info/journeymap/journeymap-api-neoforge/maven-metadata.xml)
- [JourneyMap API migration notes](https://github.com/TeamJM/journeymap-api)
- [Villager Names release metadata](https://api.modrinth.com/v2/project/villager-names-serilum/version)
- [Collective release metadata](https://api.modrinth.com/v2/project/collective/version)

### 2. Create and launch the NeoForge target

- [x] Add an isolated `neoforge-26.2` build configuration without changing the existing Forge build.
- [x] Set up the Java 25 toolchain, ModDevGradle, NeoForge dependency, and client/server run configurations.
- [x] Add NeoForge-specific mod metadata and verify it is packaged in the jar.
- [x] Build the minimal starter jar.
- [x] Launch the development client and confirm it discovers FarmUpCraft on NeoForge 26.2.
- [ ] Test a dedicated server before porting features; this requires accepting the Minecraft EULA for the isolated development server.

**Done when:** the empty/minimal NeoForge target builds and launches on both client and dedicated server.

### 3. Identify and extract genuinely shared logic

- [ ] Review the `currency/`, `data/`, `data/rules/`, and `util/` packages for domain logic that can be separated from Minecraft types.
- [ ] Keep Minecraft values such as `ItemStack`, `BlockPos`, `ResourceLocation`, `Component`, registry objects, and game codecs out of the common module.
- [ ] Review `ChecksumUtil.java` and `FileInfo.java` as straightforward Java-only sharing candidates.
- [ ] Review `AnimalGrainAssigner.java`, `QueryBuilder.java`, and database file-format strategies; share them only after their model/database dependencies are decoupled.
- [ ] Add focused tests for any extracted pure-Java rules or calculations.

**Done when:** shared code compiles without either loader or Minecraft on its compile classpath, and both targets can consume it.

### 4. Port mod bootstrap, lifecycle, and registrations

- [ ] Port the mod entry point and lifecycle setup in `FarmUpCraft.java`.
- [ ] Port `setup/Registration.java` and the `registry/` package, including item, data, and biome-related registrations.
- [ ] Replace Forge event-bus, lifecycle, distribution-side, and registry APIs with the NeoForge equivalents.
- [ ] Review loader metadata, mixin configuration registration, and Gradle resource expansion for the NeoForge jar.

**Done when:** NeoForge loads the mod and all required registrations appear without startup errors.

### 5. Port saved data, models, serializers, and database behavior

- [ ] Review the `data/` package and adapt Minecraft-dependent types and codecs.
- [ ] Port `saveddata/` and confirm biome rules and other world state load and save.
- [ ] Port `database/serializers/`, JSON/NBT import/export strategies, backups, and file handling.
- [ ] Port `database/message/` packets and their registration/encoding using the target's networking API.
- [ ] Verify existing data files and test-world saves are handled safely; test on copies, not the only copy.

**Done when:** persistent player, village, market, and world data survives save/restart and networked features work on the target.

### 6. Port gameplay managers and event behavior

- [ ] Port the root managers: `AnimalsManager`, `ChunkManager`, `CookwareManager`, `CropsManager`, `IslandScanManager`, `NpcManager`, `StoneManager`, `StructureManager`, `TreeManager`, `VillagerManager`, and `WoodManager`.
- [ ] Port handlers in `events/`: `CauldronHopperHandler`, `ForgeEvents`, `ModEvents`, and `WorldScheduler`.
- [ ] Recheck event names, event timing, cancellation semantics, server/client execution, and registry lookups.
- [ ] Smoke-test animal, crop, cookware, chunk/plot, structure, tree, village, and NPC behavior.

**Done when:** each gameplay feature behaves correctly in a NeoForge test world and does not run server-only logic on the client.

### 7. Port commands and player-facing messages

- [ ] Port the `command/` package, including registration, Brigadier argument types, permissions, and command source handling.
- [ ] Review message/component construction and any command code that assumes a particular game API.
- [ ] Test core market, player, plot, structure, village, NPC, scan, and zone commands in single-player and on a dedicated server.

**Done when:** commands register, enforce expected permissions, and work on both integrated and dedicated servers.

### 8. Port client UI, rendering, and companion-mod integrations

- [ ] Port client event registration, overlays, rendering, weather/sound behavior, and client-only code in `client/`.
- [ ] Port JEI integration if a compatible target artifact exists; otherwise make it optional or omit it from the NeoForge target.
- [ ] Port JourneyMap integration if its NeoForge API supports the target; otherwise make it optional or omit it.
- [ ] Adapt the existing JourneyMap `ClientPlugin` annotation and waypoint handling to the target API's documented replacements.
- [ ] Check all other companion-mod integrations against their actual target APIs and loader artifacts.
- [ ] Confirm client-only classes are not loaded on a dedicated server.

**Done when:** the client starts without optional integrations, and each supported integration works when installed.

### 9. Revalidate mixins individually

- [ ] Verify `EnchantmentMenuMixin` against the target `EnchantmentMenu` class and click handling.
- [ ] Verify `LevelRendererMixin` against the target rendering method and client rendering lifecycle.
- [ ] Verify `CookwareItemStackMixin` against `ItemStack` durability-bar methods and descriptors.
- [ ] Regenerate or validate the mixin refmap and test injection success at runtime.
- [ ] Prefer a public loader/game API over a mixin if it can implement the same behavior reliably.

**Done when:** all required injections apply exactly once, client/server startup is clean, and the affected features work in-game.

### 10. Port data generation and resources

- [ ] Review Java data generators in `datagen/` and adapt provider/registry APIs.
- [ ] Review `forge-1.20.1/src/main/resources/data/` and `forge-1.20.1/src/generated/resources/` for game-version-specific JSON schemas and identifiers.
- [ ] Update target-specific `pack.mcmeta`, mod metadata, tags, recipes, and other resource formats as required.
- [ ] Run data generation and compare its output with the resources packaged in the NeoForge jar.

**Done when:** generated and hand-authored resources load without datapack or registry errors.

### 11. Test both targets and release artifacts

- [ ] Build the NeoForge 26.2 jar from a clean checkout.
- [ ] Build the Forge 1.20.1 jar after shared-code or repository-structure changes.
- [ ] Test client startup, dedicated-server startup, a copied test world, persistence across restart, core commands, and required integrations.
- [ ] Check logs for missing mixins, missing dependencies, registry errors, and data-pack errors.
- [ ] Confirm each artifact has the correct loader/game metadata and cannot be mistaken for the other target's jar.

**Done when:** both supported targets build independently and pass their agreed smoke/regression checks.

## Porting guidance

- `net.minecraft.*` imports are not automatically replaced; check whether the class, signature, and behavior still exist in the target game version.
- `net.minecraftforge.*` imports are Forge-specific and generally need NeoForge equivalents or a different implementation.
- `com.mojang.*` imports are mixed: some libraries may remain usable, while game-facing APIs may still change.
- Mixin annotations may remain available, but target classes, method names/descriptors, injection points, and refmaps must be revalidated.
- Companion-mod API compatibility is a separate requirement from Minecraft/loader compatibility.
