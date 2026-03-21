package cn.qihuang02.graaljs.core;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * GraalJS 上下文工厂与生命周期入口。
 */
public class GraaljsContextFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraaljsContextFactory.class);

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
    private final Map<Class<?>, Object[]> defaultRecordProperties;
    private final Map<Class<?>, Constructor<?>> recordConstructors;
    private final ThreadLocal<GraaljsContext> currentContext;
    private boolean instanceStaticFallback;
    private CachedClassStorage cachedClassStorage;
    private ErrorReporter errorReporter;

    public GraaljsContextFactory(Path scriptRoot) {
        this.scriptRoot = scriptRoot;
        this.typeWrappers = new TypeWrappers();
        this.hostBridgeRegistry = new HostBridgeRegistry();
        this.activeContexts = new EnumMap<>(ScriptType.class);
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
                LOGGER.debug("Denied class {} in visibility context {}", className, visibilityContext);
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
    }

    protected GraaljsContext createContext(ScriptType type) {
        return new GraaljsContext(this, type);
    }

    /**
     * 配置脚本绑定。子类可覆盖以添加自定义绑定。
     */
    protected void configureBindings(ScriptType type, Map<String, Object> bindings) {
    }

    public GraaljsContext create(ScriptType type) {
        close(type);

        GraaljsContext context = createContext(type);
        Map<String, Object> bindings = new LinkedHashMap<>();
        configureBindings(type, bindings);
        context.initialize(Map.copyOf(bindings));

        activeContexts.put(type, context);
        LOGGER.info("Created GraalJS context for {}", type.directory);
        return context;
    }

    public GraaljsContext createAndLoad(ScriptType type) {
        GraaljsContext context = create(type);
        context.loadScripts();
        LOGGER.info("Loaded scripts for {}", type.directory);
        return context;
    }

    public GraaljsContext reload(ScriptType type) {
        LOGGER.info("Reloading GraalJS context for {}", type.directory);
        return createAndLoad(type);
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

    public JavaPackageProxy createPackageProxy(GraaljsContext context, String packageName) {
        return new JavaPackageProxy(context, this, packageName);
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
        GraaljsContext context = activeContexts.remove(type);
        if (context != null) {
            context.close();
            LOGGER.info("Closed GraalJS context for {}", type.directory);
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
}
