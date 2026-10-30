package com.falazar.farmupcraft.resources;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ModMetadataTest {
    private static final Set<String> CONTENT_MODS = Set.of(
            "pamhc2foodcore", "pamhc2crops", "pamhc2foodextended", "pamhc2trees",
            "biomesoplenty", "terrablender", "glitchcore");

    @Test
    void contentBundleFailsAtModDiscoveryOnEitherSideWhenMissing() throws Exception {
        var dependencies = dependencies();
        assertEquals(Set.of("minecraft", "neoforge", "pamhc2foodcore", "pamhc2crops",
                "pamhc2foodextended", "pamhc2trees", "biomesoplenty", "terrablender",
                "glitchcore", "jei", "journeymap", "pipez"), dependencies.keySet());
        for (String id : CONTENT_MODS) {
            var dependency = dependencies.get(id);
            assertEquals("required", dependency.get("type"), id);
            assertEquals("BOTH", dependency.get("side"), id);
            assertEquals("AFTER", dependency.get("ordering"), id);
            assertFalse(dependency.contains("mandatory"), "Do not use Forge dependency syntax");
            assertFalse(dependency.contains("required"), "Use NeoForge's type field");
        }
    }

    @Test
    void recipeOverridesLoadAfterNeoForgeCompatibilityRecipes() throws Exception {
        assertEquals("AFTER", dependencies().get("neoforge").get("ordering"));
    }

    @Test
    void onlyLazyGuardedClientIntegrationsAreOptional() throws Exception {
        var dependencies = dependencies();
        for (String id : List.of("jei", "journeymap")) {
            var dependency = dependencies.get(id);
            assertEquals("optional", dependency.get("type"), id);
            assertEquals("CLIENT", dependency.get("side"), id);
            assertEquals("AFTER", dependency.get("ordering"), id);
        }
    }

    @Test
    void pipezOverridesAreOptionalAndLoadAfterTheirTargetOnBothSides() throws Exception {
        var dependency = dependencies().get("pipez");
        assertEquals("optional", dependency.get("type"));
        assertEquals("BOTH", dependency.get("side"));
        assertEquals("AFTER", dependency.get("ordering"));
    }

    @Test
    void rangesAcceptActualPinnedJarVersionsAndRejectOtherMinecraftLines() throws Exception {
        var dependencies = dependencies();
        var pinned = Map.of(
                "pamhc2foodcore", "1.0.0", "pamhc2crops", "1.0.0",
                "pamhc2foodextended", "1.0.0", "pamhc2trees", "1.0.0",
                "biomesoplenty", "26.2.0.0.28", "terrablender", "26.2.0.0.2",
                "glitchcore", "26.2.0.0.0", "jei", "30.48.0.244",
                "journeymap", "26.2-6.0.10");
        for (var entry : pinned.entrySet()) {
            assertTrue(accepts(dependencies.get(entry.getKey()), entry.getValue()), entry.getKey());
        }
        for (String id : CONTENT_MODS) {
            assertFalse(accepts(dependencies.get(id), "1.20.1-1.0.0"), id);
            assertFalse(accepts(dependencies.get(id), "26.3.0"), id);
        }
        assertFalse(accepts(dependencies.get("biomesoplenty"), "26.2.0.0.27"));
        assertFalse(accepts(dependencies.get("jei"), "15.0.0.12"));
        assertFalse(accepts(dependencies.get("jei"), "31.0.0"));
        assertFalse(accepts(dependencies.get("journeymap"), "1.20.1-5.10.3"));
        assertFalse(accepts(dependencies.get("journeymap"), "26.3-6.0.10"));
        assertTrue(accepts(dependencies.get("minecraft"), "26.2"));
        assertFalse(accepts(dependencies.get("minecraft"), "26.1.2"));
        assertFalse(accepts(dependencies.get("minecraft"), "26.3"));
        assertTrue(accepts(dependencies.get("neoforge"), "26.2.0.93"));
        assertFalse(accepts(dependencies.get("neoforge"), "26.2.0.92"));
        assertFalse(accepts(dependencies.get("neoforge"), "26.3.0"));
    }

    @Test
    void registeredMixinConfigurationIsPackaged() throws Exception {
        List<UnmodifiableConfig> mixins = metadata().get("mixins");
        assertEquals(1, mixins.size());
        String config = mixins.getFirst().get("config");
        assertEquals("farmupcraft.mixins.json", config);
        assertNotNull(getClass().getResource("/" + config));
    }

    private static boolean accepts(UnmodifiableConfig dependency, String version) throws Exception {
        String range = dependency.get("versionRange");
        return VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(version));
    }

    private static Map<String, UnmodifiableConfig> dependencies() throws IOException {
        List<UnmodifiableConfig> dependencies = metadata().get("dependencies.farmupcraft");
        return dependencies.stream().collect(Collectors.toMap(
                dependency -> dependency.<String>get("modId"), Function.identity()));
    }

    private static UnmodifiableConfig metadata() throws IOException {
        var resources = ModMetadataTest.class.getClassLoader().getResources("META-INF/neoforge.mods.toml");
        UnmodifiableConfig metadata = null;
        while (resources.hasMoreElements()) {
            var resource = resources.nextElement();
            try (var reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
                var candidate = new TomlParser().parse(reader);
                List<UnmodifiableConfig> mods = candidate.getOrElse("mods", List.of());
                if (mods.stream().anyMatch(mod -> "farmupcraft".equals(mod.get("modId")))) {
                    assertNull(metadata, "FarmUpCraft release metadata must be packaged only once");
                    assertFalse(candidate.toString().contains("${"),
                            "All release metadata placeholders must be expanded");
                    metadata = candidate;
                }
            }
        }
        assertNotNull(metadata, "Missing generated FarmUpCraft META-INF/neoforge.mods.toml");
        return metadata;
    }
}
