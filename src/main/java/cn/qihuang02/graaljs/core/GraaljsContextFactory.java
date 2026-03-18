package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.Graaljs;
import cn.qihuang02.graaljs.binding.BindingsBuilder;
import cn.qihuang02.graaljs.binding.ConsoleAPI;
import cn.qihuang02.graaljs.binding.EventBusAPI;
import cn.qihuang02.graaljs.binding.ForgeEventBridge;
import cn.qihuang02.graaljs.binding.JavaAPI;
import cn.qihuang02.graaljs.binding.JavaAdapterAPI;
import cn.qihuang02.graaljs.binding.RuntimeAPI;
import cn.qihuang02.graaljs.binding.SchedulerAPI;
import cn.qihuang02.graaljs.bridge.CachedClassStorage;
import cn.qihuang02.graaljs.bridge.HostBridgeRegistry;
import cn.qihuang02.graaljs.bridge.JavaPackageProxy;
import cn.qihuang02.graaljs.error.ErrorReporter;
import cn.qihuang02.graaljs.error.LoggingErrorReporter;
import cn.qihuang02.graaljs.minecraft.MinecraftTypeWrappers;
import cn.qihuang02.graaljs.typewrap.DirectTypeWrapperFactory;
import cn.qihuang02.graaljs.typewrap.TypeWrapperFactory;
import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Constructor;
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
            "java.lang.ClassLoader",
            "java.lang.Thread",
            "java.lang.invoke",
            "java.lang.Class",
            "java.io.File",
            "java.nio.file",
            "java.net",
            "sun.",
            "jdk.",
            "com.sun.",
            "javax.management",
            "javax.script"
    );

    private final Path scriptRoot;
    private final TypeWrappers typeWrappers;
    private final HostBridgeRegistry hostBridgeRegistry;
    private final Map<ScriptType, GraaljsContext> activeContexts;
    private final Map<ScriptType, EventBusAPI> eventBuses;
    private final Map<ScriptType, SchedulerAPI> schedulers;
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
        this.schedulers = new EnumMap<>(ScriptType.class);
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

    public Path resolveModulesDirectory() {
        return scriptRoot.resolve("modules");
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
        MinecraftTypeWrappers.registerAll(wrappers);
    }

    protected GraaljsContext createContext(ScriptType type) {
        return new GraaljsContext(this, type);
    }

    protected void configureBindings(ScriptType type, BindingsBuilder builder) {
        builder.add("console", new ConsoleAPI(Graaljs.LOGGER));
    }

    /**
     * 注册 Forge 事件名到 Event 类的映射。子类可覆盖以添加自定义事件映射。
     * 默认注册常用的 Minecraft 事件。
     */
    protected void configureForgeEvents(ScriptType type, ForgeEventBridge bridge) {
        // 玩家事件
        bridge.registerMapping("player.join", PlayerEvent.PlayerLoggedInEvent.class);
        bridge.registerMapping("player.leave", PlayerEvent.PlayerLoggedOutEvent.class);
        bridge.registerMapping("player.respawn", PlayerEvent.PlayerRespawnEvent.class);
        bridge.registerMapping("player.interact.block", PlayerInteractEvent.RightClickBlock.class);
        bridge.registerMapping("player.interact.entity", PlayerInteractEvent.EntityInteract.class);
        bridge.registerMapping("player.chat", ServerChatEvent.class);

        // 实体事件
        bridge.registerMapping("entity.death", LivingDeathEvent.class);
        bridge.registerMapping("entity.hurt", LivingHurtEvent.class);

        // 世界事件（1.20+ 使用 LevelEvent）
        bridge.registerMapping("world.load", LevelEvent.Load.class);
        bridge.registerMapping("world.unload", LevelEvent.Unload.class);

        // 方块事件
        bridge.registerMapping("block.break", BlockEvent.BreakEvent.class);
        bridge.registerMapping("block.place", BlockEvent.EntityPlaceEvent.class);

        // 命令事件
        bridge.registerMapping("command.execute", CommandEvent.class);

        // Tick 事件
        bridge.registerMapping("server.tick", TickEvent.ServerTickEvent.class);
        bridge.registerMapping("client.tick", TickEvent.ClientTickEvent.class);
    }

    /**
     * 返回 Forge 事件总线。子类可覆盖以提供测试用的 mock 总线。
     */
    protected IEventBus getForgeEventBus() {
        return MinecraftForge.EVENT_BUS;
    }

    public GraaljsContext create(ScriptType type) {
        close(type);

        GraaljsContext context = createContext(type);
        EventBusAPI eventBus = new EventBusAPI(context);
        ForgeEventBridge forgeBridge = new ForgeEventBridge(context, getForgeEventBus());
        configureForgeEvents(type, forgeBridge);
        eventBus.setForgeBridge(forgeBridge);
        SchedulerAPI scheduler = new SchedulerAPI(context);
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
        builder.add("scheduler", scheduler);
        builder.add("runtime", new RuntimeAPI(this, context));
        context.initialize(builder.build());

        activeContexts.put(type, context);
        eventBuses.put(type, eventBus);
        schedulers.put(type, scheduler);
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

    public SchedulerAPI getScheduler(ScriptType type) {
        return schedulers.get(type);
    }

    public int tickScheduler(ScriptType type, long currentTimeMs) {
        SchedulerAPI scheduler = schedulers.get(type);
        if (scheduler == null) {
            return 0;
        }
        return scheduler.tick(currentTimeMs);
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
            EventBusAPI eventBus = eventBuses.get(type);
            if (eventBus != null) {
                eventBus.closeForgeBridge();
            }
            SchedulerAPI scheduler = schedulers.remove(type);
            if (scheduler != null) {
                scheduler.close();
            }
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

    private Map<String, Object> createEventEnvelope(ScriptType type, String eventName, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("name", eventName);
        envelope.put("scriptType", type.name().toLowerCase(Locale.ROOT));
        envelope.put("payload", payload);
        return envelope;
    }
}
