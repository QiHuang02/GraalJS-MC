package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.Graaljs;
import cn.qihuang02.graaljs.bridge.AbstractClassAdapter;
import cn.qihuang02.graaljs.bridge.InterfaceAdapter;
import cn.qihuang02.graaljs.bridge.ProxyValue;
import cn.qihuang02.graaljs.typewrap.EnumTypeWrapper;
import cn.qihuang02.graaljs.typewrap.TypeWrapperFactory;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * GraalJS 执行上下文。
 */
public class GraaljsContext {
    private final GraaljsContextFactory factory;
    private final ScriptType type;
    private final Map<Object, Object> wrappedValueCache;
    private Context context;

    public GraaljsContext(GraaljsContextFactory factory, ScriptType type) {
        this.factory = factory;
        this.type = type;
        this.wrappedValueCache = new IdentityHashMap<>();
    }

    public GraaljsContextFactory getFactory() {
        return factory;
    }

    public ScriptType getType() {
        return type;
    }

    public Context getPolyglotContext() {
        return context;
    }

    public void initialize(Map<String, Object> bindings) {
        context = Context.newBuilder("js")
                .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
                .allowHostClassLookup(className -> factory.visibleToScripts(className, ClassVisibilityContext.BINDING))
                .allowExperimentalOptions(true)
                .option("js.ecmascript-version", "2022")
                .option("js.nashorn-compat", "false")
                .build();

        Value jsBindings = context.getBindings("js");
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            jsBindings.putMember(entry.getKey(), javaToJs(entry.getValue()));
        }
    }

    public void addToScope(String name, Object value) {
        requireContext().getBindings("js").putMember(name, javaToJs(value));
    }

    public Value eval(Source source) {
        factory.bindCurrentContext(this);
        try {
            return requireContext().eval(source);
        } catch (PolyglotException exception) {
            throw logAndWrapScriptException(source, exception);
        } finally {
            factory.clearCurrentContext(this);
        }
    }

    public Value eval(String scriptName, String code) {
        return eval(Source.newBuilder("js", code, scriptName).buildLiteral());
    }

    public Object javaToJs(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof ProxyObject) {
            return value;
        }
        if (value instanceof Character character) {
            return String.valueOf(character);
        }
        if (value instanceof Value polyglotValue) {
            return polyglotValue;
        }
        if (value instanceof Class<?> javaClass) {
            return wrapJavaClass(javaClass);
        }
        return wrapAsJavaObject(value);
    }

    public Object wrapAsJavaObject(Object javaObject) {
        if (javaObject == null) {
            return null;
        }
        return wrappedValueCache.computeIfAbsent(javaObject, value -> factory.getHostBridgeRegistry().toJs(this, value));
    }

    public Object wrapJavaClass(Class<?> javaClass) {
        if (javaClass == null) {
            return null;
        }
        return wrappedValueCache.computeIfAbsent(javaClass, value -> factory.getHostBridgeRegistry().toJs(this, value));
    }

    public <T> T jsToJava(Object from, Class<T> target) {
        if (from instanceof Value originalValue && target == Value.class) {
            return target.cast(originalValue);
        }
        if (from instanceof Value originalValue
                && target.isInterface()
                && !Collection.class.isAssignableFrom(target)
                && !Map.class.isAssignableFrom(target)) {
            if (originalValue.isHostObject()) {
                Object hostObject = originalValue.asHostObject();
                if (target.isInstance(hostObject)) {
                    return target.cast(hostObject);
                }
            }
            return asInterface(originalValue, target);
        }

        Object normalized = normalizeJsValue(from);
        if (normalized == null) {
            if (target == Optional.class) {
                return target.cast(Optional.empty());
            }
            return null;
        }
        if (target == Object.class) {
            return target.cast(normalized);
        }
        if (target.isInstance(normalized)) {
            return target.cast(normalized);
        }

        if (target == String.class) {
            return target.cast(String.valueOf(normalized));
        }
        if (target == Boolean.class || target == boolean.class) {
            return castValue(target, normalized instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(normalized)));
        }
        if (Number.class.isAssignableFrom(box(target)) || target.isPrimitive()) {
            return convertNumber(target, normalized);
        }
        if (target.isEnum()) {
            return target.cast(new EnumTypeWrapper<>((Class<? extends Enum>) target).wrap(this, normalized, target));
        }
        if (target.isRecord() && normalized instanceof Map<?, ?> recordMap) {
            return target.cast(convertRecord(target, recordMap));
        }
        if (target.isArray() && normalized instanceof List<?> list) {
            return convertArray(target, list);
        }
        if (List.class.isAssignableFrom(target) && normalized instanceof List<?> list) {
            return target.cast(new ArrayList<>(list));
        }
        if (Set.class.isAssignableFrom(target) && normalized instanceof List<?> list) {
            return target.cast(new LinkedHashSet<>(list));
        }
        if (Map.class.isAssignableFrom(target) && normalized instanceof Map<?, ?> map) {
            return target.cast(new LinkedHashMap<>(map));
        }
        if (target == Optional.class) {
            return target.cast(Optional.of(normalized));
        }

        TypeWrapperFactory<T> wrapperFactory = factory.getTypeWrappers().getWrapperFactory(target, normalized);
        if (wrapperFactory != null) {
            return wrapperFactory.wrap(this, normalized, target);
        }

        throw new IllegalArgumentException("Unsupported JS to Java conversion: " + normalized.getClass().getName() + " -> " + target.getName());
    }

    public <T> T asInterface(Value value, Class<T> interfaceType) {
        return InterfaceAdapter.adapt(this, value, interfaceType);
    }

    public Object asInterfaces(Value value, Class<?>... interfaceTypes) {
        return InterfaceAdapter.adaptInterfaces(this, value, interfaceTypes);
    }

    public <T> T asAbstractClass(Value value, Class<T> abstractType, Object... constructorArgs) {
        return AbstractClassAdapter.adapt(this, value, abstractType, constructorArgs);
    }

    public <T> T asAbstractClass(Value value, Class<T> abstractType, Class<?>[] interfaceTypes, Object... constructorArgs) {
        return AbstractClassAdapter.adapt(this, value, abstractType, interfaceTypes, constructorArgs);
    }

    public void loadScripts() {
        Path scriptDirectory = factory.resolveScriptDirectory(type);
        if (!Files.isDirectory(scriptDirectory)) {
            try {
                Files.createDirectories(scriptDirectory);
            } catch (IOException exception) {
                Graaljs.LOGGER.error("Failed to create script directory: {}", scriptDirectory, exception);
            }
            Graaljs.LOGGER.info("No scripts found in {}", scriptDirectory);
            return;
        }

        try (Stream<Path> paths = Files.walk(scriptDirectory)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".js"))
                    .sorted()
                    .forEach(this::loadScript);
        } catch (IOException exception) {
            Graaljs.LOGGER.error("Failed to scan script directory: {}", scriptDirectory, exception);
        }
    }

    public void close() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception exception) {
                Graaljs.LOGGER.warn("Error closing context for {}: {}", type.directory, exception.getMessage());
            }
            context = null;
        }
        wrappedValueCache.clear();
    }

    private void loadScript(Path path) {
        try {
            Source source = Source.newBuilder("js", path.toFile())
                    .name(factory.resolveScriptDirectory(type).relativize(path).toString())
                    .build();
            eval(source);
            Graaljs.LOGGER.info("Loaded {} script: {}", type.name().toLowerCase(), path);
        } catch (IOException exception) {
            Graaljs.LOGGER.error("Failed to load script source: {}", path, exception);
        } catch (RuntimeException exception) {
            Graaljs.LOGGER.error("Failed to evaluate script: {}", path, exception);
        }
    }

    private Context requireContext() {
        if (context == null) {
            throw new IllegalStateException("Context not initialized");
        }
        return context;
    }

    private RuntimeException logAndWrapScriptException(Source source, PolyglotException exception) {
        String sourceName = source == null ? null : source.getName();
        int line = exception.getSourceLocation() != null ? exception.getSourceLocation().getStartLine() : -1;
        int lineOffset = exception.getSourceLocation() != null ? exception.getSourceLocation().getStartColumn() : -1;
        String lineSource = exception.getSourceLocation() != null && exception.getSourceLocation().getCharacters() != null
                ? exception.getSourceLocation().getCharacters().toString()
                : extractLineSource(source, line);
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Script execution failed"
                : exception.getMessage();
        Throwable reportedCause = exception;

        if (exception.isHostException()) {
            Throwable hostException = exception.asHostException();
            if ("Host exception is not visible to scripts".equals(hostException.getMessage())) {
                message = hostException.getMessage();
                reportedCause = hostException;
            } else if (factory.visibleToScripts(hostException.getClass().getName(), ClassVisibilityContext.EXCEPTION)) {
                String hostMessage = hostException.getMessage();
                message = hostMessage == null || hostMessage.isBlank()
                        ? hostException.getClass().getSimpleName()
                        : hostException.getClass().getSimpleName() + ": " + hostMessage;
                reportedCause = hostException;
            } else {
                message = "Host exception is not visible to scripts";
            }
        }

        factory.getErrorReporter().error(this, message, sourceName, line, lineSource, lineOffset, reportedCause);
        return factory.getErrorReporter().runtimeError(this, message, sourceName, line, lineSource, lineOffset, reportedCause);
    }

    private String extractLineSource(Source source, int line) {
        if (source == null || !source.hasCharacters()) {
            return null;
        }
        if (line <= 0) {
            return source.getCharacters().toString();
        }
        String[] lines = source.getCharacters().toString().split("\\R", -1);
        return line <= lines.length ? lines[line - 1] : null;
    }

    private Object normalizeJsValue(Object from) {
        if (!(from instanceof Value value)) {
            return from;
        }
        if (value.isNull()) {
            return null;
        }
        if (value.isProxyObject()) {
            Object proxyObject = value.asProxyObject();
            if (proxyObject instanceof ProxyValue proxyValue) {
                return proxyValue.unwrap();
            }
            return proxyObject;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(normalizeJsValue(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String memberKey : value.getMemberKeys()) {
                map.put(memberKey, normalizeJsValue(value.getMember(memberKey)));
            }
            return map;
        }
        return value.as(Object.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T convertNumber(Class<T> target, Object normalized) {
        if (!(normalized instanceof Number number)) {
            throw new IllegalArgumentException("Cannot convert non-number to " + target.getName());
        }

        Object result;
        if (target == Integer.class || target == int.class) {
            result = number.intValue();
        } else if (target == Long.class || target == long.class) {
            result = number.longValue();
        } else if (target == Double.class || target == double.class) {
            result = number.doubleValue();
        } else if (target == Float.class || target == float.class) {
            result = number.floatValue();
        } else if (target == Short.class || target == short.class) {
            result = number.shortValue();
        } else if (target == Byte.class || target == byte.class) {
            result = number.byteValue();
        } else {
            result = number;
        }
        return castValue(target, result);
    }

    @SuppressWarnings("unchecked")
    private <T> T convertArray(Class<T> target, List<?> values) {
        Class<?> componentType = target.getComponentType();
        Object array = java.lang.reflect.Array.newInstance(componentType, values.size());
        for (int i = 0; i < values.size(); i++) {
            Object converted = convertComponentValue(componentType, values.get(i));
            java.lang.reflect.Array.set(array, i, converted);
        }
        return (T) array;
    }

    private Object convertComponentValue(Class<?> componentType, Object value) {
        if (value == null) {
            return null;
        }
        if (componentType.isInstance(value)) {
            return value;
        }
        if (componentType.isArray() && value instanceof List<?> nestedList) {
            return convertArray(componentType, nestedList);
        }
        if (componentType.isEnum()) {
            return new EnumTypeWrapper<>((Class<? extends Enum>) componentType).wrap(this, value, componentType);
        }
        if (componentType == String.class) {
            return String.valueOf(value);
        }
        if (Number.class.isAssignableFrom(box(componentType)) || componentType.isPrimitive()) {
            return convertNumber((Class<Object>) componentType, value);
        }
        if (componentType == Boolean.class || componentType == boolean.class) {
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
        }
        TypeWrapperFactory<?> wrapperFactory = factory.getTypeWrappers().getWrapperFactory(componentType, value);
        if (wrapperFactory != null) {
            return wrapperFactory.wrap(this, value, componentType);
        }
        return value;
    }

    private Object convertRecord(Class<?> target, Map<?, ?> values) {
        Constructor<?> constructor = factory.getRecordConstructor(target);
        if (constructor == null) {
            throw new IllegalArgumentException("No record constructor available for " + target.getName());
        }

        RecordComponent[] components = target.getRecordComponents();
        Object[] defaults = factory.getDefaultRecordProperties(target);
        Object[] arguments = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Object rawValue = values.containsKey(component.getName()) ? values.get(component.getName()) : null;
            if (rawValue == null && defaults != null && i < defaults.length) {
                arguments[i] = defaults[i];
            } else if (rawValue == null) {
                arguments[i] = defaultValue(component.getType());
            } else {
                arguments[i] = convertComponentValue(component.getType(), rawValue);
            }
        }

        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate record " + target.getName(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T castValue(Class<T> target, Object value) {
        if (target.isPrimitive()) {
            return (T) value;
        }
        return target.cast(value);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
