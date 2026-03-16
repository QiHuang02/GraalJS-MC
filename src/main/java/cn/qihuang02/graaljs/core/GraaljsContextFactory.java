package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.Graaljs;
import cn.qihuang02.graaljs.binding.BindingsBuilder;
import cn.qihuang02.graaljs.binding.ConsoleAPI;
import cn.qihuang02.graaljs.binding.EventBusAPI;
import cn.qihuang02.graaljs.binding.JavaAPI;
import cn.qihuang02.graaljs.binding.JavaAdapterAPI;
import cn.qihuang02.graaljs.binding.RuntimeAPI;
import cn.qihuang02.graaljs.bridge.CachedClassStorage;
import cn.qihuang02.graaljs.bridge.HostBridgeRegistry;
import cn.qihuang02.graaljs.bridge.JavaPackageProxy;
import cn.qihuang02.graaljs.error.ErrorReporter;
import cn.qihuang02.graaljs.error.LoggingErrorReporter;
import cn.qihuang02.graaljs.typewrap.DirectTypeWrapperFactory;
import cn.qihuang02.graaljs.typewrap.TypeWrapperFactory;
import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GraalJS 上下文工厂与生命周期入口。
 */
public class GraaljsContextFactory {
    private static final Set<String> CLASS_BLACKLIST = Set.of(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.System",
            "java.lang.reflect",
            "java.io.File",
            "java.nio.file",
            "java.net"
    );

    private final Path scriptRoot;
    private final TypeWrappers typeWrappers;
    private final HostBridgeRegistry hostBridgeRegistry;
    private final Map<ScriptType, GraaljsContext> activeContexts;
    private final Map<ScriptType, EventBusAPI> eventBuses;
    private final Map<Class<?>, Object[]> defaultRecordProperties;
    private final Map<Class<?>, Constructor<?>> recordConstructors;
    private final ThreadLocal<GraaljsContext> currentContext;
    private boolean instanceStaticFallback;
    private CachedClassStorage cachedClassStorage;
    private ErrorReporter errorReporter;

    public GraaljsContextFactory() {
        this(FMLPaths.GAMEDIR.get());
    }

    public GraaljsContextFactory(Path scriptRoot) {
        this.scriptRoot = scriptRoot;
        this.typeWrappers = new TypeWrappers();
        this.hostBridgeRegistry = new HostBridgeRegistry();
        this.activeContexts = new EnumMap<>(ScriptType.class);
        this.eventBuses = new EnumMap<>(ScriptType.class);
        this.defaultRecordProperties = new IdentityHashMap<>();
        this.recordConstructors = new IdentityHashMap<>();
        this.currentContext = new ThreadLocal<>();
        this.instanceStaticFallback = true;
        this.cachedClassStorage = new CachedClassStorage(false, (type, context) -> visibleToScripts(type.getName(), context));
        this.errorReporter = new LoggingErrorReporter();
        initTypeWrappers(typeWrappers);
    }

    public TypeWrappers getTypeWrappers() {
        return typeWrappers;
    }

    public HostBridgeRegistry getHostBridgeRegistry() {
        return hostBridgeRegistry;
    }

    public ErrorReporter getErrorReporter() {
        return errorReporter;
    }

    public boolean getInstanceStaticFallback() {
        return instanceStaticFallback;
    }

    public void setInstanceStaticFallback(boolean instanceStaticFallback) {
        this.instanceStaticFallback = instanceStaticFallback;
    }

    public void setErrorReporter(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    public CachedClassStorage getCachedClassStorage() {
        return cachedClassStorage;
    }

    public void setCachedClassStorage(CachedClassStorage cachedClassStorage) {
        this.cachedClassStorage = cachedClassStorage;
    }

    public synchronized void registerDefaultRecordProperties(Record record) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Object[] properties = new Object[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                properties[i] = components[i].getAccessor().invoke(record);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to extract default record properties for " + record.getClass().getName(), exception);
        }
        defaultRecordProperties.put(record.getClass(), properties);
    }

