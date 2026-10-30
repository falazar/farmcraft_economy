package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Executes compiled production guards in a child loader that cannot see either optional mod.
 * Reads raw build output, not FML's transformed resource stream. Harness copies have their class
 * names relocated because FML's guardian also instruments protected names at defineClass time;
 * guard instructions are preserved and the real global class-loading guardian remains enabled.
 * Only ModList's get/isLoaded boundary is substituted; Minecraft, packets and events are real.
 * This proves guarded registration/dispatch, not NeoForge mod discovery, rendering or a GUI session.
 * Registration is invoked directly, not through the real client event bus. Waypoint payloads currently
 * warn in the log only; frecipe provides the asserted player-visible messages. Command payload feedback
 * requires a real level and is asserted by OptionalIntegrationFeedbackGameTests in src/gameTest.
 * Follow up with an actual client without either mod to verify discovery, event-bus subscription,
 * reconnect/logout and visual behavior. No absent-mod GUI acceptance is implied by these tests.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@Timeout(30)
class OptionalIntegrationAbsenceTest {
    private static final String CLIENT = "com.falazar.farmupcraft.client.";
    private static final String ISOLATED_CLIENT = "optionalabsence.isolated.client.";
    private static final String MOD_LIST = "net.neoforged.fml.ModList";
    private static final List<String> API_CLASSES = List.of(
            "mezz.jei.api.IModPlugin",
            "mezz.jei.api.runtime.IJeiRuntime",
            "journeymap.api.v2.client.IClientPlugin",
            "journeymap.api.v2.client.IClientAPI");
    private static final Set<String> ISOLATED = Set.of(
            CLIENT + "ClientDatabaseEvents", CLIENT + "ClientRecipeCommand",
            CLIENT + "CommandPayloadHandler", CLIENT + "WaypointPayloadHandler",
            CLIENT + "JeiIntegration", CLIENT + "JourneyMapIntegration",
            CLIENT + "JourneyMapChunkOverlays");

    @Test
    void optionalApisAreActuallyUnavailableAndUnguardedPluginsFailToLink() throws Exception {
        var loader = new AbsentIntegrationLoader();
        assertApisUnavailable(loader);

        // Negative controls: the real plugin bytecode must fail, not silently use the parent's APIs.
        for (String plugin : List.of("JeiIntegration", "JourneyMapIntegration")) {
            assertTrue(Files.isRegularFile(classFile(loader.mainClasses, CLIENT + plugin)));
            NoClassDefFoundError error = assertThrows(NoClassDefFoundError.class,
                    () -> Class.forName(isolatedName(CLIENT + plugin), true, loader), plugin);
            String expectedApi = plugin.equals("JeiIntegration")
                    ? "mezz.jei.api.IModPlugin" : "journeymap.api.v2.client.IClientPlugin";
            assertEquals(expectedApi.replace('.', '/'), error.getMessage(), plugin);
            assertInstanceOf(ClassNotFoundException.class, error.getCause());
            assertTrue(loader.optionalLoads.contains(expectedApi), plugin);
        }
    }

