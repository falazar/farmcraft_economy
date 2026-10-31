# FarmUpCraft

FarmUpCraft is a Minecraft economy and farming mod with player currency,
villages, plots, markets, crop rules, and server-side management tools. The
repository contains a Forge 1.20.1 target and an in-progress NeoForge 26.2
port; feature parity between the two targets is not guaranteed.

## Features

- **Economy and trading:** player and village coin balances, village fees and
  upkeep, a configurable market, buying and selling, and coin payments for
  enchanting.
- **Villages and land:** create and manage villages, invite members, manage
  plots and visitors, show claimed-chunk borders, and inspect village farms,
  animals, structures, and chests.
- **Farming and crafting:** crop and biome planting rules, crop/biome lookup
  tools, HarvestCraft-aware content, and cookware with use limits.
- **World and gameplay systems:** animal, crop, tree, wood, stone, NPC,
  structure, chunk, and island-scan management; the exact systems available
  depend on the target and its installed companion mods.
- **Client integrations:** a player-data HUD, claim/village map features,
  waypoint tools, recipe lookup, crop tooltips, and weather-sound controls.
  JEI and JourneyMap integrations require those companion mods.
- **Persistent server data:** player, village, chunk, market, NPC, world, and
  other mod data are saved and synchronized to clients. Database export,
  restore, query, and (where enabled) managed backups are available to
  operators.

NeoForge is still a partial port, not a feature-complete replacement for the
Forge target. Its current port status, test coverage, known limitations, and
legacy-world migration warning are documented in the development notes below.

## Commands

Commands are run in-game with the slash prefix. The following index covers the
main command roots and their commonly used actions; argument names, permission
requirements, and available branches can vary by target. Use Minecraft's
command suggestions for the exact syntax in your installed build. Administrative
and testing branches are permission-gated where appropriate.

| Command | Main actions |
| --- | --- |
| `/farmupcraft help` | Player-facing command guide without administrative commands; `/farmcraft help` is also supported. |
| `/farmcraft` | `map`, `waypoint`, `chunk`, `here`, `village`, `frecipe`, settings, scheduler, and admin utilities. |
| `/player` | `info`, `show`, `border`, `color`, village join/leave, and player/village coin administration. |
| `/market` | `info`, `show`, `buy`, `sell`; operator actions include market configuration, price changes, and daily refresh. |
| `/village` | `info`, `list`, `buy`, `rename`, `levelup`; member invitations, upkeep, farms, animals, structures, chores, and map display. |
| `/plot` | `info`, `buy`, `delete`, `reclaim`, `list`, `upgrade`; manage owners, types, and visitors. |
| `/npc` | Find, claim, and list NPCs; manage profiles and names; initialize villagers. |
| `/scan` | `islands`, `point`, and `clear` for island scans and scan points. |
| `/show` | `biomes`; `/show biomecrops` lists plantable crops for the biome you are standing in, or supply `<biome>` to look up another biome. `/show cropbiomes <crop>` lists suitable biomes. Both crop lookups appear in `/farmupcraft help`. |
| `/structures` | `find`, `list`, `detail`, `chests`, `inside`; manage visited-state markers. |
| `/zone` | `list` and `rename` configured zones. |
| `/managers` | Administrative `clear` and `repopulate` maintenance operations. |
| `/ai` | `test` AI-related behavior. |
| `/database` | Operator `export`, `restore`, and `query` tools for persisted mod data. |
| `/tester` | Development and diagnostic commands; not intended as normal player gameplay. |
| `/farmupcraft-legacy` | NeoForge legacy SavedData preflight. `inspect` only checks a copied legacy data directory; `apply` is deliberately blocked and does not import data. |

### Player help and login welcome

On login, players see "Welcome to FarmUpCraft!" and a pointer to
`/farmupcraft help` during their first five calendar dates, counted using the
server's local date (first login date plus the next four dates). The start date
is saved per player and does not reset on restart or reconnect. Existing players
start their window on their first login after this update. This works in both
targets, on dedicated servers and in single-player worlds.

### Initial market setup

Underlined market items open their recipes in JEI when clicked. These links use
plain-text tooltips with the item ID and a click hint, avoiding item tooltips
that can intercept chat clicks. Items without crafting recipes keep item tooltips.

Both targets automatically import the bundled item lists for empty market
categories at server startup and activate an initial selection when a category
has no active goods. Existing goods, prices, and sales counts are preserved;
restarting does not trigger daily rotation or price increases. Items from
companion mods that are not installed are skipped with a warning.

Operators can manually import missing items with `/market admin importall`,
or just one category with `/market admin importtxt <type>` (`food`, `wood`,
`stone`, or `general`). These imports add missing goods without overwriting
existing entries. `/market importall` is not a valid command.

### Naturalist raw duck ingredients

Naturalist raw duck (`naturalist:duck`) counts as raw meat and raw chicken for
HarvestCraft recipes in both versions, including the nested chicken ingredient
tags. Entries are optional, so Naturalist is not required. Cooked duck is unchanged;
recipes requiring the exact vanilla chicken item rather than a tag are unchanged.

### Temporary Ice and Fire stone-statue compatibility (NeoForge 26.2)

An optional client-only mixin fixes the unofficial Ice and Fire beta's stone-statue
render crash by assigning the render-only hollow entity its owning statue's ID.
Minecraft 26.2 requires an assigned ID when extracting held-item render state.
The model is not spawned or registered, and no statue or saved entity is removed.
The patch is inactive when the targeted renderer is absent; Forge 1.20.1 is unchanged.
This is a temporary workaround for `iceandfire-2.2-mc26.2-unofficial-beta.1`,
not a permanent FarmUpCraft feature. Remove the mixin, its client registration,
`StoneStatueRenderCompat` helper, and `stone_statue_model_id` game test once an
upstream Ice and Fire update fixes ID assignment. Before removal, verify the
updated mod renders the previously crashing statue without this workaround.

