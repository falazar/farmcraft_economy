# FarmUpCraft NeoForge 26.2 Port Plan

## Goal and scope

- [ ] Keep the existing Forge 1.20.1 build working.
- [ ] Add a separate NeoForge 26.2 build that produces its own mod jar.
- [ ] Share only code that is genuinely independent of Minecraft and the loader; keep loader-specific code and metadata in each target.

**Confirmed target:** Minecraft 26.2 on NeoForge. The initial isolated target uses NeoForge 26.2.0.93, Java 25, Gradle 9.2.1, and ModDevGradle 2.0.148, matching the official 26.2 MDK.

## Current-code snapshot

The pre-port Forge source tree contained **139 Java files**. Two Java-only
helpers have since moved into `common`, leaving 137 in the Forge target.
The following overlapping import counts describe the original baseline:

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

Each target owns its Gradle configuration, loader metadata, version-specific resources, and platform integrations. Both include `common` as a Java 17 subproject and package its compiled classes in their own mod jar. Common build outputs are isolated under each target's build directory. The common module has no Minecraft or loader dependency.

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
- [x] Check whether compatible 26.2 NeoForge releases exist for JEI, The One Probe, Pam's HarvestCraft 2 (Food Core, Crops, Food Extended, Trees), Biomes O' Plenty, JourneyMap, Villager Names, Collective, and TerraBlender.
- [x] Finalize runtime metadata: all four HarvestCraft modules, BOP, TerraBlender, and GlitchCore are mandatory on both sides; guarded JEI/JourneyMap integrations remain optional client integrations. Test actual pinned versions and reject other Minecraft/NeoForge minor lines.
- [x] Record and pin selected artifact versions in `neoforge-26.2/gradle.properties`.
- [x] Download the selected jars and inspect actual NeoForge metadata; verify mandatory dependencies, embedded libraries, and compatibility with NeoForge 26.2.0.93.
- [x] Build and launch the dependency-enabled development client; all selected companion mods were discovered and reached client resource/sound initialization.

**Done when:** every required dependency has a verified compatible artifact or an agreed replacement.

**Artifact verification and client smoke test:** all four HarvestCraft artifacts
downloaded successfully through CurseMaven. Their embedded metadata requires
Minecraft `[26.2,26.3)` and NeoForge `[26.2.0.88,26.3)`, satisfied by our target.
The Crops jar retains all seven grain IDs used by `AnimalGrainAssigner`.
JEI embeds its required `mezz_config` library; JourneyMap embeds Common
Networking and its API. BOP's required GlitchCore and TerraBlender are declared
explicitly. Development runtime testing found upstream warnings for a missing
HarvestCraft apple texture and malformed BOP Spanish translation; these were
not modified. No missing-dependency or fatal startup error appeared in this
client smoke test. World creation and feature behavior remain unverified.

#### Content dependency research (2026-10-28)

The content-dependency research agent checked Modrinth and then the original
HarvestCraft CurseForge projects through the unofficial cfwidget metadata
mirror. The follow-up found exact Minecraft 26.2 / NeoForge file records for
all four HarvestCraft modules, superseding the earlier Modrinth-only negative
finding. Direct CurseForge file pages returned access errors, but a subsequent
artifact check downloaded all four jars through CurseMaven and verified their
embedded metadata. The dependency-enabled development client also loaded them.

| Dependency | Reported exact-target release | Next step |
| --- | --- | --- |
| Pam's HarvestCraft 2 Food Core | `pamhc2foodcore-NEOFORGE-1.26.2-1.0.0.jar`, CurseForge file `9050309` | Jar metadata verified; loaded in development client |
| Pam's HarvestCraft 2 Crops | `pamhc2crops-NEOFORGE-1.26.2-1.0.0.jar`, CurseForge file `9050315` | Jar metadata and seven grain IDs verified; loaded in development client |
| Pam's HarvestCraft 2 Food Extended | `pamhc2foodextended-NEOFORGE-1.26.2-1.0.0.jar`, CurseForge file `9050328` | Jar metadata verified; loaded in development client |
| Pam's HarvestCraft 2 Trees | `pamhc2trees-NEOFORGE-1.26.2-1.0.0.jar`, CurseForge file `9050321` | Jar metadata verified; loaded in development client |
| Biomes O' Plenty | `26.2.0.0.28` | Jar metadata verified; declared GlitchCore and TerraBlender; loaded in development client |
| TerraBlender | `26.2.0.0.2` | Jar metadata verified; loaded in development client |
| GlitchCore (additional BOP dependency) | `26.2.0.0.0` | Jar metadata verified; loaded in development client |

HarvestCraft file pages (records reported by the cfwidget mirror; direct-page
verification pending):

