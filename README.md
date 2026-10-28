# FarmUpCraft

## Development targets

The targets have separate Gradle wrappers/build roots; open the appropriate
target directory in your IDE rather than importing the repository root as a
Gradle project. Each target includes the shared `common` Java 17 subproject.
Common classes are packaged in each mod jar, so no extra common jar is needed.

| Target | Directory | Java target | Gradle |
| --- | --- | --- | --- |
| Minecraft 1.20.1 / Forge | [forge-1.20.1](forge-1.20.1) | 17 | 8.1.1 |
| Minecraft 26.2 / NeoForge (partial port) | [neoforge-26.2](neoforge-26.2) | 25 | 9.2.1 |

The Forge code, resources, wrapper, and development run directory now live
under `forge-1.20.1`. The NeoForge target has currency and crop-rule
codecs/registries and basic JEI/JourneyMap plugin bridges; the complete economy, commands,
networking, and gameplay systems have not yet been ported.

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
The fixture does not cover world-dependent crop cache behavior.

The NeoForge dedicated development server has reached startup successfully.
NeoForge's development-mode patches bypass the EULA-file check; no EULA file
was edited. Normal production server setup still requires the user's EULA
acceptance. Development startup also reported companion-mod diagnostics
that remain to be investigated; this is not a production-readiness guarantee.

First-time builds may take several minutes to download and prepare Minecraft.
See [Plan.md](Plan.md) for the migration checklist.

## How to run the Forge server

0. Get Forge correct version

1. Must install all mods required, 
current zip file location here:
TODO

2. Must copy setup configs for:
[SpecialAI](../../AppData/Roaming/.minecraft/config/SpecialAI)
[solonion.json](../../AppData/Roaming/.minecraft/config/solonion.json)

3. harvestcraft_datapack rules are now bundled inside the mod jar — no manual copy needed.
   (All data files are in `forge-1.20.1/src/main/resources/data/` and are included in the Gradle build automatically.)