### Temporary Ice and Fire dragon-prey compatibility (NeoForge 26.2)

An optional mixin works around a second crash in
`iceandfire-2.2-mc26.2-unofficial-beta.1`: after the prey-shaking animation
passes tick 55, `updatePreyInMouth` casts the client world to `ServerLevel`
to apply bite damage. Only that completion branch is skipped on the client,
before the cast. Earlier mouth positioning/animation and server-side damage,
healing, hunger and passenger release remain unchanged. No entities are removed.
Forge 1.20.1 is unchanged, and the patch is inactive without the targeted mod.
Remove `IceAndFireDragonPreyMixin`, its registration and the
`dragon_prey_bite` game test after verifying an upstream client-side fix.

### HarvestCraft skillet recipe

Both targets override `pamhc2foodcore:tool_skillet` to avoid the spear recipe
collision. Use two copper ingots in the top-left and top-middle slots, with
wooden sticks in the center and bottom-right slots. This crafts one skillet;
the original single-copper layout no longer crafts a skillet.

### One-time gameplay defaults

Both JARs bundle the old Spice of Life: Onion 42-food progression. When Onion is
installed, FarmUpCraft merges the tracking limit and eight rewards into its global
config during mod construction, before Onion initializes. Other options are kept.
The original config is backed up under `config/farmupcraft/`; the
`solonion-progression-v1.done` marker there prevents overwriting later admin edits.
This applies to all worlds in that instance, including dedicated servers.
Onion remains optional; installing it later triggers the setup on the next launch.

Each existing or new world also receives random tick speed **1** and sleeping
percentage **50** once on server startup. A marker at
`<world>/data/farmupcraft-startup-rules-v1.done` preserves later gamerule changes.
Do not delete these markers unless you want to reapply the defaults.

### Structure visits and claims

Both targets require a structure to be visited before it can be marked claimed.
Buying a village claims territory, not its structures: only the village-center
chunk counts as a purchased plot at founding. A visited structure's saved center/
marker chunk must itself be a purchased (blue) plot to count as claimed.
Purchased chunks overlapping only the structure's edges do not count. Discovering or scanning
an unvisited structure never claims it; visiting it later on an already-bought
plot marks it visited and claimed. Creative-mode exploration still does not
mark structures visited.

Structure scans and global/village structure lists correct and save stale claim
flags using these rules without changing visit history. Ordinary village
territory does not count as a purchased plot.

### Zombie and skeleton torch breaking

Both targets let vanilla zombies and skeletons seek out and break normal and
soul torches (standing or wall-mounted). Redstone torches, lanterns and other
blocks are not targets. Enabled by default; operators can toggle it immediately
with `/farmcraft admin settings mobTorchBreaking true` or `false`. The setting
is saved per world and shown by `/farmcraft admin settings`.

Idle mobs search at most once every 100 ticks (about five seconds), normally
within six blocks. Exactly 1% of searches use a sixteen-block radius instead.
They require a visible, reachable torch, walk to it, and break it only within
two blocks. Combat takes precedence. Searches never load chunks and time out
after ten seconds. The behavior respects `mobGriefing` and block-destruction
event vetoes, but does not exempt village claims.

### Crop lookup autocomplete

The crop lookup arguments support Tab completion: `/show biomecrops ` suggests
all biomes registered in the current world, including loaded mod/datapack biomes;
`/show cropbiomes ` suggests crop item IDs from the configured biome crop rules.
Namespaced IDs can be entered directly without quotes.
`/show biomes <x> <y> <z> <radius>` scans surface biomes at each column's
motion-blocking height (ignoring leaves), not underground biomes at Y=0.
The supplied Y coordinate does not reveal caves beneath the scanned terrain.

### Villager sleep healing

In both targets, living villagers heal to their own maximum health immediately
when they enter bed to sleep, including villagers with boosted maximum health.
This does not require village membership. Remaining asleep does not continuously
heal damage; players and other sleeping entities are unaffected.

### Moving walls and buildings

Both targets provide `/build pull_wall <distance>` and
`/build push_building <distance>` to all players who can build. Run the command,
left-click one bottom corner, then right-click the other bottom corner. The two
blocks must be at the same height on a straight X/Z line. Stand in front of the
wall: pull moves toward your side, push moves away. The second click creates a
preview only: red particle outlines mark the source and green outlines mark the
destination, visible only to you and refreshed while awaiting confirmation.
These are bounding boxes, not translucent ghost blocks, and particles can be
obscured by terrain. Chat shows the dimensions, non-air block count, price, and
clickable Confirm/Cancel actions. Run `/build confirm` (or click Confirm) to move;
`/build cancel` cancels without charging. Starting another build command replaces
the old preview. Selections/previews expire after two minutes or on logout/
dimension change. Remaining particles fade naturally when canceled or completed.

The wall is one block deep and 15 blocks tall including the bottom row.
Buildings are also 15 blocks tall; depth is found by tracing back along both
bottom edges, stopping before either edge first reaches dirt, plain stone, or
air, with a maximum depth of 20 blocks including the front row. This is a
rectangular selection, not an automatic outline of the building.

Each successful move costs 1 gp (one wallet coin) per non-air source block,
regardless of movement distance. Creative-mode moves are free and do not require
a wallet; previews show the actual block count and a zero cost. Switching between
creative and survival requires a new preview because the price changes.
The total is displayed after selection; an
insufficient balance cancels the move without changing blocks. Failed or canceled
moves are not charged. Successful moves deduct and save the cost and display the
remaining balance. Confirmation rechecks permissions, funds, loaded chunks,
world limits, and destination block entities. Any change to source/destination
blocks, block entity data (including inventory), or traced depth boundary
invalidates the preview and requires reselection. Walking around the building
does not change the previewed movement direction.

