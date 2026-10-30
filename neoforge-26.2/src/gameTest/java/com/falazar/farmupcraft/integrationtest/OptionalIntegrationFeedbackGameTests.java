package com.falazar.farmupcraft.integrationtest;

import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;

/**
 * World-backed counterpart to OptionalIntegrationAbsenceTest: the JUnit server has no overworld.
 * Registered through the existing gameTest source set; no additional build wiring is required.
 * Uses a real level/player and raw production guards, with only class names and ModList substituted.
 * This is a message/dispatch boundary test, not client mod discovery, rendering or GUI acceptance.
 */
@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class OptionalIntegrationFeedbackGameTests {
    private static final String CLIENT = "com.falazar.farmupcraft.client.";
    private static final String ISOLATED_CLIENT = "optionalabsence.feedback.client.";
    private static final String MOD_LIST = "net.neoforged.fml.ModList";
    private static final Set<String> ISOLATED = Set.of(
            CLIENT + "CommandPayloadHandler", CLIENT + "JeiIntegration",
            CLIENT + "JourneyMapIntegration", CLIENT + "JourneyMapChunkOverlays");

    private OptionalIntegrationFeedbackGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("optional_integration_absence_feedback"),
                OptionalIntegrationFeedbackGameTests::verifyFeedback));
    }

    private static void verifyFeedback(GameTestHelper helper) {
        var loader = new AbsentIntegrationLoader();
        try {
            for (String api : List.of("mezz.jei.api.IModPlugin", "mezz.jei.api.runtime.IJeiRuntime",
                    "journeymap.api.v2.client.IClientPlugin", "journeymap.api.v2.client.IClientAPI")) {
                try {
                    Class.forName(api, false, loader);
                    throw new AssertionError("Optional API was available: " + api);
                } catch (ClassNotFoundException expected) {
                    helper.assertTrue(expected.getMessage().contains("Optional mod deliberately absent"),
                            "The isolated loader must explicitly deny optional APIs");
                }
                String resource = api.replace('.', '/') + ".class";
                helper.assertTrue(loader.getResource(resource) == null, "Optional API resource unavailable: " + api);
                helper.assertTrue(!loader.getResources(resource).hasMoreElements(),
                        "Optional API resource enumeration unavailable: " + api);
            }
            loader.optionalLoads.clear();
            Class<?> handler = Class.forName(isolatedName(CLIENT + "CommandPayloadHandler"), true, loader);
            helper.assertTrue(handler.getClassLoader() == loader, "Execute isolated production guard bytecode");
            var player = new RecordingPlayer(helper.getLevel());
            try {
                IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                        IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("player")) {
                                return player;
                            }
                            throw new AssertionError("Unexpected payload context access: " + method);
                        });
                List<CustomPacketPayload> payloads = List.of(
                        new HighlightChunkPacket(0, 0, "absent", "minecraft:overworld", 0),
                        new ShowVillageChunksPacket(List.of(new int[]{0, 0, 0}), true, "minecraft:overworld"),
                        new ShowVillageChunksPacket(List.of(), false, "minecraft:overworld"),
                        new OpenJeiRecipePacket("minecraft:bread"));
                List<String> methods = List.of("handleHighlight", "handleVillage", "handleVillage", "handleRecipe");
                for (int i = 0; i < payloads.size(); i++) {
                    CustomPacketPayload payload = payloads.get(i);
                    handler.getMethod(methods.get(i), payload.getClass(), IPayloadContext.class)
                            .invoke(null, payload, context);
                }
                helper.assertValueEqual(player.messages, List.of(
                        "Cannot display command result: journeymap is not installed.",
                        "Cannot display command result: journeymap is not installed.",
                        "Cannot display command result: journeymap is not installed.",
                        "Cannot display command result: jei is not installed."),
                        "All command payloads must deliver their exact absent-mod feedback to a real player");
                handler.getMethod("loggedOut").invoke(null);
                helper.assertTrue(loader.optionalLoads.isEmpty(),
                        "Guarded dispatch/logout must not even request optional API classes: " + loader.optionalLoads);
                for (String integration : List.of("JeiIntegration", "JourneyMapIntegration", "JourneyMapChunkOverlays")) {
                    helper.assertTrue(!loader.defined.contains(CLIENT + integration),
                            "Guarded dispatch must not define " + integration);
                }
            } finally {
                player.discard();
            }
        } catch (ReflectiveOperationException | IOException error) {
            throw new IllegalStateException("Absent-integration feedback GameTest failed", error);
        }
        helper.succeed();
    }

    private static Path classFile(Path root, String className) {
        return root.resolve(className.replace('.', File.separatorChar) + ".class");
    }

    private static String isolatedName(String name) {
        if (name.equals(MOD_LIST)) {
            return ISOLATED_CLIENT + "ModList";
        }
        String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) : name;
        return ISOLATED.contains(outer) ? ISOLATED_CLIENT + name.substring(CLIENT.length()) : name;
    }

    private static final class RecordingPlayer extends FakePlayer {
        private final List<String> messages = new ArrayList<>();

        private RecordingPlayer(ServerLevel level) {
            super(Objects.requireNonNull(level, "GameTest requires a real level"),
                    new GameProfile(UUID.randomUUID(), "AbsentModProbe"));
        }

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }
    }

    private static final class AbsentIntegrationLoader extends ClassLoader {
        private final List<String> optionalLoads = new ArrayList<>();
        private final Set<String> defined = new HashSet<>();
        private final Path mainClasses;

        private AbsentIntegrationLoader() {
            super(OptionalIntegrationFeedbackGameTests.class.getClassLoader());
            String configured = System.getProperty("farmupcraft.optionalAbsence.mainClasses");
            List<Path> candidates = configured == null
                    ? List.of(Path.of("build", "classes", "java", "main"),
                            Path.of("neoforge-26.2", "build", "classes", "java", "main"),
                            Path.of("..", "classes", "java", "main"))
                    : List.of(Path.of(configured));
            mainClasses = candidates.stream().map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> ISOLATED.stream().allMatch(name -> Files.isRegularFile(classFile(path, name))))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "Compile main first or set farmupcraft.optionalAbsence.mainClasses. Checked: " + candidates));
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("mezz.jei.") || name.startsWith("journeymap.")) {
                optionalLoads.add(name);
                throw new ClassNotFoundException("Optional mod deliberately absent: " + name);
            }
            if (!name.startsWith(ISOLATED_CLIENT)) {
                return super.loadClass(name, resolve);
            }
            String originalName = name.equals(isolatedName(MOD_LIST))
                    ? MOD_LIST : CLIENT + name.substring(ISOLATED_CLIENT.length());
            if (!isolatedName(originalName).equals(name)) {
                throw new ClassNotFoundException("Not a configured isolated production class: " + name);
            }
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                byte[] bytes;
                if (originalName.equals(MOD_LIST)) {
                    bytes = absentModList();
                } else {
                    try {
                        var reader = new ClassReader(Files.readAllBytes(classFile(mainClasses, originalName)));
                        if (!reader.getClassName().equals(originalName.replace('.', '/'))) {
                            throw new AssertionError("Unexpected raw class: " + originalName);
                        }
                        var writer = new ClassWriter(0);
                        reader.accept(new ClassRemapper(writer, new Remapper() {
                            @Override
                            public String map(String internalName) {
                                return isolatedName(internalName.replace('/', '.')).replace('.', '/');
                            }
                        }), 0);
                        bytes = writer.toByteArray();
                    } catch (IOException error) {
                        throw new ClassNotFoundException(name, error);
                    }
                }
                type = defineClass(name, bytes, 0, bytes.length);
                defined.add(originalName);
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }

        @Override
        public URL getResource(String name) {
            return optionalResource(name) ? null : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return optionalResource(name) ? Collections.emptyEnumeration() : super.getResources(name);
        }

        private static boolean optionalResource(String name) {
            return name.startsWith("mezz/jei/") || name.startsWith("journeymap/");
        }

        private static byte[] absentModList() {
            ClassDesc type = ClassDesc.of(isolatedName(MOD_LIST));
            return ClassFile.of().build(type, builder -> builder
                    .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                    .withMethodBody("<init>", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC,
                            code -> code.aload(0).invokespecial(CD_Object, "<init>",
                                    MethodTypeDesc.of(CD_void)).return_())
                    .withMethodBody("get", MethodTypeDesc.of(type),
                            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            code -> code.new_(type).dup().invokespecial(type, "<init>",
                                    MethodTypeDesc.of(CD_void)).areturn())
                    .withMethodBody("isLoaded", MethodTypeDesc.of(CD_boolean, CD_String),
                            ClassFile.ACC_PUBLIC, code -> code.iconst_0().ireturn()));
        }
    }
}
