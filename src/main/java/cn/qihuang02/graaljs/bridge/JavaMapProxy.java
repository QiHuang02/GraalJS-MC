package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为 Map 提供对象式访问，同时暴露 JS Map 风格的方法。
 */
public class JavaMapProxy implements ProxyObject, ProxyValue {
    private static final Set<String> METHOD_KEYS = Set.of(
            "size", "keys", "values", "entries",
            "has", "delete", "forEach", "clear",
            "toString",
            "get", "set", "hasOwnProperty"
    );

    private final GraaljsContext context;
    private final Map<Object, Object> map;

    @SuppressWarnings("unchecked")
    public JavaMapProxy(GraaljsContext context, Map<?, ?> source) {
        this.context = context;
        this.map = (Map<Object, Object>) source;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "size" -> map.size();
            case "keys" -> (ProxyExecutable) this::keysFn;
            case "values" -> (ProxyExecutable) this::valuesFn;
            case "entries" -> (ProxyExecutable) this::entriesFn;
            case "has" -> (ProxyExecutable) this::hasFn;
            case "delete" -> (ProxyExecutable) this::deleteFn;
            case "forEach" -> (ProxyExecutable) this::forEachFn;
            case "clear" -> (ProxyExecutable) this::clearFn;
            case "toString" -> (ProxyExecutable) this::toStringFn;
            case "get" -> (ProxyExecutable) this::getFn;
            case "set" -> (ProxyExecutable) this::setFn;
            case "hasOwnProperty" -> (ProxyExecutable) this::hasOwnPropertyFn;
            default -> context.javaToJs(map.get(key));
        };
    }

    @Override
    public Object getMemberKeys() {
        List<String> allKeys = new ArrayList<>(METHOD_KEYS);
        for (Object key : map.keySet()) {
            String strKey = String.valueOf(key);
            if (!METHOD_KEYS.contains(strKey)) {
                allKeys.add(strKey);
            }
        }
        return allKeys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return METHOD_KEYS.contains(key) || map.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        if (METHOD_KEYS.contains(key)) {
            return; // 方法名不可覆盖
        }
        map.put(key, context.jsToJava(value, Object.class));
    }

    @Override
    public boolean removeMember(String key) {
        return map.remove(key) != null;
    }

    @Override
    public Object unwrap() {
        return map;
    }

    // ── JS 方法实现 ──

    private Object keysFn(Value... args) {
        List<Object> keys = new ArrayList<>(map.keySet());
        return new JavaListProxy(context, keys);
    }

    private Object valuesFn(Value... args) {
        List<Object> values = new ArrayList<>(map.values());
        return new JavaListProxy(context, values);
    }

    private Object entriesFn(Value... args) {
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            List<Object> pair = List.of(
                    entry.getKey() == null ? "null" : entry.getKey(),
                    entry.getValue() == null ? "null" : entry.getValue()
            );
            entries.add(new JavaListProxy(context, new ArrayList<>(pair)));
        }
        return new JavaListProxy(context, entries);
    }

    private Object hasFn(Value... args) {
        if (args.length == 0) {
            return false;
        }
        String key = args[0].isString() ? args[0].asString() : String.valueOf(context.jsToJava(args[0], Object.class));
        return map.containsKey(key);
    }

    private Object deleteFn(Value... args) {
        if (args.length == 0) {
            return false;
        }
        String key = args[0].isString() ? args[0].asString() : String.valueOf(context.jsToJava(args[0], Object.class));
        return map.remove(key) != null;
    }

    private Object forEachFn(Value... args) {
        if (args.length == 0 || !args[0].canExecute()) {
            throw new IllegalArgumentException("First argument must be a function");
        }
        Value callback = args[0];
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            callback.execute(context.javaToJs(entry.getValue()), context.javaToJs(entry.getKey()), this);
        }
        return null;
    }

    private Object clearFn(Value... args) {
        map.clear();
        return null;
    }

    private Object toStringFn(Value... args) {
        return map.toString();
    }

    private Object getFn(Value... args) {
        if (args.length == 0) {
            return null;
        }
        Object key = context.jsToJava(args[0], Object.class);
        Object value = map.get(key);
        // 也尝试 String key 查找（JS 侧常用字符串 key）
        if (value == null && !(key instanceof String)) {
            value = map.get(String.valueOf(key));
        }
        return value == null ? null : context.javaToJs(value);
    }

    private Object setFn(Value... args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("set() requires key and value arguments");
        }
        Object key = context.jsToJava(args[0], Object.class);
        Object value = context.jsToJava(args[1], Object.class);
        map.put(key, value);
        return this; // 返回 Map 自身，符合 JS Map.set() 语义
    }

    private Object hasOwnPropertyFn(Value... args) {
        if (args.length == 0) {
            return false;
        }
        String key = args[0].isString() ? args[0].asString() : String.valueOf(context.jsToJava(args[0], Object.class));
        return map.containsKey(key);
    }
}