Building and wall widths are both limited to 20 blocks, including both selected corners.
Both push and pull movement distances are limited to 1-10 blocks per operation.
Both corners must be within 64
blocks of the player. All affected blocks must be loaded and inside the world
border/height limits. Source blocks move rather than duplicate, including block
entity inventories/data. Overlap is supported. Destination blocks (including
those under source air) are overwritten, but a destination block entity outside
the source region cancels the entire operation to avoid deleting its contents.
Every affected block is preflighted for break/place events, normal editing
permissions, and the existing village/house protections before moving anything.
Permission probes skip FarmUpCraft's harvesting rolls, bonus drops, and chest/
spawner traps: moving a block is not harvesting it.
Entities and claim ownership are not moved.

Village map overlays automatically turn on when a player founds a village,
accepts a village invitation, or is added to a village by an administrator while
online, in both targets. Use `/village map clear` to hide the overlay or
`/village map` to show it again. Each player's on/off choice is saved and restored
on login, including after a server restart. Clearing all map overlays also saves
the village map as off; plot refreshes respect that choice. Existing player
records without a saved choice default to off until enabled. Village upgrades
refresh overlay tiles for all online members whose village map is enabled,
including the newly added territory. JourneyMap is
required to display the overlay.
The village center always uses purchased-plot blue, including when its chunk
record is missing or still marked as ordinary village territory. Other ordinary
village territory stays green; purchased plots are blue.

`/show biomecrops` and `/show cropbiomes` autocomplete short registry names
(for example `plains`, `orchard`, `onion`, and `wheat`) while resolving full IDs
internally. Ambiguous short names remain namespaced in suggestions. Explicit
namespaced IDs and existing command aliases remain accepted in both targets.
`/show plotcrops` lists every biome across the current 16x16 chunk at the
player's farmland height (feet minus one), followed by one combined, sorted,
deduplicated crop list. Green means a matching crop or seed is in inventory;
yellow means absent. This is informational: ordinary farms still restrict
planting to the local biome, while level 3+ farms allow the chunk's biome union.
Crop-and-seed pairs appear only once in autocomplete; explicit seed IDs still work.
In "Crops you can plant in" lists, crop names are yellow by default and green
when the matching crop/planting item is anywhere in the player's inventory,
including either hand. This applies to
the command and rejected-planting feedback, not the separate biome-name list.
Edible-seed crops retain their crop names (`mustardseeds`, `sesameseeds`); their
extra planting-seed variants do not become duplicate suggestions.

Every five minutes of server ticks, while two or more players are online on Hard
difficulty, all online operators with game-master command permission receive a
reminder to consider `/difficulty normal` for the kids. Difficulty is never changed
automatically. The existing solo reminder to consider Hard remains unchanged.

HarvestCraft harvested crops with a matching plantable seed item can also be
planted directly (for example `kaleitem` and `kaleseeditem`). Placement uses the
seed's normal block-item path with the original held stack, preserving biome
checks, protection events, survival checks, and consumption in both targets.

## Development targets

NeoForge 26.2 adds compatibility recipes for SoL Onion 1.5.5's lunch bag and food book to
replace its invalid `food` crafting category with `misc`. Ingredients and
outputs are unchanged; recipes load only when SoL Onion is installed.
Separate FarmUpCraft recipe IDs preserve the early mod loading needed for
Onion startup defaults. Upstream invalid recipes may still log category errors,
but the compatibility recipes provide the same crafts.

Bee and Naturalist butterfly crop fertilization shares a persisted per-chunk
budget in each dimension: up to five successful one-stage boosts in any 6,000 world ticks
(five minutes at 20 TPS). Butterflies add one stage instead of their usual
2-5 stages. More insects do not increase the shared limit; relogging and server
restarts do not reset it. The limit covers all chunks, not just bought farms,
so moving insects outside a village does not bypass it. Normal random growth
and player-used bone meal are unchanged. Naturalist integration is optional.

Naturalist ducks have their own `/village setanimal duck` breeding category;
goats use the sheep category. Ducks accept either their unchanged native
Naturalist food choices (fish) or one village-specific assigned grain.
The grain assignment is stable and saved without rerolling other species.
Both follow the existing own-village pasture requirement, pasture
animal cap, village species/feed selection, and once-per-real-day breeding
limit. Ducks also qualify for pasture animal drop bonuses. Creative-mode
breeding remains unrestricted.

The plot spawn filter exempts Naturalist ducks (including egg hatchlings) and
offspring produced by non-canceled breeding events. Breeding provenance is
saved on the offspring so reloading does not remove that exemption. Lycanites
are never exempt, even if bred; all their existing spawn/light/drop rules are
unchanged. Other non-breeding modded spawns keep their existing restrictions.
These exceptions do not bypass pasture/feed/daily breeding checks or other
mods' spawn cancellations.
Thrown chicken and Naturalist duck eggs can hatch only when the hatchling's
position is inside a pasture plot. Wilderness, ordinary village territory and
other plot types do not allow egg hatching. Eggs still break normally and keep
their native hatch chances inside pastures. This does not change breeding,
natural animal spawns or Lycanites rules.
Spawner-origin mobs bypass FarmUpCraft's custom spawn suppression in both
the finalize and world-join checks, inside and outside villages. This includes
Lycanites from spawners and NeoForge trial spawners; natural Lycanites spawns
keep their existing restrictions. Vanilla spawner light, difficulty, space,
player-distance and nearby-entity checks still apply.

