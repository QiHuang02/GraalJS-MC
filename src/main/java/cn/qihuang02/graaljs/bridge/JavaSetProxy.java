package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 为 Set 提供稳定顺序的数组式访问，同时暴露 JS Set 风格的方法。
 */
public class JavaSetProxy implements ProxyArray, ProxyObject, ProxyValue {
    private static final Set<String> METHOD_KEYS = Set.of(
            "size", "add", "has", "delete",
            "forEach", "clear", "toArray",
            "includes", "toString"
    );

    private final GraaljsContext context;
    private final Set<Object> set;

    @SuppressWarnings("unchecked")
    public JavaSetProxy(GraaljsContext context, Set<?> set) {
        this.context = context;
        this.set = (Set<Object>) set;
    }

    // ── ProxyArray ──

    @Override
    public Object get(long index) {
        List<Object> snapshot = snapshot();
        if (index < 0 || index >= snapshot.size()) {
            return null;
        }
        return context.javaToJs(snapshot.get((int) index));
    }

    @Override
    public void set(long index, Value value) {
        if (index < 0 || index > Integer.MAX_VALUE) {
            throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        }

        Object converted = context.jsToJava(value, Object.class);
        List<Object> snapshot = snapshot();
        int targetIndex = (int) index;
        if (targetIndex > snapshot.size()) {
            throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        }
        if (targetIndex == snapshot.size()) {
            set.add(converted);
            return;
        }

        Object previous = snapshot.get(targetIndex);
        set.remove(previous);
        set.add(converted);
    }

    @Override
    public boolean remove(long index) {
        List<Object> snapshot = snapshot();
        if (index < 0 || index >= snapshot.size()) {
            return false;
        }
        return set.remove(snapshot.get((int) index));
    }

    @Override
    public long getSize() {
        return set.size();
    }

    // ── ProxyObject ──

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "size" -> set.size();
            case "add" -> (ProxyExecutable) this::addFn;
            case "has" -> (ProxyExecutable) this::hasFn;
            case "delete" -> (ProxyExecutable) this::deleteFn;
            case "forEach" -> (ProxyExecutable) this::forEachFn;
            case "clear" -> (ProxyExecutable) this::clearFn;
            case "toArray" -> (ProxyExecutable) this::toArrayFn;
            case "includes" -> (ProxyExecutable) this::includesFn;
            case "toString" -> (ProxyExecutable) this::toStringFn;
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return METHOD_KEYS.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return METHOD_KEYS.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        // 只读成员，忽略
    }

    // ── ProxyValue ──

    @Override
    public Object unwrap() {
        return set;
    }

    // ── JS 方法实现 ──

    private Object addFn(Value... args) {
        if (args.length == 0) {
            return this;
        }
        Object converted = context.jsToJava(args[0], Object.class);
        set.add(converted);
        return this;
    }

    private Object hasFn(Value... args) {
        if (args.length == 0) {
            return false;
        }
        Object target = context.jsToJava(args[0], Object.class);
        return set.stream().anyMatch(element -> Objects.equals(element, target));
    }

    private Object deleteFn(Value... args) {
        if (args.length == 0) {
            return false;
        }
        Object target = context.jsToJava(args[0], Object.class);
        return set.remove(target);
    }

    private Object forEachFn(Value... args) {
        if (args.length == 0 || !args[0].canExecute()) {
            throw new IllegalArgumentException("First argument must be a function");
        }
        Value callback = args[0];
        for (Object element : set) {
            callback.execute(context.javaToJs(element), context.javaToJs(element), this);
        }
        return null;
    }

    private Object clearFn(Value... args) {
        set.clear();
        return null;
    }

    private Object toArrayFn(Value... args) {
        return new JavaListProxy(context, new ArrayList<>(set));
    }

    private Object includesFn(Value... args) {
        return hasFn(args);
    }

    private Object toStringFn(Value... args) {
        return set.toString();
    }

    private List<Object> snapshot() {
        return new ArrayList<>(set);
    }
}