- [HarvestCraft Food Core](https://www.curseforge.com/minecraft/mc-mods/pams-harvestcraft-2-food-core/files/9050309)
- [HarvestCraft Crops](https://www.curseforge.com/minecraft/mc-mods/pams-harvestcraft-2-crops/files/9050315)
- [HarvestCraft Food Extended](https://www.curseforge.com/minecraft/mc-mods/pams-harvestcraft-2-food-extended/files/9050328)
- [HarvestCraft Trees](https://www.curseforge.com/minecraft/mc-mods/pams-harvestcraft-2-trees/files/9050321)

Other primary metadata sources:

- [Biomes O' Plenty](https://api.modrinth.com/v2/project/HXF82T3G)
- [TerraBlender](https://api.modrinth.com/v2/project/kkmrDlKT)
- [GlitchCore](https://api.modrinth.com/v2/project/s3dmwKy5)

#### Integration dependency research (2026-10-28)

The integration-dependency agent reported matching NeoForge / Minecraft 26.2
releases on Modrinth. These are candidates, not yet installed or runtime-tested
with FarmUpCraft. CurseForge artifact IDs remain unverified.

| Dependency | Reported candidate | Conversion or verification needed |
| --- | --- | --- |
| JEI | `mezz.jei:jei-26.2-neoforge:30.48.0.244` | Agent inspected actual jar metadata: Minecraft `[26.2, 26.2]`, NeoForge `[26.2.0.67,)`, compatible with our pinned loader; runtime integration testing remains pending |
| The One Probe | `26.2_neo-15.0.1` | Check required dependencies and actual API usage before adding |
| JourneyMap | `26.2-6.0.10` | Port integration to the matching API |
| JourneyMap API | `info.journeymap:journeymap-api-neoforge:26.2-2.0.0` | Upstream documents waypoint changes and replacement of `ClientPlugin` with `JourneyMapPlugin` |
| Villager Names | `26.2.0-8.9` | Requires matching Collective |
| Collective | `26.2.0-8.67` | Verify alongside Villager Names |

Sources:

- [JEI release metadata](https://api.modrinth.com/v2/project/jei/version)
- [JEI Maven metadata](https://maven.blamejared.com/mezz/jei/jei-26.2-neoforge/maven-metadata.xml)
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
- [x] Start the dependency-enabled dedicated development server: reached `Done` and listened on port 25565 with the currency registry present. NeoForge's built-in development-mode EULA behavior allowed startup without editing an EULA file.
- [ ] Investigate dedicated-server companion-mod diagnostics (a client-only `SalmonRenderer` load attempt and missing spawn-placement registration); startup succeeded, but this is not yet a clean production-server regression test.
- [x] Attribute those diagnostics using the exact installed jars: JourneyMap's `journeymap.mixins.json` puts `client.SalmonRendererAccessor` in its common mixins array; BOP's Mystic Grove biome adds allays without a spawn placement, while Rainforest/Rocky Rainforest put ocelots in the monster category. FarmUpCraft has no matching renderer/entity references. Upstream fixes and revalidation remain outstanding; do not modify cached jars or suppress the errors.
- [x] Trace retained intermittent registry-startup failures to concurrent registry decoding registering composite-holder callbacks on a shared `HolderSet.Named` unsynchronized `ArrayList` in the pinned NeoForge/Minecraft code. HarvestCraft tree modifiers share the affected biome tags; no FarmUpCraft listener registration was identified.
- [ ] Reproduce the listener-registration race without FarmUpCraft and verify an upstream fix. Three bounded default-parallelism companion-only starts (FarmUpCraft absent, HarvestCraft Trees present) all reached ready and stopped cleanly, so the isolated reproduction is still inconclusive. Another default unit-fixture start with FarmUpCraft present hit the same `apple_placed` exception. No verified fixed 26.2 upgrade is available. Keep `max.bg.threads=1` diagnostic-only, not a production compatibility claim.

**Done when:** the empty/minimal NeoForge target builds and launches on both client and dedicated server.

### 3. Identify and extract genuinely shared logic

- [ ] Review the `currency/`, `data/`, `data/rules/`, and `util/` packages for domain logic that can be separated from Minecraft types.
- [ ] Keep Minecraft values such as `ItemStack`, `BlockPos`, `ResourceLocation`, `Component`, registry objects, and game codecs out of the common module.
- [x] Review `ChecksumUtil.java` and `FileInfo.java` as straightforward Java-only sharing candidates.
- [x] Move `ChecksumUtil.java` and `FileInfo.java` unchanged into `common`, wire both development runtimes and packaged jars, and compile common independently of Minecraft/loader APIs.
- [x] Add four focused shared-utility tests and pass them through both Forge/Java 17 and NeoForge builds.
- [ ] Review `AnimalGrainAssigner.java`, `QueryBuilder.java`, and database file-format strategies; share them only after their model/database dependencies are decoupled.
- [ ] Add focused tests for any extracted pure-Java rules or calculations.

**Done when:** shared code compiles without either loader or Minecraft on its compile classpath, and both targets can consume it.

### 4. Port mod bootstrap, lifecycle, and registrations

- [ ] Port the mod entry point and lifecycle setup in `FarmUpCraft.java`.
- [x] Port the currency models, coin registry key/registration, bronze coin resource, and five currency codec/behavior tests; tests pass on the NeoForge target.
- [x] Port the four crop-rule classes, serializer IDs, and crop datapack registry registration while preserving coin registration; initial standalone crop tests pass.
- [x] Enable ModDevGradle's official FML-aware JUnit support and matching NeoForge test framework instead of mocking the Minecraft loader.
- [x] Validate item/dispatch codecs and actual datapack registries using the supported server fixture: loaded bronze coin value 10, coin/crop registry keys, serializer registrations, and legacy JSON decoding.
- [x] Exercise world-dependent `getCropItems` during real development-server startup: 105 biome entries received nonempty crop selections. Verify those ordered lists, rule definitions, and the reverse index remain unchanged after restart and a clean save/stop.
- [ ] Add automated in-world/GameTest coverage for crop-cache lifecycle and dimension routing; the ephemeral registry fixture does not supply a `ServerLevel`.
- [x] Add and execute three actual-world crop tests for startup caches, the focused production tag-refresh listener, and independent Nether/End SavedData stores versus intentional overworld routing. The refresh test invokes the production listener directly with real registries; it does not simulate a full datapack reload or broadcast a synthetic completed-reload event to unrelated listeners.
- [ ] Port `setup/Registration.java` and the `registry/` package, including item, data, and biome-related registrations.
- [ ] Replace Forge event-bus, lifecycle, distribution-side, and registry APIs with the NeoForge equivalents.
- [ ] Review loader metadata, mixin configuration registration, and Gradle resource expansion for the NeoForge jar.

**Done when:** NeoForge loads the mod and all required registrations appear without startup errors.

### 5. Port saved data, models, serializers, and database behavior

- [ ] Review the `data/` package and adapt Minecraft-dependent types and codecs.
- [ ] Port `saveddata/` and confirm biome rules and other world state load and save.
- [x] Port `BiomeRulesData`, `BiomeRulesInstance`, and `BiomeRulesManager` using 26.2's codec-based `SavedDataType` and registry-aware storage; retain legacy codec fields and the biome-rules NBT payload shape.
- [x] Isolate biome-rule and reverse-item maps per saved-data instance instead of sharing static server-world state; track removals and lazy crop selection as dirty.
- [x] Pass seven biome-persistence tests covering legacy codec/NBT shape, registry-backed disk roundtrips, malformed input, and independent stores.
- [x] Port `CropBlockData` / `CropItemData` and all three crop/biome JSON reload listeners. Wire server startup, tag reload, and shutdown; fix the direct-item branch's null-tag lookup.
- [x] Pass six crop-data tests covering both selector forms, required legacy fields, invalid definitions, actual resource/tag loading, population flags, and stale-index clearing.
- [x] Verify actual development-world persistence across restart: 105 biomes, 198 indexed items, and 3,084 ordered crop selections survive unchanged. The final run saved and stopped cleanly.
- [ ] Implement and test explicit import/migration of legacy saved-data files. Preserving the payload does not automatically move Forge's old file into 26.2's dimension/namespaced storage paths; do not upgrade the only copy of a world.
- [x] Add an explicit operator-only, read-only `/farmupcraft-legacy inspect` preflight for a copied legacy overworld `data` directory. Fifteen fixture cases cover ten required legacy envelopes, canonical path/alias safety, validation and nonmutation: twelve pass and three platform-dependent cases skip on this Windows environment (colon filenames and unavailable symbolic-link privileges). The run used process-scoped diagnostic single-thread background loading after another default startup hit the known upstream race. Skipped cases are not verified coverage.
- [ ] Implement a safe atomic install/rollback lifecycle before enabling legacy `apply`. It intentionally returns blocked/failure without writes; existing destination files are rejected and preflight cannot certify unsaved live caches. No original save or active-world data is imported automatically.
- [x] Review the exact restart boundary and offer the migration workflow choice. The user chose to keep migration preflight-only and prioritize visual/compatibility acceptance. Do not enable import writes; the proposed pre-level staged installation remains deferred.
- [x] Port all 13 active database serializers, preserving legacy NBT wrappers, coordinates, UUID layout, and current/legacy version envelopes. Leave the entirely commented-out experimental compressed serializer inactive.
- [x] Introduce the NeoForge target's standalone `DataSerializer` contract with explicit `HolderLookup.Provider` context for both encoding and decoding, instead of coupling serializers to a concrete `DataBase` or requiring a live `Level`.
- [x] Reject malformed, unsupported-version, fractional, overflowing, or partially decoded values explicitly rather than silently defaulting or dropping collection entries. Support lossless numeric/UUID conversions needed by ordinary JSON import.
- [x] Port all seven JSON/NBT file-format types with UTF-8 JSON, compound-root validation, exact gzip NBT roundtrips, and explicit IO failures. Ordinary JSON retains values but cannot preserve every NBT numeric width or array tag kind.
- [x] Test every active serializer through both actual JSON and gzip NBT files, including long precision/extremes, small-component UUIDs, nested collections, registry references, version envelopes, and currency codecs.
- [x] Port `ChunkData`, `NpcData`, `GameStructureData`, `GameZone`, `WorldData`, `VillageData`, `VillagerRecord`, and `CodecUtils`; verify legacy JSON/NBT fields/defaults, mutations, UUID/coordinate formats, zone boundaries, and village/world collections.
- [x] Port the database core/access/builder/manager/query classes with registry-aware saved data, atomic full loads/imports, indexes, expiry, transactions, and executor shutdown. Register all nine original database schemas and their synchronization settings.
- [x] Port `PlayerData`, `GoodsData`, `MarketData`, and `MarketDataJsonManager`; preserve legacy fields/defaults and the effective lowercase `goods_data_database_new` ID. Keep the market loader inactive, matching the old commented-out registration.
- [x] Port database backup scheduling, per-world paths, export/checksum handling, and managed-file retention. Snapshot on the server thread before async IO, restart workers per server, and defer world-dependent ticks for registry-only fixtures.
- [x] Test all nine database schemas through actual saved-data storage and both JSON/NBT import/export. Verify client-cache isolation, backup retention/errors, query behavior, and atomic rejection of malformed snapshots.
- [x] Start/save/stop and restart the real dedicated development server with all nine databases registered. All nine database files plus the biome-rule manager file retained identical SHA-256 hashes after restart/save/stop; the new database files contain empty initial tables, not populated gameplay data.
- [ ] Exercise model methods requiring live entities or a `ServerLevel`, including chunk-owner name lookup, in a real-world/GameTest fixture; registry-only unit fixtures do not cover those methods.
- [x] Replace database messages with named NeoForge clientbound entry/full payloads, client-only handlers, login/dimension snapshots, and disconnect-cache cleanup. Use NeoForge's negotiated packet splitter instead of the legacy custom chunk collector.
- [x] Test payload codecs, defensive NBT copies, a 3 MiB snapshot, malformed input, atomic replacement, empty snapshots, and real registry references.
- [x] Verify actual separate localhost client/server login snapshots, balance 10 to 654321 entry updates, Nether transition with new level/cache identities, one 1,441,504-byte full production snapshot with exact digest acknowledgement, and logout cache clearing. Strict dual-receipt verification passes; both owned JVMs exit cleanly.
- [x] Seed all nine cross-referenced populated tables, save/stop/restart in distinct dedicated JVMs, observe new real scheduled backups in both lifecycles, and restore every cleared table through checksum-validating imports. Independently verify physical hashes, nine SavedData files, and three economy exports.
- [ ] Verify existing data files and test-world saves are handled safely; test on copies, not the only copy.

**Latest validation:** NeoForge `build` plus forced unit reruns discover **528
NeoForge tests: 525 pass, three documented platform skips, zero failures/errors**.
All **fourteen common tests and 60 actual-world GameTests** pass. All 145 NeoForge
top-level production classes and all three common classes are packaged exactly once.
GameTest, network, persistence and visual acceptance helpers/resources are
excluded from the production jar, including the new player-action helpers.
Actual connected database transport and populated restart/backup/restore
acceptance also pass. The combined validation used process-scoped
`-Dmax.bg.threads=1` to avoid an observed intermittent companion-registry
tag-listener startup failure; it is not a production fix. A subsequent
default-parallelism run passed the earlier 32-case world suite, but does not
establish default startup stability. Connected survival movement/break/place
and all nine GUI stages (including installed JEI/JourneyMap) pass with inspected
framebuffers. Startup stability, broader gameplay/client-command coverage,
audible weather, waypoint disk persistence and deferred old-world migration
remain outstanding; see the detailed later acceptance entries.

**Quick parity audit follow-up:** The three confirmed gaps are now restored:
28 legacy vanilla recipe definitions (16 active, 12 disabled), the custom wheat
Fortune loot table plus six HarvestCraft garden loot tables, and the original
`/npc createskeleton <name>` literal with `createskleleton` retained as an alias.
Eleven new actual-world cases verify these behaviors. FarmUpCraft explicitly
loads after NeoForge so the latter's compatibility recipe cannot overwrite the
legacy deepslate input/output. Preserve legacy recipe IDs: custom
`stonecutter_*` definitions do not disable differently named vanilla recipes.
The completed work is recorded in `TODO.txt`. A second bounded read-only audit
of defaults/mutators, event and lifecycle wiring, command helper branches,
NPC profile resolution/Ollama activation and non-recipe resource references
found no additional corroborated omission. This is not exhaustive method-body
equivalence or runtime parity certification.

The final full unit/common build and world suite passed in separate sequential
invocations with every helper source set compiled and no exclusions. An earlier
concurrent run passed all new parity cases but hit two existing timing-sensitive
fixtures: the 100 ms database expiry assertion and sheep entity-tick readiness.
No database or sheep production behavior was changed to obtain the passing reruns.
The final jar contains all 28 recipes, seven loot overrides and the claim shader,
147 expected top-level production classes exactly once, and no acceptance
helpers or test instances. A concurrently introduced incomplete cookware
condition was repaired with approval; normal exhaustion coverage passes.

**External HarvestCraft datapack completion:** All 155 original JSONs now have
unique converted counterparts in the NeoForge production jar. This adds the
previously separate 100 tree and 14 garden worldgen definitions, oil recipe,
three migrated `c:` fish/meat tags and two conditional Pipez recipes alongside
the 35 recipe/loot files restored above. All 100 tree definitions have exact
parity checks plus an exhaustive 50+50 inventory check. Garden checks preserve
all 14 definitions and verify all eight installed biome-modifier attachments.
Removed vanilla garden patch types map to HarvestCraft's registered
`original_random_patch`; old tree dirt keys become 26.2 `below_trunk_provider`
rules preserving the original grass/mycelium exceptions. No new biome
modifiers, original-save writes or global workaround changes were needed.
Seven new actual-world cases verify loaded bindings, native generation,
deterministic rarity acceptance/rejection, oil outputs and cookware remainder,
tag binding, and actual optional Pipez crafting. The complete 60-case suite
passes both with Pipez 1.2.32+26.2 and without it. Preserve both XML/log sets in
`build/external-pack-acceptance/{with-pipez,without-pipez}`. Earlier absent-Pipez
attempts exposed intermittent existing sheep/readiness and entity-visibility
fixture failures; these are not claimed resolved. All new conversion cases
passed in those attempts too. The final unit/common build passes separately.
Pipez is a data-only optional dependency ordered before FarmUpCraft; the
`-PwithPipez=true` flag enables its pinned development runtime only.
The installed JEI mismatch was corrected to the tested full 30.48.0.244 jar,
preserving .242 outside the active mods directory. This does not certify the
entire user's modpack startup; a fresh user restart remains necessary.

**Forge external-pack verification:** The Forge resources already contain all
155 original external HarvestCraft datapack JSONs. Compared every source
definition and every corresponding entry in the rebuilt Forge jar: all match
the original semantically, and each occurs exactly once. No additional resource
copies or Forge behavior changes were necessary. The Java 17/Gradle 8.1.1
`build -x copyJar` passes, including reobfuscation and the common checks; the
mod's own pack metadata remains format 15. Corrected the earlier documentation
that incorrectly implied the full pack was absent from Forge. This verifies
packaging, not new Forge in-world generation acceptance. No live mods folder,
Rimfog save or original external pack was changed.

**Daily chores startup defaults:** Both target jars now package the authorized
original chores list through `common` resources. A shared Java 17 `ChoresFile`
helper seeds `config/farmupcraft/chores.md` on actual server startup, copying an
existing instance-relative legacy `Rimfog/chores.md` first or otherwise using
the bundled snapshot. Existing new files, including empty files, are preserved;
copying never requests replacement, and invalid paths/IO failures are surfaced
through startup error logging. Both login and `/village chores` consumers use
the new location. Ten added shared tests pass on both Java 17/Forge and the
NeoForge build, bringing common coverage to fourteen tests. Fresh full NeoForge
validation remains 528 cases (525 pass, three documented skips); all 60 world
tests pass. Before the actual test-server startup both candidate chores paths
were absent; the production startup listener created the new file with bytes
matching the original and bundled resource exactly. Both rebuilt jars contain
the resource once; NeoForge has 148 top-level production classes and Forge has
139 (its wholly commented legacy serializer does not produce a class). No
acceptance helper/resource leakage or live-instance deployment occurred.

**Done when:** persistent player, village, market, and world data survives save/restart and networked features work on the target.

### 6. Port gameplay managers and event behavior

- [x] Port the root managers: `AnimalsManager`, `ChunkManager`, `CookwareManager`, `CropsManager`, `IslandScanManager`, `NpcManager`, `StoneManager`, `StructureManager`, `TreeManager`, `VillagerManager`, and `WoodManager`.
- [x] Port `AnimalsManager` and `AnimalGrainAssigner`, preserving feeding/breeding/milking/shearing rules and actual HarvestCraft grain IDs. Use 26.2's wool color collections, entity types, and animal APIs.
- [x] Port `ChunkManager` and `CauldronHopperHandler`, including plot permissions, height protection, spawn/drop policies, death tracking, and refinery bucket automation. Use level-aware database access and notify clients when canceling server-only block breaks.
- [x] Port `StoneManager`, `WoodManager`, and `TreeManager`, retaining success-rate boundaries, nursery/orchard behavior, sapling/cocoa biome restrictions, fruit registry IDs, and pickup milestones.
- [x] Port `StructureManager` and its `AIManager` / `OllamaService` support, including structure/chest lookup, loot notes, registry APIs, and server-bound cache/executor lifecycle. AI transport/parser tests are offline; no external AI service was contacted during validation.
- [x] Port `VillagerManager`, including fall immunity, alerts, stuck/bell tracking, reincarnation, health, and permanent-child rules. Clear transient state between servers and surface database errors rather than silently returning ordinary lookup results.
- [x] Port `CookwareManager` with data-component-backed use counters, all nine actual HarvestCraft tools, kitchen budgets, and warnings. Keep ingredients intact until recipe lookup and suppress only an exhausted tool's remainder to avoid breaking recipe consumption.
- [x] Port remaining `CropsManager`, `IslandScanManager`, and `NpcManager` gameplay consumers, including biome/growth rules, bounded server-thread scans, conversation/profile loading, retries, and shutdown cancellation. Their focused tests pass without contacting external AI.
- [x] Port handlers in `events/`: `CauldronHopperHandler`, `ForgeEvents`, `ModEvents`, and `WorldScheduler`. Create/backfill player records before login snapshots; retain chores/reminders, hourly upkeep, and retry scheduling. Only mark a daily market refresh successful after it returns success.
- [x] Port the database registration/lifecycle portion of `ModEvents`, including startup, shutdown, cache cleanup, and level-aware consumer access.
- [x] Add gameplay lifecycle cleanup for plot/structure/villager state and the restartable AI worker.
- [x] Add a development-only GameTest source set and `runGameTestServer`, with eight passing actual-world tests: refinery transfer, required plot, missing bucket, full-output drop, villager fall immunity, cross-dimension database routing, command registration/permissions, and economy save/export. Repeated notifications are asserted within the refinery tests.
- [x] Exercise real `save-all flush` exports for all three economy JSON files, including decoding a temporary player's exact 137-coin balance. Export before database shutdown and skip final save callbacks that would otherwise recreate closed caches. Populated restart/migration acceptance remains separate.
- [ ] Complete actual-world coverage for animal actions, mining/tree drops, plot ownership/visitors, structure loot, villager reincarnation, and crafting/menu behavior. Rule/codec tests do not establish all live event or UI effects.
- [ ] Recheck event names, event timing, cancellation semantics, server/client execution, and registry lookups.
- [ ] Smoke-test animal, crop, cookware, chunk/plot, structure, tree, village, and NPC behavior.

**Done when:** each gameplay feature behaves correctly in a NeoForge test world and does not run server-only logic on the client.

### 7. Port commands and player-facing messages

- [x] Port the `command/` package and `DataBaseCommand`, including a single registration path, Brigadier argument types, modern permission sets, and command source handling. Preserve all fourteen server roots and test their registration in an actual world.
- [x] Pass focused command tests for permissions, public branches, source handling, inventory-component preservation, pricing/overflow boundaries, village upkeep selection, scan bounds, and database restore validation. Initialize fixtures on the real server thread rather than weakening lifecycle guards.
- [ ] Review message/component construction and any command code that assumes a particular game API.
- [ ] Test core market, player, plot, structure, village, NPC, scan, and zone commands in single-player and on a dedicated server.

**Done when:** commands register, enforce expected permissions, and work on both integrated and dedicated servers.

### 8. Port client UI, rendering, and companion-mod integrations

- [x] Port client event registration, player-data HUD, claimed-chunk rendering, `/frecipe`, weather muting, and crop tooltips. Use public render-state/custom-geometry events and client-only subscribers; retain immutable geometry and shared-wall suppression.
- [x] Test HUD labels, border selection/negative coordinates/geometry/colors, null-player tooltip filtering, and exact weather-volume restoration. Launch a fresh development client through resource/sound initialization, verify its responsive window, and close it gracefully.
- [x] Inspect actual connected game-framebuffer captures of the HUD, visible blue claim walls, wrapped crop tooltip, cookware bar, and real PauseScreen. Verify weather option/file muting and exact restoration; this does not assert audible weather.
- [ ] Complete remaining connected client-command, audible weather, and JourneyMap restart/disk-persistence acceptance. The tested GUI flow below is not blanket coverage of every GUI action.
- [x] Port optional JEI integration and verify actual production-payload OUTPUT focus opens visible bread recipe layouts in the connected client.
- [x] Port optional JourneyMap integration and verify actual production-payload waypoint manager/editor save, rendered highlight/village polygons, and cleanup state in the connected client.
- [x] Convert the basic JEI runtime-holder plugin and JourneyMap client-plugin bridge to the target APIs; command/packet consumers are also ported. Connected UI and in-world rendering acceptance remain pending.
- [x] Pass four plugin-identifier/lifecycle tests and confirm JourneyMap discovers the converted plugin during development-client startup.
- [x] Adapt the existing JourneyMap `ClientPlugin` annotation and waypoint handling to the target API's documented replacements.
- [x] Replace waypoint add/remove/scan messages with validated named NeoForge payloads and separate client-only handlers. Adapt JourneyMap v2 waypoint construction, colors, persistence, ownership, filtering, and logout cleanup; test codecs and API-facing selection behavior.
- [x] Port highlight/village-chunk and JEI recipe-opening payloads with validated codecs, client-only handlers, JourneyMap v2 polygons, and the actual JEI output-focus API. Test replacement/refresh/cleanup and recipe-opening calls. Use the full JEI mod in the test runtime to avoid API/Minecraft classloader splits.
- [ ] Verify waypoint rendering/editing/removal and payload delivery with an actual connected client; dedicated-server and codec coverage do not verify the map UI.
- [ ] Check all other companion-mod integrations against their actual target APIs and loader artifacts.
- [ ] Confirm client-only classes are not loaded on a dedicated server.

**Done when:** the client starts without optional integrations, and each supported integration works when installed.

### 9. Revalidate mixins individually

- [x] Verify and register `EnchantmentMenuMixin` against the actual 26.2 click callback. Reserve payment only after vanilla request/enchantment-list validation, commit after item application, and refund an uncommitted reserve on scope exit.
- [x] Pass seven enchantment GameTests covering real-menu payment, insufficient coins, creative exemption, rejected requests, village surcharge/debt, and reservation-refund idempotence. Assert actual enchantments, balances, lapis, and XP where applicable. Exceptional-item rollback acceptance remains pending.
- [x] Replace `LevelRendererMixin` with public render-state extraction/custom-geometry events, avoiding an injection into the removed weather method. Geometry tests and dedicated-server/client startup pass; actual connected framebuffers now visibly verify claim walls.
- [x] Verify `CookwareItemStackMixin` against `ItemStack` durability-bar methods and descriptors.
- [x] Port the cookware mixin to verified 26.2 bar methods and the inherited crafting-remainder contract. Register it in NeoForge metadata and verify real mixin application, durability values, preserved HarvestCraft remainder data, exhausted tools, and unchanged vanilla remainders in FML tests.
- [x] Verify actual HarvestCraft crafting-menu consumption and remainder handling, exhaustion without a returned tool, preserved custom data, and kitchen tool budgets in two world tests.
- [ ] Visually verify cookware bars and crafting on a connected development client.
- [ ] Regenerate or validate the mixin refmap and test injection success at runtime.
- [ ] Prefer a public loader/game API over a mixin if it can implement the same behavior reliably.

**Done when:** all required injections apply exactly once, client/server startup is clean, and the affected features work in-game.

### 10. Port data generation and resources

- [x] Review and port all active Java generators/providers using current server GatherDataEvent, lookup futures, tags, and codec-backed output. Thirteen focused provider tests pass; keep the empty legacy market template empty rather than inventing output.
- [ ] Review `forge-1.20.1/src/main/resources/data/` and `forge-1.20.1/src/generated/resources/` for game-version-specific JSON schemas and identifiers.
- [ ] Update target-specific `pack.mcmeta`, mod metadata, tags, recipes, and other resource formats as required.
- [x] Migrate the six existing generated crop/biome rule definitions and seven supporting tags into NeoForge resources. Use singular `tags/block` / `tags/item` directories and replace the old `forge:is_desert` reference with `c:is_desert`; verify real HarvestCraft/Biomes O' Plenty registry resolution.
- [x] Run all standalone data providers and semantically compare all nineteen fresh JSON outputs. Promote only the reviewed new food tag and updated BOP overworld snapshot; verify packaged counts of 1,056 foods and 59 dimension-correct biomes.
- [x] Resolve unbound virtual tags and missing installed datapack registries without suppressing validation or rebinding global components. Preserve 95 crop harvests plus six gardens; test exact output uniqueness/order to prevent duplicate accumulation. Seventeen focused generator/classification tests pass.

**Done when:** generated and hand-authored resources load without datapack or registry errors.

### 11. Test both targets and release artifacts

- [ ] Build the NeoForge 26.2 jar from a clean checkout.
- [x] Build the current working-tree NeoForge jar and freshly run 389 NeoForge tests plus four common tests with zero failures/errors/skips, and 21 actual-world GameTests. Verify all 143 NeoForge top-level production classes plus two common classes are packaged exactly once, with all development acceptance code/resources excluded. See the latest-validation caveat above.
- [x] Subsequently expand and run all 25 actual-world GameTests with default background parallelism: zero failures/errors. Four new tests cover house-owner breaking/loot, visitor invitation/revocation/membership, stone-drop gating, and nursery leaf drops through production interaction/event paths. This successful startup does not resolve the intermittent upstream registry race; the earlier 389+4 unit-test result is unchanged.
- [x] Subsequently execute all 32 actual-world GameTests with default background parallelism: zero failures/errors. Add the three crop/dimension tests above and four villager cases for lethal-damage reincarnation identity/NPC metadata, disk-loaded entities remaining unchanged, unclaimed spawn/death isolation, and forever-kid aging protection. These results do not replace pending visual checks, full reload/restart lifecycle coverage, or the upstream startup-race fix.
- [x] Final integrated build: 407 NeoForge tests discovered (404 passing, three documented platform skips, zero failures/errors), four passing common tests and 33 passing actual-world GameTests. Three API-absent JUnit cases plus one real-level feedback case cover isolated optional-integration handler boundaries, not complete absent-mod graphical startup. The run uses process-scoped diagnostic background loading. Package all 145 NeoForge plus two common top-level production classes exactly once and exclude every acceptance helper/resource.
- [x] Subsequently run all 38 gameplay GameTests with zero failures/errors using process-scoped diagnostic background loading. Five new cases cover structure-note contents/cache idempotence, visited-barrel break loot, hoe plot enforcement/durability, berry planting/consumption, and crop biome enforcement/disabled-rule behavior. This focused run excludes compilation of the independently unfinished visual helper source set; validate that source set separately before claiming a complete combined build.
- [x] Compile the completed gated visual helpers and rerun the full combined build/unit/common/world checks without exclusions: all 38 world tests pass. No production source changes are needed for the five additional gameplay cases.
- [x] Launch actual separate dedicated-server and graphical-client peers with JEI, JourneyMap and their APIs removed. Strict runtime FML inventories retain FarmUpCraft/required content and reject optional-mod/API leakage; the production connected login/update/dimension/full/logout receipts pass on loopback 25580 and both peers stop cleanly. The run uses scoped diagnostic background loading.
- [x] Capture four real game-framebuffer screenshots with hashes and ordered client/server acknowledgements for HUD, crop tooltip, cookware and a real PauseScreen; verify weather option/file muting and restoration. Both visual peers stop cleanly. Machine completion is not blanket visual acceptance: HUD/tooltip/cookware are visible, but the first border framing does not clearly show the border and needs a better capture; audible weather and full JEI/JourneyMap UI remain unverified.
- [x] Resolve actual invisible claim walls without raising legacy opacity: preserve low-alpha forcefield fragments in a custom shader and subdivide full-height quads into 16-block sections to correct interpolated vertex fog. Rerun the real connected four-stage harness; inspected framebuffers show clear blue walls, HUD, tooltip, cookware bar and PauseScreen. Verify both peers exit zero and acceptance ports are released. Audible weather and full JEI/JourneyMap UI remain unverified.
- [x] Rebuild after the renderer fixes and freshly run all 409 NeoForge tests (406 passing, three documented platform skips), four common tests and 38 world tests. Verify all 145 NeoForge plus two common top-level classes occur exactly once in the production jar, the custom shader is packaged, and acceptance helpers (including same-package visual probes) are absent. This run uses process-scoped diagnostic background loading and does not resolve the intermittent startup race.
- [x] Correct the structure/crop interaction fixture's required village ID and matching player membership, retaining fixture cleanup. Rerun all 38 world tests successfully and inspect the server log: no entry-synchronization failures, codec encoding errors or null-pointer exceptions remain. Existing unrelated spawn-registration and mock-button diagnostics are not claimed resolved.
- [x] Run actual connected non-operator survival movement, dirt breaking and placement in a unique disposable wilderness fixture on loopback 25581. Forward-key input moves the player from x=32.5 to x=34.674; vanilla attack input and client `useItemOn` produce server-observed break/place events and authoritative block changes, consuming held dirt from three to two. Ordered acknowledgements verify both peers' final state; both exit zero and ports are released. The regular-world TODO remains pending, not silently substituted by this disposable test.
- [x] Recheck the startup-race source chain and public upstream status: pinned `HolderSet` callback registration uses unsynchronized `ArrayList.add`, while registry decoding runs in parallel with shared lookup state. No exact released fix was found; the checked 26.2 line remains `.93` and the inspected upstream patch retains unsafe registration. This strengthens the source hypothesis but does not establish isolated runtime attribution or resolve startup stability. No dependency/workaround changes were persisted.
- [x] Add and execute four real-level command/upkeep regressions: non-operator rejection of seven sensitive commands without state mutation, treasury deposit/withdrawal and insufficient-funds behavior, exact upkeep timer/debt/member/offline/idempotency boundaries, and ownership-filtered structure income. All 42 world tests pass with all helpers compiled and no compile exclusions. Scheduler tests invoke the production listener directly with primed elapsed time; they do not claim global event-bus registration or full mock-player login coverage. No synchronization/config-sync/null-pointer fixture failures remain.
- [x] Extend and complete real connected visual acceptance to nine ordered stages (final mask 511). Inspect all nine framebuffers: JEI bread recipes, native/scanned JourneyMap waypoints, native editor rename/save, and visible adjacent red/blue polygons join the original four stages. Preserve first-open AboutScreen handling through its real Close callback. Require exact ARGB geometry/style and supported player-centered viewport at zoom 2048/four pixels per block; both full chunks project to 64x64 pixels. Independent edge-pixel checks confirm the red/blue outlines, excluding labels/player icon. Production cleanup removes observed polygon handles and fixture waypoints; final waypoint manager is empty. All nine hashes match receipts and both peers exit zero. No post-clear map framebuffer, waypoint disk persistence, or audible weather claim.
- [x] Final combined build and forced unit/common/world rerun after all fixture corrections: 409 NeoForge tests discovered (406 pass, three documented platform skips), four common tests and 42 world tests pass without compile exclusions. The production jar still contains only the 147 expected top-level production classes plus normal nested classes/resources; all acceptance helpers are excluded.
- [x] Independently build and test a fresh NeoForge working-tree source-only snapshot offline, with no copied project outputs/worlds. Reuse global dependency/build caches; reproduce the same 407/4/33 results and three documented platform skips with scoped diagnostic loading. A committed clean-checkout build remains a separate release task.
- [x] Build the preserved Forge 1.20.1 target with Java 17 and Gradle 8.1.1 (`build -x copyJar`); reobfuscation and common tests pass. Explicitly exclude the deployment finalizer; no live mod directory is modified.
- [x] Independently rebuild Forge from a fresh source-only temporary snapshot with no project build/cache outputs, using `build -x copyJar --offline`. All 15 actionable tasks execute successfully, including compilation, mixin refmap generation, reobfuscation, and common tests; cached dependencies are reused without network access. This is a working-tree source snapshot, not a committed clean-checkout claim.
- [ ] Test client startup, dedicated-server startup, a copied test world, persistence across restart, core commands, and required integrations.
- [ ] Check logs for missing mixins, missing dependencies, registry errors, and data-pack errors.
- [x] Inspect both packaged jars: Forge has only `META-INF/mods.toml` with Forge and Minecraft `[1.20,1.21)` dependencies; NeoForge has only `META-INF/neoforge.mods.toml` with NeoForge and Minecraft `[26.2]` dependencies. Neither contains unexpanded metadata placeholders or development acceptance classes/test-instance resources; artifact names distinguish the targets.

**Done when:** both supported targets build independently and pass their agreed smoke/regression checks.

## Porting guidance

- `net.minecraft.*` imports are not automatically replaced; check whether the class, signature, and behavior still exist in the target game version.
- `net.minecraftforge.*` imports are Forge-specific and generally need NeoForge equivalents or a different implementation.
- `com.mojang.*` imports are mixed: some libraries may remain usable, while game-facing APIs may still change.
- Mixin annotations may remain available, but target classes, method names/descriptors, injection points, and refmaps must be revalidated.
- Companion-mod API compatibility is a separate requirement from Minecraft/loader compatibility.