Rejected planting immediately resends the clicked/placement blocks and the
player inventory, correcting client-predicted ghost crops and missing items.
Biome planting rules apply to both hands.
Planting checks use the supporting farmland block's biome Y, including
level-3 chunk-wide crop checks. `/plot info`, `/plot info <biome>` and
`/show plotcrops` sample at the block supporting the player, accounting for
farmland's fractional height. Stand on the farmland being checked; commands
do not infer the height of a different field while standing elsewhere.

Ground plant-eaters seek growing crops within eight blocks and destroy one
without drops at most every 1,200 world ticks (60 seconds at 20 TPS). This
includes vanilla livestock, rabbits, turtles, pandas, camels and sniffers, plus
Naturalist ducks, ostriches, elephants/mammoths, deer, boars, capybaras, zebras,
giraffes, hippos, rhinos, tortoises, turkeys and rats. Predators, insects and
flying birds are excluded. CropBlock crops (including HarvestCraft) and blocks
in the Minecraft crops tag are eligible at any growth stage. Animals need a
reachable path and an unobstructed bite; fences prevent eating through them.
The per-animal cooldown is saved, mobGriefing and block-destruction hooks are
respected, and ridden/riding animals and animals in love do not seek crops.
Existing rabbit carrot-raiding and Naturalist rat crop-harvesting goals are
replaced so they cannot bypass this shared behavior's per-animal limit.

The targets have separate Gradle wrappers/build roots; open the appropriate
target directory in your IDE rather than importing the repository root as a
Gradle project. Each target includes the shared `common` Java 17 subproject.
Common classes and resources are packaged in each mod jar, so no extra common jar is needed.

| Target | Directory | Java target | Gradle |
| --- | --- | --- | --- |
| Minecraft 1.20.1 / Forge | [forge-1.20.1](forge-1.20.1) | 17 | 8.1.1 |
| Minecraft 26.2 / NeoForge (partial port) | [neoforge-26.2](neoforge-26.2) | 25 | 9.2.1 |

The Forge code, resources, wrapper, and development run directory now live
under `forge-1.20.1`. The NeoForge target has currency and crop-rule
codecs/registries, crop/biome data loading, biome-rule persistence, database
storage/query/backup infrastructure, database synchronization payloads,
player/goods/market/world/village/NPC models, all eleven gameplay managers,
fourteen gameplay server command roots plus a legacy-data preflight root,
login/scheduling handlers, and JEI/JourneyMap
command and waypoint payloads. Client HUD/borders, weather/tooltips, and the
enchantment payment gate are also ported. Standalone data generation, populated
database restart/backup/restore, and connected database synchronization now pass
development acceptance. Visual gameplay, old-world migration, broader gameplay
regression, and startup stability remain incomplete.

### Build Forge on Windows

Run Gradle with JDK 17; do not upgrade this target to Java 25. For example,
from the repository root, using the portable JDK installed on this machine:

```powershell
$jdk17 = Join-Path $HOME '.jdks\jdk-17.0.20.1+1'
Push-Location .\forge-1.20.1
try {
    .\gradlew.bat "-Dorg.gradle.java.home=$jdk17" build -x copyJar
} finally {
    Pop-Location
}
```

Use your own JDK 17 installation path on other machines. The explicit Java-home
argument also overrides any stale `org.gradle.java.home` in the user-level
Gradle settings, without changing settings used by other projects.
For IDE use, select JDK 17 as the Forge project's Gradle JVM.

`-x copyJar` prevents the existing Forge build from copying the jar into the
live Minecraft mods directory. Omit it only when intentionally deploying.
Artifacts are written to `forge-1.20.1\build\libs`.

### Build NeoForge on Windows

From `neoforge-26.2`, run `.\gradlew.bat build`. The Java 25 compilation
toolchain can be provisioned by Gradle. Gradle 9.2.1 itself can run with JDK 21.
If a user-level Java-home setting is stale, explicitly override it:

```powershell
Push-Location .\neoforge-26.2
try {
    .\gradlew.bat "-Dorg.gradle.java.home=$($env:JAVA_HOME.TrimEnd('\'))" build
} finally {
    Pop-Location
}
```

This example assumes `JAVA_HOME` points to a valid JDK supported by Gradle 9.2.1.
Artifacts are written to `neoforge-26.2\build\libs`.

The development runtime now includes verified 26.2 releases of all four
HarvestCraft modules, Biomes O' Plenty, TerraBlender, GlitchCore, JEI, The One
Probe, JourneyMap, Villager Names, and Collective. Versions/file IDs are pinned
in `neoforge-26.2/gradle.properties`. These companion mods are not bundled in
the FarmUpCraft jar; installing that jar does not install a complete modpack.
The development client has loaded these mods successfully, but their presence
does not mean all FarmUpCraft integrations or gameplay features are ported.

Use the same Java-home override with `runClient` instead of `build` to launch
the development client, or `test` to run the automated tests. Both builds run
the common-module tests as part of `build`. For a filtered NeoForge test,
use `:test --tests <class-name>` so the filter does not also apply to common.
NeoForge tests use ModDevGradle's FML-aware JUnit support. Registry tests use
NeoForge's ephemeral server fixture to verify real item codecs, serializer
registrations, HarvestCraft lookups, and the loaded bronze coin value.
They also check migrated crop/biome resources and registry-aware saved-data
roundtrips. The latest full validation discovers 528 NeoForge tests: 525 pass, with
zero failures/errors and three platform-dependent legacy-path tests skipped
(Windows colon filenames and unavailable symbolic-link privileges). All fourteen
common tests pass without skips. The skipped cases are not verified coverage.
The fixture does not supply a `ServerLevel`; world-dependent behavior is
checked separately by dedicated development-server acceptance and sixty
actual-world GameTests.

