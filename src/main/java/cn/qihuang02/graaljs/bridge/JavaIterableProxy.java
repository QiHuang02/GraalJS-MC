package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyIterable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Set;

/**
 * 将 Java {@link Iterable}（非 List/Set/Map）包装为 GraalVM {@link ProxyIterable}，
 * 使其在 JS 中支持 {@code for...of} 语法。
 * <p>同时实现 {@link ProxyObject} 暴露 {@code forEach} 等便捷方法。
 */
public class JavaIterableProxy implements ProxyIterable, ProxyObject, ProxyValue {
    private static final Set<String> MEMBER_KEYS = Set.of("forEach", "toString");

    private final GraaljsContext context;
    private final Iterable<?> iterable;

    public JavaIterableProxy(GraaljsContext context, Iterable<?> iterable) {
        this.context = context;
        this.iterable = iterable;
    }

    // ── ProxyIterable ──

    @Override
    public Object getIterator() {
        return new JavaIteratorProxy(context, iterable.iterator());
    }

    // ── ProxyObject ──

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "forEach" -> (ProxyExecutable) this::forEachFn;
            case "toString" -> (ProxyExecutable) this::toStringFn;
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return MEMBER_KEYS.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBER_KEYS.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        // 只读
    }

    // ── ProxyValue ──

    @Override
    public Object unwrap() {
        return iterable;
    }

    // ── JS 方法实现 ──

    private Object forEachFn(Value... args) {
        if (args.length == 0 || !args[0].canExecute()) {
            throw new IllegalArgumentException("First argument must be a function");
        }
        Value callback = args[0];
        for (Object element : iterable) {
            callback.execute(context.javaToJs(element));
        }
        return null;
    }

    private Object toStringFn(Value... args) {
        return iterable.toString();
    }
}