    @Test
    void clientRegistrationAndWaypointDispatchSurviveAbsence() throws Exception {
        var loader = new AbsentIntegrationLoader();
        assertApisUnavailable(loader);
        loader.optionalLoads.clear();
        var registration = new RecordingRegistration();
        Class<?> bootstrap = Class.forName(isolatedName(CLIENT + "ClientDatabaseEvents"), true, loader);
        assertSame(loader, bootstrap.getClassLoader());
        assertDoesNotThrow(() -> bootstrap
                .getMethod("registerPayloadHandlers", RegisterClientPayloadHandlersEvent.class)
                .invoke(null, registration));
        assertEquals(8, registration.handlers.size());

        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("Absent waypoint integrations must not access the context: " + method);
                });
        List<CustomPacketPayload> payloads = List.of(
                new AddJMWaypointPacket(0, 64, 0, "absent", "minecraft:overworld"),
                new RemoveModWaypointsPacket(0, 64, 0, 10, true),
                new ScanWaypointsPacket(List.of(new ScanWaypointsPacket.Entry("absent", 0, 64, 0, 0))),
                new ScanWaypointsPacket());
        for (CustomPacketPayload payload : payloads) {
            assertDoesNotThrow(() -> registration.dispatch(payload, context), payload.type().toString());
        }

        // Both actual logout guard entry points, without invoking unrelated database-cache cleanup.
        for (String handler : List.of("WaypointPayloadHandler", "CommandPayloadHandler")) {
            Class<?> type = Class.forName(isolatedName(CLIENT + handler), true, loader);
            assertSame(loader, type.getClassLoader());
            assertDoesNotThrow(() -> type.getMethod("loggedOut").invoke(null));
        }
        assertTrue(loader.optionalLoads.isEmpty(), () -> "Optional classes requested: " + loader.optionalLoads);
        assertNoIntegrationWasLoaded(loader);
    }

    @Test
    void recipeCommandRegistersAndProducesVisibleFallbackWithoutJei() throws Exception {
        var loader = new AbsentIntegrationLoader();
        assertApisUnavailable(loader);
        loader.optionalLoads.clear();
        Class<?> command = Class.forName(isolatedName(CLIENT + "ClientRecipeCommand"), true, loader);
        assertSame(loader, command.getClassLoader());
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var event = new RegisterClientCommandsEvent(dispatcher, null);
        assertDoesNotThrow(() -> command
                .getMethod("onRegisterClientCommands", RegisterClientCommandsEvent.class)
                .invoke(null, event));
        assertNotNull(dispatcher.getRoot().getChild("frecipe"));

        var open = command.getDeclaredMethod("openRecipeForItem", String.class, Consumer.class);
        open.setAccessible(true);
        List<Component> feedback = new ArrayList<>();
        Consumer<Component> sink = feedback::add;
        assertDoesNotThrow(() -> open.invoke(null, "minecraft:bread", sink));
        assertDoesNotThrow(() -> open.invoke(null, "not an identifier", sink));
        assertEquals(List.of("Cannot open recipe: JEI is not installed.",
                        "Cannot open recipe: invalid item identifier not an identifier"),
                feedback.stream().map(Component::getString).toList());
        assertTrue(loader.optionalLoads.isEmpty(), () -> "Optional classes requested: " + loader.optionalLoads);
        assertNoIntegrationWasLoaded(loader);
    }

    private static void assertApisUnavailable(AbsentIntegrationLoader loader) {
        for (String api : API_CLASSES) {
            // These APIs really exist in the normal test runtime; absence is enforced by the loader.
            assertNotNull(loader.getParent().getResource(resource(api)), "Missing positive control: " + api);
            assertThrows(ClassNotFoundException.class, () -> Class.forName(api, false, loader), api);
            assertNull(loader.getResource(resource(api)), api);
            assertFalse(assertDoesNotThrow(() -> loader.getResources(resource(api))).hasMoreElements(), api);
        }
    }

    private static void assertNoIntegrationWasLoaded(AbsentIntegrationLoader loader) {
        for (String integration : List.of("JeiIntegration", "JourneyMapIntegration", "JourneyMapChunkOverlays")) {
            assertFalse(loader.defined.contains(CLIENT + integration), integration);
        }
    }

    private static String resource(String className) {
        return className.replace('.', '/') + ".class";
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

    private static final class RecordingRegistration extends RegisterClientPayloadHandlersEvent {
        private final Map<CustomPacketPayload.Type<?>, IPayloadHandler<?>> handlers = new HashMap<>();

        @Override
        public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
                                                           IPayloadHandler<T> handler) {
            assertNull(handlers.put(type, handler), "Duplicate payload handler: " + type);
        }

        @SuppressWarnings("unchecked")
        private <T extends CustomPacketPayload> void dispatch(T payload, IPayloadContext context) {
            var handler = (IPayloadHandler<T>) handlers.get(payload.type());
            assertNotNull(handler, "Unregistered payload: " + payload.type());
            handler.handle(payload, context);
        }
    }

    private static final class AbsentIntegrationLoader extends ClassLoader {
        private final List<String> optionalLoads = new ArrayList<>();
        private final Set<String> defined = new HashSet<>();
        private final Path mainClasses;

        private AbsentIntegrationLoader() {
            super(OptionalIntegrationAbsenceTest.class.getClassLoader());
            String configured = System.getProperty("farmupcraft.optionalAbsence.mainClasses");
            List<Path> candidates = configured == null
                    ? List.of(Path.of("build", "classes", "java", "main"),
                            Path.of("neoforge-26.2", "build", "classes", "java", "main"),
                            Path.of("..", "classes", "java", "main"))
                    : List.of(Path.of(configured));
            mainClasses = candidates.stream().map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> ISOLATED.stream().allMatch(name -> Files.isRegularFile(classFile(path, name))))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "Raw compiled main classes not found. Compile main first; when running outside the "
                                    + "project, set farmupcraft.optionalAbsence.mainClasses to build/classes/java/main. "
                                    + "Checked: " + candidates));
        }

        private static boolean optional(String name) {
            return name.startsWith("mezz.jei.") || name.startsWith("journeymap.");
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (optional(name)) {
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
                        bytes = relocate(Files.readAllBytes(classFile(mainClasses, originalName)), originalName);
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

        private static byte[] relocate(byte[] bytes, String originalName) {
            var reader = new ClassReader(bytes);
            assertEquals(originalName.replace('.', '/'), reader.getClassName(), "Unexpected raw class");
            var writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, new Remapper() {
                @Override
                public String map(String name) {
                    return isolatedName(name.replace('/', '.')).replace('.', '/');
                }
            }), 0);
            return writer.toByteArray();
        }

        // A loader-local ModList avoids mutating the real global mod list or production bytecode.
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