Run `.\gradlew.bat runGameTestServer` with the same Java-home override to run
those GameTests. They cover refinery transfer, ineligible plots, missing
buckets, full-output drops, repeated-notification safety, villager fall
immunity, shared overworld database routing from the Nether, registration of
all fifteen server command roots and database/legacy-preflight permissions, and real
`save-all flush` JSON exports. The export test seeds a player and decodes the
exported record to verify its exact 137-coin balance before removing the fixture.
Four additional enchantment GameTests exercise the real enchanting menu:
successful payment, insufficient coins, creative exemption, and rejected
button/lapis/XP requests. They assert actual applied enchantments, balances,
lapis consumption, and vanilla XP behavior, not just payment helper results.
Additional cases cover village surcharge/debt and reservation refunds, actual
cow interactions and sheep entity ticks, and real HarvestCraft crafting-menu
tool exhaustion/kitchen remainders. The sheep case waits for actual entity
ticks rather than assuming a newly spawned entity ticks within two server ticks.
Four further cases cover house-owner breaking/loot, visitor invitations and
revocation/membership, stone-drop restrictions, and nursery leaf drops through
production interaction/event paths. Seven more cases exercise crop startup
caches, the focused production tag-refresh listener, Nether/End SavedData
isolation versus overworld routing, and villager reincarnation/death identity,
loaded/unclaimed isolation, and forever-kid aging. The refresh test invokes the
production listener with real registries, not a full datapack reload or synthetic
global reload-event dispatch. All 32 cases passed with default background
parallelism.
An additional real-level test verifies optional-integration command feedback
using API-blocking isolated production-bytecode copies. Three companion JUnit
tests check actual API absence, negative linkage controls, client payload
registration/dispatch, logout guards and recipe-command fallback. These are
focused handler-boundary tests, not complete absent-mod client discovery or
graphical startup acceptance. Separate real absent-mod acceptance is described
below. The earlier combined build/test run passed all
33 world tests using the process-scoped diagnostic thread setting noted below.
The subsequent full combined build, including the gated visual helper source
set, passes all 38 world tests. Five new cases cover structure-note contents and
idempotent cache use, visited-barrel block/inventory drops, farm-only hoe
durability and berry consumption, and actual crop planting under biome rules
and disabled enforcement.
Four further cases pass actual non-operator dispatcher rejection of seven
sensitive commands, treasury deposits/withdrawals and insufficient-funds
immutability, and production upkeep scheduling/accounting. The upkeep cases
invoke the production scheduler listener with primed elapsed time: exact timer
boundary/reset, member deduplication, debt, offline exclusion, same-day
idempotency, and ownership-filtered structure income are asserted. These are
real-level scheduler-listener tests, not full login or global event-bus
registration coverage. Those 42 world tests pass with the scoped diagnostic
background-thread setting.
Eleven further parity cases bring the passing world suite to 53: three recipe
cases check all 28 legacy definitions (16 active, 12 disabled), loaded manager
selection, seven real crafting-menu outputs/remainders, and stonecutting; seven
loaded loot-table cases check custom wheat and all six garden overrides,
including deterministic Fortune, maturity, Silk Touch and explosion behavior;
one dispatcher case checks both skeleton-command spellings, permissions,
actual entity counts and cleared AI goals. FarmUpCraft loads after NeoForge so
its recipe overrides win over NeoForge's compatibility recipes.
Seven external-datapack cases bring the world suite to 60: loaded tree/garden
bindings, deterministic generation and rarity rejection, real oil crafting
with rolling-pin durability/remainder, additive fish tags, and conditional
Pipez recipes. All 60 pass both without Pipez and with Pipez 1.2.32+26.2.
The runner creates/replaces a disposable world under `build\gameTest\world`,
not the normal development world. Results are in `build\gameTest-results.xml`.
Test code/data lives in `src/gameTest` and is not included in the release jar.

The database building blocks include all 13 active serializers and JSON/gzip
NBT import/export strategies. The NeoForge `DataSerializer` interface takes an
explicit `HolderLookup.Provider` for encoding and decoding so dynamic registry
references use the correct world context without requiring a live level.
Valid legacy payload field names and version envelopes are retained.
Malformed or unsupported data now fails explicitly instead of returning null,
defaulting, or silently dropping entries. Ordinary JSON does not retain every
NBT numeric width or array tag kind; serializers accept lossless conversions
but reject fractional or overflowing integer values. Use NBT when exact tag
types must survive export/import.

Chunk, NPC, structure, zone, world, village, and villager-record model codecs
are also ported and tested, alongside player, goods, and market models. All
nine original database definitions are registered with codec-based saved data,
queries, atomic imports/full-snapshot replacement, expiry, transactions, and
per-server shutdown. Backup workers capture snapshots on the server thread
before asynchronous export, isolate world paths, and retain only managed backup
files and checksum sidecars. Backups remain disabled by default, matching the
original definitions. The market JSON loader remains inactive, matching its
old commented-out registration. Gameplay consumers and live entity/name
lookups now have ported consumers, but still need broader actual-world coverage.

The animal, plot/chunk, cookware, crop, island-scan, NPC, stone, wood, tree,
structure, and villager managers are now ported with NeoForge event hooks.
Their tests cover actual
HarvestCraft/BOP identifiers, wool color collections, feeding and breeding
decisions, plot policies, rate/threshold calculations, biome restrictions,
warning caches, and offline AI response handling. Static gameplay state and
the AI worker are cleaned up between servers. Island scans and asynchronous
locators are canceled during shutdown; NPC retries and conversations are
server/world-bound and cleared at the appropriate lifecycle boundaries.
These tests are not a complete live-player gameplay regression suite: crop
actions, scans, NPC conversations, login workflows, and timed upkeep still
need live acceptance. Stone rules retain their tag-based selectors; 26.2's loaded dirt tag
does not include grass blocks.

