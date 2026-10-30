# Populated persistence acceptance (dev only)

This source set is **not production code** and must not be included in the published
jar. The harness is an `@EventBusSubscriber`; registering this source set with the
development `farmupcraft` mod is sufficient. No bootstrap call is needed.
Every event hook is explicitly gated by the boolean system property
`farmupcraft.persistence.enabled=true` (default **false**); merely including
these classes in ordinary server/client/GameTest mod roots does nothing.
The exact subscriber is
`com.falazar.farmupcraft.persistencetest.PersistenceAcceptance`, with static hooks
`configure(ServerAboutToStartEvent)`, `started(ServerStartedEvent)`, and
`tick(ServerTickEvent.Post)`, each at `EventPriority.LOWEST`.
It also has a gated `preflight(FMLCommonSetupEvent)` on the mod bus (automatically
routed by NeoForge's subscriber registration). No parent bootstrap hook is needed.

## Parent-owned build/run wiring

1. Define `persistenceTest` with main output and main dependencies on its compile
   and runtime classpaths, plus `:common` output. Call
   `neoForge.addModdingDependenciesTo(sourceSets.persistenceTest)`.
2. Include this source set in the development `farmupcraft` mod alongside main
   and common. Define two ordinary dedicated-server runs using this source set,
   **not** `gameTestServer`: `persistenceSeedServer` and
   `persistenceVerifyServer`.
3. Both runs must use the exact same absolute game directory:
   `C:\Users\robin\IdeaProjects\farmcraft_economy\neoforge-26.2\build\persistence-acceptance`.
   Set `farmupcraft.persistence.root` to that directory. Set
   `farmupcraft.persistence.phase` respectively to `seed` and `verify`.
   Set `farmupcraft.persistence.enabled=true` on **these two runs only**.
   Use `--nogui`; keep the world at `<gameDirectory>\world` (default world
   name `world`; do not point `--universe` at another directory).
   Use a dedicated loopback address/unused port, no clients, and no existing
   `run\world`. Disable empty-server pausing in **this disposable run only** if
   the runtime defaults pause before the first one-second backup becomes due.
   For deterministic background behavior, provision this disposable
   `server.properties` with `level-name=world`, `level-type=minecraft:flat`,
   `generate-structures=false`, `level-seed=2602`, `difficulty=peaceful`, and
   `server-ip=127.0.0.1` (plus the parent's unused port). Do not reuse/copy a
   live server's properties. This avoids generated village crops/entities
   changing the database fixture during the short observation window.
4. Provision the disposable game directory using the parent's authorized setup.
   The helper does **not** read/write an EULA file, accept an EULA, launch a
   process, or alter global settings. Parent must use an already-authorized
   server setup; absent that prerequisite, stop and report the blocker.
5. Before either launch, create `ACCEPTANCE_DISPOSABLE` in that game directory,
   containing exactly `farmupcraft persistence acceptance v1` (a trailing newline
   is allowed). Gated **common-setup preflight**, before vanilla world startup,
   refuses any pre-existing `world\level.dat` or `world\level.dat_old` and records
   the checked root in memory. Server configuration revalidates the root/marker
   and requires that attestation; it does not recheck file absence.
   Minecraft 26.2 writes `level.dat` **before** `ServerAboutToStartEvent`, so
   checking freshness in that event (or `ServerStartedEvent`) is too late.
   A failed disposable run must be archived and freshly reprovisioned by the
   parent; the helper never deletes/replaces world files to make preflight pass.

Only after receiving the exclusive runtime window, parent runs (from
`neoforge-26.2`, using its normal Gradle wrapper):

```powershell
.\gradlew.bat runPersistenceSeedServer --console=plain
# Wait for process exit; inspect evidence and normal server shutdown.
.\gradlew.bat runPersistenceVerifyServer --console=plain
```

These are **two separate JVM/server lifecycles**, not two calls within one
GameTest server. The helper auto-stops after saving and passing, or immediately
reports/stops on failure. Scheduled-backup waiting is bounded to 45 seconds
after `ServerStartedEvent`; parent should impose an independent startup timeout
because a failed mod/server bootstrap can occur before that event.

## What executes

Before levels initialize, and only after the disposable-path/marker checks,
the helper verifies that the nine production accessors have backups disabled.
It replaces their registrations with equivalent serializers, expiry and sync
settings, but explicitly enables JSON backups every **1 second**, count **2**,
retention **60000 milliseconds**. No production sources/defaults are changed.
This tests the real registered economy tables, not surrogate empty tables.

Seed clears only these disposable databases and inserts exactly one entry per
table. No entity is spawned and no fake player connection is required.

| Table | Exact key | Selected expected values/references |
|---|---|---|
| player | `26020000-0000-0000-0000-000000000001` | id `71`, coins `137`, home village `…0002`, border `true/green` |
| village | `26020000-0000-0000-0000-000000000002` | `Acceptance Village`, level `3`, coins `941`, member player `…0001`, claim `(23,-17)` |
| chunk | `ChunkPos(23,-17).pack()` = `-73014444009` | type `farm`, player id `71`, owner `…0001`, village `…0002`, bought Y `72`, plot level `2` |
| NPC | `26020000-0000-0000-0000-000000000003` | `Acceptance Farmer`, village UUID/name `…0002/Acceptance Village` |
| goods | `minecraft:wheat` | cost `17`, sold `29`, active `true`, market `daily`, date `2026-01-02` |
| world | integer `0` | upkeep `314`, date `2026-01-02`, farms `false`, crop rules `true`, light block `false`, LAN notice `false` |
| structure | `BlockPos(376,72,-264).asLong()` = `103628969836616` | `Acceptance Barn`, claimed/visited, claimed chunk above, chest at center opened |
| zone | `26020000-0000-0000-0000-000000000004` | `Acceptance Farm Zone`, `COUNTRY`, section `(23,4,-17)`, center `(376,72,-264)` |
| biome rules | `minecraft:plains` holder | empty base rules plus **persisted** selected crops `minecraft:wheat`, `minecraft:carrot` |

`evidence\expected-<namespace>-<database>.json` contains **every** exact serialized
key/value, not just the selected fields above. The seed report records the
SHA-256 of each canonical payload and of each observed physical backup.
The canonical representation recursively sorts object properties; array order
is preserved. Exactly one entry per database makes entry-array order unambiguous.

The helper never calls `performBackupAsync`, `processQueue`, clock overrides,
or a synchronous backup shortcut. It waits for the real production tick listener
to create a **new** backup and matching `.sha256` for every table. It validates
both physical checksum and canonical populated contents.

Verify first compares loaded SavedData to every seed payload, before any
mutation or import. It excludes pre-existing backup filenames and observes
nine new scheduled backups in the restarted server. Then it empties each table,
restores it through `importWithCheckSumValidation` using the observed backup,
compares the entire restored payload, and rechecks cross-references.
Both phases call `server.saveEverything(true, true, true)` and stop normally.

## Required acceptance evidence

Require **all** of the following, not only a zero Gradle exit code:

- Distinct JVM lifecycles and clean shutdowns; no startup/runtime failure.
- `PERSISTENCE_ACCEPTANCE_SEED_PASS` and
  `PERSISTENCE_ACCEPTANCE_VERIFY_PASS` in their respective logs.
- Both `evidence\seed-complete.json` and `evidence\verify-complete.json`
  with `status: PASS`, different process/start identities, nine entries in the
  `databases` object, one populated row per table, and all verify `restored: true`.
- No `evidence\*-FAILED.txt`.
- Nine canonical expected payloads, populated world SavedData files,
  `world\level.dat`, and world-save exports in `farmupcraft-data`.
- Observed backups under
  `databases\backup\<world-path-derived-id>\farmupcraft\<database>\`
  with matching `.sha256` sidecars. Report file hashes and exact filenames.

The two dedicated JVM lifecycles now pass this contract. Both reports contain
nine populated tables; every verify record has `restored: true`. Independent
checks verified physical backup/sidecar hashes, distinct JVM identities, nine
SavedData files, three exports, and no failure evidence in the successful run.
Validation used process-scoped `-Dmax.bg.threads=1` after an intermittent
companion-registry startup failure; default-parallelism stability is not implied.
The first failed fresh-world guard attempt is archived separately under
`build\persistence-acceptance-failed-startup`, not counted as a passing lifecycle.