    public synchronized Object[] getDefaultRecordProperties(Class<?> type) {
        return defaultRecordProperties.get(type);
    }

    public synchronized Constructor<?> getRecordConstructor(Class<?> type) {
        if (!type.isRecord()) {
            return null;
        }
        return recordConstructors.computeIfAbsent(type, key -> {
            try {
                RecordComponent[] components = key.getRecordComponents();
                Class<?>[] parameterTypes = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    parameterTypes[i] = components[i].getType();
                }
                Constructor<?> constructor = key.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to resolve record constructor for " + key.getName(), exception);
            }
        });
    }

    public Path getScriptRoot() {
        return scriptRoot;
    }

    public Path resolveScriptDirectory(ScriptType type) {
        return scriptRoot.resolve(type.directory);
    }

    public boolean visibleToScripts(String className) {
        return visibleToScripts(className, ClassVisibilityContext.CLASS_LOOKUP);
    }

    public boolean visibleToScripts(String className, ClassVisibilityContext visibilityContext) {
        for (String blocked : CLASS_BLACKLIST) {
            if (className.startsWith(blocked)) {
                Graaljs.LOGGER.debug("Denied class {} in visibility context {}", className, visibilityContext);
                return false;
            }
        }
        return true;
    }

    public <T> void registerTypeWrapper(Class<T> target, TypeWrapperFactory<T> factory) {
        typeWrappers.register(target, factory);
    }

    public <T> void registerTypeWrapper(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory) {
        typeWrappers.register(target, validator, factory);
    }

    public <T> void registerDirectTypeWrapper(Class<T> target, DirectTypeWrapperFactory<T> factory) {
        typeWrappers.registerDirect(target, factory);
    }

    protected void initTypeWrappers(TypeWrappers wrappers) {
        registerBuiltinMinecraftTypeWrappers(wrappers);
    }

    protected GraaljsContext createContext(ScriptType type) {
        return new GraaljsContext(this, type);
    }

    protected void configureBindings(ScriptType type, BindingsBuilder builder) {
        builder.add("console", new ConsoleAPI(Graaljs.LOGGER));
    }

    public GraaljsContext create(ScriptType type) {
        close(type);

        GraaljsContext context = createContext(type);
        EventBusAPI eventBus = new EventBusAPI(context);
        BindingsBuilder builder = new BindingsBuilder();
        configureBindings(type, builder);
        builder.add("Java", new JavaAPI(this, context));
        builder.add("JavaAdapter", new JavaAdapterAPI(this, context));
        builder.add("Packages", createPackageProxy(context, ""));
        builder.add("java", createPackageProxy(context, "java"));
        builder.add("javax", createPackageProxy(context, "javax"));
        builder.add("com", createPackageProxy(context, "com"));
        builder.add("org", createPackageProxy(context, "org"));
        builder.add("net", createPackageProxy(context, "net"));
        builder.add("dev", createPackageProxy(context, "dev"));
        builder.add("cn", createPackageProxy(context, "cn"));
        builder.add("events", eventBus);
        builder.add("runtime", new RuntimeAPI(this, context));
        context.initialize(builder.build());

        activeContexts.put(type, context);
        eventBuses.put(type, eventBus);
        Graaljs.LOGGER.info("Created GraalJS context for {}", type.directory);
        return context;
    }

    public GraaljsContext createAndLoad(ScriptType type) {
        GraaljsContext context = create(type);
        context.loadScripts();
        emitEvent(type, "context.loaded", Map.of(
                "directory", resolveScriptDirectory(type).toString()
        ));
        return context;
    }

    public GraaljsContext reload(ScriptType type) {
        Graaljs.LOGGER.info("Reloading GraalJS context for {}", type.directory);
        GraaljsContext context = createAndLoad(type);
        emitEvent(type, "context.reloaded", Map.of(
                "directory", resolveScriptDirectory(type).toString()
        ));
        return context;
    }

    public GraaljsContext enter(ScriptType type) {
        GraaljsContext context = activeContexts.get(type);
        if (context == null) {
            context = create(type);
        }
        bindCurrentContext(context);
        return context;
    }

    public GraaljsContext current() {
        return currentContext.get();
    }

    public GraaljsContext getContext(ScriptType type) {
        return activeContexts.get(type);
    }

    public EventBusAPI getEventBus(ScriptType type) {
        return eventBuses.get(type);
    }

    public JavaPackageProxy createPackageProxy(GraaljsContext context, String packageName) {
        return new JavaPackageProxy(context, this, packageName);
    }

    public int emitEvent(ScriptType type, String eventName) {
        return emitEvent(type, eventName, Map.of());
    }

    public int emitEvent(ScriptType type, String eventName, Object payload) {
        EventBusAPI eventBus = eventBuses.get(type);
        if (eventBus == null) {
            return 0;
        }
        return eventBus.emitHost(eventName, createEventEnvelope(type, eventName, payload));
    }

    public Class<?> resolveVisibleClass(String className) throws ClassNotFoundException {
        return resolveVisibleClass(className, ClassVisibilityContext.CLASS_LOOKUP);
    }

    public Class<?> resolveVisibleClass(String className, ClassVisibilityContext visibilityContext) throws ClassNotFoundException {
        if (className == null || className.isBlank() || !visibleToScripts(className, visibilityContext)) {
            throw new IllegalArgumentException("Class is not visible to scripts: " + className);
        }
        return Class.forName(className);
    }

    public Class<?> resolveVisibleClassOrNull(String className) {
        return resolveVisibleClassOrNull(className, ClassVisibilityContext.CLASS_LOOKUP);
    }

    public Class<?> resolveVisibleClassOrNull(String className, ClassVisibilityContext visibilityContext) {
        if (className == null || className.isBlank() || !visibleToScripts(className, visibilityContext)) {
            return null;
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    public void close(ScriptType type) {
        GraaljsContext context = activeContexts.get(type);
        if (context != null) {
            emitEvent(type, "context.closing", Map.of(
                    "directory", resolveScriptDirectory(type).toString()
            ));
            activeContexts.remove(type);
            eventBuses.remove(type);
            context.close();
            Graaljs.LOGGER.info("Closed GraalJS context for {}", type.directory);
        }
    }

    public void closeAll() {
        for (ScriptType type : ScriptType.values()) {
            close(type);
        }
    }

    void bindCurrentContext(GraaljsContext context) {
        currentContext.set(context);
    }

    void clearCurrentContext(GraaljsContext context) {
        if (currentContext.get() == context) {
            currentContext.remove();
        }
    }

    private void registerBuiltinMinecraftTypeWrappers(TypeWrappers wrappers) {
        if (!wrappers.contains(ResourceLocation.class)) {
            wrappers.register(ResourceLocation.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
                if (from instanceof ResourceLocation location) {
                    return location;
                }
                if (from instanceof CharSequence sequence) {
                    ResourceLocation location = ResourceLocation.tryParse(sequence.toString());
                    if (location != null) {
                        return location;
                    }
                }
                throw new IllegalArgumentException("Cannot convert value to ResourceLocation: " + from);
            });
        }

        if (!wrappers.contains(BlockPos.class)) {
            wrappers.register(BlockPos.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
                if (from instanceof BlockPos blockPos) {
                    return blockPos;
                }
                if (from instanceof java.util.List<?> list && list.size() >= 3) {
                    return new BlockPos(
                            ((Number) list.get(0)).intValue(),
                            ((Number) list.get(1)).intValue(),
                            ((Number) list.get(2)).intValue()
                    );
                }
                if (from instanceof java.util.Map<?, ?> map) {
                    return new BlockPos(
                            ((Number) map.get("x")).intValue(),
                            ((Number) map.get("y")).intValue(),
                            ((Number) map.get("z")).intValue()
                    );
                }
                if (from instanceof CharSequence sequence) {
                    String[] parts = sequence.toString().trim().split("[,\\s]+");
                    if (parts.length >= 3) {
                        return new BlockPos(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2])
                        );
                    }
                }
                throw new IllegalArgumentException("Cannot convert value to BlockPos: " + from);
            });
        }

        if (!wrappers.contains(Vec3.class)) {
            wrappers.register(Vec3.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
                if (from instanceof Vec3 vec3) {
                    return vec3;
                }
                if (from instanceof java.util.List<?> list && list.size() >= 3) {
                    return new Vec3(
                            ((Number) list.get(0)).doubleValue(),
                            ((Number) list.get(1)).doubleValue(),
                            ((Number) list.get(2)).doubleValue()
                    );
                }
                if (from instanceof java.util.Map<?, ?> map) {
                    return new Vec3(
                            ((Number) map.get("x")).doubleValue(),
                            ((Number) map.get("y")).doubleValue(),
                            ((Number) map.get("z")).doubleValue()
                    );
                }
                if (from instanceof CharSequence sequence) {
                    String[] parts = sequence.toString().trim().split("[,\\s]+");
                    if (parts.length >= 3) {
                        return new Vec3(
                                Double.parseDouble(parts[0]),
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2])
                        );
                    }
                }
                throw new IllegalArgumentException("Cannot convert value to Vec3: " + from);
            });
        }

        if (!wrappers.contains(Component.class)) {
            wrappers.register(Component.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
                if (from instanceof Component component) {
                    return component;
                }
                if (from instanceof CharSequence sequence) {
                    return Component.literal(sequence.toString());
                }
                if (from instanceof java.util.Map<?, ?> map && map.containsKey("text")) {
                    return Component.literal(String.valueOf(map.get("text")));
                }
                throw new IllegalArgumentException("Cannot convert value to Component: " + from);
            });
        }

        if (!wrappers.contains(ItemStack.class)) {
            wrappers.register(ItemStack.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
                if (from instanceof ItemStack stack) {
                    return stack;
                }
                if (from instanceof Item item) {
                    return new ItemStack(item);
                }
                if (from instanceof CharSequence sequence) {
                    Item item = resolveItem(sequence.toString());
                    if (item != null) {
                        return new ItemStack(item);
                    }
                }
                if (from instanceof java.util.Map<?, ?> map) {
                    Object itemValue = map.get("item");
                    Object countValue = map.get("count");
                    ItemStack base = context.jsToJava(itemValue, ItemStack.class);
                    int count = countValue instanceof Number number ? number.intValue() : 1;
                    ItemStack copy = base.copy();
                    copy.setCount(count);
                    return copy;
                }
                throw new IllegalArgumentException("Cannot convert value to ItemStack: " + from);
            });
        }
    }

    private Item resolveItem(String rawId) {
        ensureMinecraftBootstrap();
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return null;
        }

        Item vanillaItem = resolveVanillaItem(id);
        if (vanillaItem != null) {
            return vanillaItem;
        }

        try {
            Item registryItem = BuiltInRegistries.ITEM.get(id);
            return registryItem != null && registryItem != Items.AIR ? registryItem : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Item resolveVanillaItem(ResourceLocation id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }

        String fieldName = id.getPath().toUpperCase(Locale.ROOT).replace('/', '_');
        try {
            Field field = Items.class.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Item item ? item : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void ensureMinecraftBootstrap() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
            // 游戏运行期通常已经完成引导；测试环境缺失时尽量补齐即可。
        }
    }

    private Map<String, Object> createEventEnvelope(ScriptType type, String eventName, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("name", eventName);
        envelope.put("scriptType", type.name().toLowerCase(Locale.ROOT));
        envelope.put("payload", payload);
        return envelope;
    }
}