All fifteen server roots are registered through one event handler: `ai`,
`database`, `farmcraft`, `managers`, `market`, `npc`, `player`, `plot`, `scan`,
`show`, `structures`, `tester`, `village`, `zone`, and `farmupcraft-legacy`.
Tests cover command trees,
permissions, economy boundaries, and source handling, but do not execute every
branch with a connected player. Login creates/backfills the player record before
database snapshots without resetting an existing balance. World scheduling
retains the 72,000-tick hourly interval and 6,000-tick reminder/retry interval;
a failed daily market refresh does not advance its successful-run date.
Overworld saves export players, villages, and chunks under the server working
directory's `farmupcraft-data`; shutdown exports run before database cleanup.

Cookware counters use the legacy `farmupcraft_cookware_uses` key inside the
modern custom-data component. The registered mixin supplies durability bars
and prevents exhausted cookware from returning as a crafting remainder,
without removing the ingredient before recipe lookup. Tests verify all nine
tools, 500/10,000-use initialization, counter preservation across actual
HarvestCraft remainders, exhaustion, and unchanged vanilla remainders.
Visual bars and full crafting-menu interaction still need client acceptance.

Enchanting reserves coins only after vanilla validates the request and selects
nonempty enchantments, immediately before applying them. The scoped payment
commits after installing the enchanted item and refunds an uncommitted reserve
on scope exit. Village-level fees/debt checks and creative coin exemption are
retained; failed vanilla or insufficient-funds requests do not debit coins.

The client player-data HUD, `/frecipe`, and claimed-chunk borders are ported.
Borders use public render-state extraction/custom-geometry events instead of
the old weather-rendering mixin; immutable snapshots omit shared internal
walls. Tooltip hints retain crop-tag selection and support a null player.
Pause-screen weather muting preserves the original double-precision volume
across repeated opening notifications. Unit tests cover HUD labels, border
geometry/selection/colors, tooltip filtering, and volume state. A connected
graphical client and separate dedicated server now capture nine real game
framebuffers with ordered acknowledgements. Inspected captures verify the HUD,
visible blue claim walls, wrapped crop tooltip, cookware bar, and real PauseScreen.
The weather option and options file mute and restore exactly; audible weather
is not asserted. Both peers exit cleanly. The gated harness and execution contract
are in `src/visualTest`; screenshots and receipts are under
`build/visual-acceptance`.

The border renderer preserves the legacy opacity with its own fragment shader:
the vanilla particle shader discards every forcefield texel at that opacity.
Walls are divided into 16-block-high sections so nearby wall fragments do not
inherit distant fog from full-level-height corner vertices. Regression tests
cover the shader contract and full-height section bounds, and the actual game
capture verifies visible walls. The installed optional-mod GUI checks below
also pass; broader connected client-command acceptance remains incomplete.

JourneyMap waypoint add/remove/scan payloads now use the v2 API with separate
client-only handlers. Generated native-owned waypoints carry an origin marker
so removal does not delete unrelated player waypoints. Codec, filtering, and
API-facing behavior are tested. Actual production payload delivery now also
creates visible native and scanned waypoints in JourneyMap's manager, opens
the native editor, and saves a verified rename. The persistent flag is checked,
but restart/disk waypoint persistence remains unverified.

Highlight/village-chunk payloads now drive JourneyMap v2 polygons, with
replacement, refresh, clearing, and logout cleanup. Recipe-opening payloads
use the actual JEI output-focus API. A connected nine-stage run verifies JEI's
bread OUTPUT focus with two visible recipe layouts, the native JourneyMap
manager/editor, and rendered red highlight/blue village polygons. Exact
geometry/style and viewport checks require 16-block chunks at four render
pixels/block; inspected screenshots show both colored squares. Production
cleanup removes the tracked polygon handles and both fixture waypoints; the
final native manager is visibly empty. This does not claim a post-clear map
framebuffer or disk persistence. All nine screenshot hashes match receipts,
both peers exit zero, and acceptance ports are released. Border/HUD framebuffer
acceptance is recorded above. JEI tests use its full mod at
runtime so Minecraft-facing API classes load in the same mod layer, rather
than loading the standalone compile API through JUnit's parent classloader.

### Data-generation status

The fourteen generator/provider classes compile; seventeen focused generator
and biome-classification tests pass. `runServerData` completes all providers
and writes only to `build\datagen-verification`, never directly to reviewed
source resources:

```powershell
.\gradlew.bat "-Dorg.gradle.java.home=$($env:JAVA_HOME.TrimEnd('\'))" runServerData
```

Datagen loads installed datapack registries before evaluating item component
initializers and normalizes unbound virtual tags through ordinary named tags,
without rebinding global components or suppressing validation. All nineteen
fresh JSON outputs were reviewed semantically. The packaged food tag now has
1,056 unique optional HarvestCraft foods, and the BOP overworld tag has 59
dimension-correct optional biomes. Crop generation retains 95 harvest items
plus six gardens. Regressions check uniqueness and sorted output, not merely
set membership. Normal `build` does not run data generation.

### Daily chores file

Both jars bundle the original chores list as `farmupcraft/defaults/chores.md`.
When a server starts (including opening a single-player world), FarmUpCraft
creates `config/farmupcraft/chores.md` only if it is missing. An existing legacy
`Rimfog/chores.md` in that instance is copied first when available; otherwise
the bundled list is used. Neither the legacy file nor an existing new file is
overwritten, even when it is empty. Startup initialization failures are logged.

Edit the new config file to customize the list. Login suggestions and
`/village chores` read it directly, so edits do not require rebuilding the jar.
The location is relative to the game's/server's config directory, not inside a
world save. The bundled snapshot is not automatically synchronized with edits
to the old file. The original source chores file and live instance directories
are not modified during the build.

Ten shared tests cover first-run defaults, custom/empty files, legacy copy and
precedence, subsequent edits, invalid paths and concurrent startup. All fourteen
common tests pass under both target builds. An actual NeoForge test-server
startup with no existing config or legacy file created an exact byte-for-byte
copy of the bundled list, and all 60 world tests passed.

### Bundled external HarvestCraft datapack

Both target jars include all 155 JSONs from Rimfog's external
`harvestcraft_datapack`: Forge retains the original 1.20.1 definitions, while
NeoForge includes converted 26.2 counterparts. These comprise 28 vanilla recipe definitions,
seven loot tables, 100 tree worldgen definitions (50 configured and 50 placed),
14 garden worldgen definitions, one oil recipe, three fish/meat tags and two
Pipez recipes. No separate datapack installation is required. The original
pack, its old `pack.mcmeta`, and the Rimfog save are not modified.

The NeoForge conversion uses singular recipe/loot/tag paths and current codecs. Garden
patches use HarvestCraft's registered `original_random_patch`; tree dirt rules
use 26.2's `below_trunk_provider`. Existing HarvestCraft biome modifiers already
attach the converted feature IDs. Preserve original aliases, generation
parameters and recipe quantities: the oil recipe produces eight despite its
legacy filename. Fish tags use HarvestCraft's actual `c:fishes`, `c:rawfish`
and `c:rawmeats` IDs; optional Aquaculture/Alex's Mobs entries do not require
those mods. The legacy cooked-fillet entry in `rawfish` is intentionally retained.

On NeoForge, Pipez remains optional. Its recipe overrides are skipped when absent and load
after Pipez when installed. To reproduce the installed-Pipez acceptance run:

```powershell
.\gradlew.bat "-Dorg.gradle.java.home=$($env:JAVA_HOME.TrimEnd('\'))" -PwithPipez=true runGameTestServer
```

Omit `-PwithPipez=true` for the absent-Pipez run. This flag adds the pinned Pipez
mod only to development runtimes; it does not bundle or require Pipez in the
production jar. Both runs pass all 60 world tests. Retained XML and logs are
under `build\external-pack-acceptance\with-pipez` and `without-pipez`.
Earlier attempts exposed intermittent entity-visibility/readiness failures
in existing fixtures; passing reruns are not a claim that those flakes or the
known upstream startup race are fixed.

Bundled data is available wherever the mod runs. World-generation changes
normally affect new chunks, not existing terrain. World datapacks can override
bundled data; do not also enable the unconverted old pack in a 26.2 world.
The Forge source resources and rebuilt 1.20.1 jar were checked against the
original pack: all 155 JSONs match semantically, with each packaged exactly
once. Its own `pack.mcmeta` retains format 15; the external pack metadata is
not copied. Forge keeps its original tag IDs, mod references and dependency
assumptions, rather than receiving NeoForge's optional-mod guards. The Forge
build passes with Java 17 and `build -x copyJar`; this packaging verification
does not claim a new Forge in-world generation test.

Database synchronization uses named NeoForge clientbound entry/full payloads,
separate client-only handlers, login/dimension snapshots, and disconnect-cache
cleanup. Large packets use NeoForge's negotiated packet splitter rather than
the old custom chunk collector. Tests cover a 3 MiB payload codec, malformed
snapshots, defensive copying, registry references, and empty-table clearing;
actual separate localhost server/client JVMs also passed login snapshots,
the exact balance update from 10 to 654321, Nether resynchronization with new
level/cache identities, a single 1,441,504-byte full snapshot, and logout cache
clearing. Both peers acknowledged exact contents; the strict receipt verifier
passed. This verifies database transport, not JourneyMap/JEI GUI behavior.
Development-only helpers and instructions live in `src/networkTest`; they are
excluded from the production jar and disabled in ordinary runs.

The database-enabled dedicated development server started, saved, stopped,
and restarted successfully. All nine database files and the existing biome
manager file retained identical SHA-256 hashes after restart/save/stop.
A subsequent disposable-world acceptance seeded one cross-referenced populated
row in each of the nine tables, saved/stopped, and restarted in a separate JVM.
Both lifecycles observed new backups from the real tick scheduler. Restart
verified exact loaded data before mutation, then cleared/restored every table
through checksum-validating imports. Independent checks verified all nine
physical backup hashes, nine SavedData files, and three economy exports.
The development-only `src/persistenceTest/EXECUTION.md` documents reproduction
and required evidence. This is not an old Forge save migration test.

That smoke test initialized 105 biomes, 198 indexed items, and 3,084 ordered
crop selections, then verified the saved definitions, selections, and reverse
index were unchanged after restart and a clean save/stop. This is biome-rule
coverage, not a complete economy or gameplay regression test.
The existing crop/biome JSON field names are retained, including
`growth_succes_rate`; the NeoForge resources use singular `tags/block` and
`tags/item` directories and the common `c:is_desert` biome tag.

Old Forge saved-data files are **not automatically migrated**. The 26.2
development world stores biome rules under
`world\dimensions\minecraft\overworld\data\farmupcraft\farmupcraft_biome_rules_manager.dat`.
The payload shape is preserved, but this does not establish that an entire
1.20.1 world can be upgraded safely. Test any future importer on copies.

An operator-only, read-only preflight is available:
`/farmupcraft-legacy inspect "<copied legacy overworld data directory>"`.
Pass the copied world's `data` directory, not the world root. It requires the
biome-rule manager file and all nine known database SavedData files; it checks
legacy envelopes, registry references, duplicates, canonical paths and destination
file absence before reporting eligibility. The biome filename is
`farmupcraft_biome_rules_manager.dat`; database filenames are
`farmupcraft:NAME.dat` or explicitly staged Windows-safe
`farmupcraft_NAME.dat`, where NAME is one of `biome_rules_database`,
`player_database`, `chunk_data_database`, `village_database`,
`goods_data_database_new`, `world_data_database`, `npc_database`,
`game_structure_database`, or `game_zone_database`. Supplying both aliases for
one database is rejected. Source paths equal to, inside or containing the live
world are rejected after canonical resolution, as is any existing destination
file, even an empty one.

**This is not a working importer yet.** The `apply` subcommand always reports
blocked and returns failure without writing. Installing data safely requires
coordinating live database caches with all independent SavedData files and
providing atomic rollback; successful inspection cannot certify unsaved live
state. Neither inspection nor apply modifies the supplied copy or active world.
Do not manually copy these files into an active server as a substitute.
The user has chosen to keep migration preflight-only for now; import/install
writes remain disabled.

Actual optional-integration absence is also verified using separate loopback
dedicated-server and graphical-client processes on port 25580 in a fresh
disposable root. Fresh launch preparation removes both JEI and JourneyMap jars
and rejects leaked bundled/separately resolved APIs. Strict runtime inventories
confirm FarmUpCraft and required content are present while JEI, JourneyMap and
JourneyMap API are absent. Production login, balance update, dimension resync,
large full snapshot and logout-cache receipts pass, and both peers shut down
cleanly. The development-only scripts are in `src/optionalRuntimeTest`; they do
not modify cached jars, original acceptance evidence or EULA/global settings.
The run uses the scoped diagnostic background-thread setting, so it does not
resolve default-parallelism startup stability.

Separate regular-player action acceptance also passes with a real connected
graphical client and dedicated server on loopback 25581. The participant is
non-operator and in survival mode: normal forward-key input moves it over two
blocks, vanilla attack input breaks a dirt block, and client `useItemOn` places
a dirt block. Server-side events, authoritative block states, and held dirt
consumption from three to two gate ordered round-trip acknowledgements. Both
peers exit zero. The server seeds only the disposable wilderness fixture;
measured actions do not use creative mode or operator elevation.
Run `src\networkTest\run-player-actions.ps1` after `networkTestClasses` and
preparing the existing flattened network launch exports. Each attempt creates
a unique test root under `build\player-action-run-*`, enforces runtime bounds,
and preserves receipts and console logs. This is scripted normal client action
coverage, not physical mouse/keyboard automation or a test of the user's
regular world. The regular-world task remains pending explicit world selection
and approval; no live save, EULA, or global settings were changed.

The NeoForge dedicated development server has reached startup successfully.
NeoForge's development-mode patches bypass the EULA-file check; no EULA file
was edited. Normal production server setup still requires the user's EULA
acceptance. Dedicated-server diagnostics were traced to the pinned companion
artifacts: JourneyMap lists `client.SalmonRendererAccessor` in the common
`mixins` array rather than its client-only array; Biomes O' Plenty adds allays
to Mystic Grove without a registered spawn placement and puts ocelots in the
monster category for Rainforest/Rocky Rainforest. No dependency jars were
modified or errors suppressed; upstream corrections and runtime revalidation
remain outstanding. This is not a production-readiness guarantee.
An intermittent companion-registry tag-listener startup failure was observed
with default background parallelism. The combined build/tests, datagen, and
connected/persistence acceptance used process-scoped `-Dmax.bg.threads=1` for
diagnosis, not a production fix or global setting. A subsequent default-
parallelism run passed all 32 GameTests, but does not establish startup stability.
Three bounded default-parallelism server starts with FarmUpCraft removed and the
same companions present all reached ready and stopped cleanly; they did not
reproduce the intermittent race, so isolated upstream attribution remains
unconfirmed by runtime reproduction. A later default unit-fixture start with
FarmUpCraft present hit the same `apple_placed` listener-registration exception.
Only the disposable network client's loading-warning/accessibility prompts
were disabled for automatic connection; warnings remained logged and fatal
loading errors were not bypassed.

First-time builds may take several minutes to download and prepare Minecraft.
Both targets also build from fresh working-tree source-only snapshots in
offline mode, reusing global dependency/build caches without copying project
build outputs or development worlds. The NeoForge snapshot additionally passes
the same 407-test (three platform skips), four-common-test and 33-world-test
checks with the scoped diagnostic setting. This is not a committed
clean-checkout/release-readiness claim.
A quick old/new parity audit identified three concrete gaps, now restored and
verified: all 28 legacy vanilla recipe definitions, custom wheat Fortune loot
plus six HarvestCraft garden loot tables, and `/npc createskeleton <name>`
(with `createskleleton` retained as an alias). Recipe IDs and the 12 disabled
definitions retain legacy behavior; custom `stonecutter_*` IDs do not suppress
differently named vanilla recipes, just as in the Forge target.
A second bounded source audit found no additional corroborated omission in
the inspected defaults, lifecycle, command branches, NPC profiles and resource
references. Neither audit establishes exhaustive branch or runtime equivalence.
Completed parity work remains recorded in [TODO.txt](TODO.txt), separately
from deferred save migration and features lacking full live testing.
See [Plan.md](Plan.md) for the migration checklist.

## How to run the Forge server

0. Get Forge correct version

1. Must install all mods required, 
current zip file location here:
TODO

2. Copy any customized setup configs for:
[SpecialAI](../../AppData/Roaming/.minecraft/config/SpecialAI)
Spice of Life progression is now bundled and installed once automatically;
its config does not need to be copied manually.

3. FarmUpCraft's crop/biome rules and all 155 external HarvestCraft datapack
   JSONs are bundled in both target jars, including worldgen, tags and recipes.
   Forge uses the original definitions; NeoForge uses its converted versions.
   A separate copy of the pack is not needed to supply these defaults.
