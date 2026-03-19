package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.core.GraaljsContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JS 对象与 Gson {@link JsonElement} 之间的双向转换桥接。
 * 在 JS 全局作用域中注册为 {@code GSON} 对象。
 */
public class GsonBridge implements ProxyObject {
    private static final Set<String> MEMBER_KEYS = Set.of("toJson", "fromJson", "parse");

    private final GraaljsContext context;

    public GsonBridge(GraaljsContext context) {
        this.context = context;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "toJson" -> (ProxyExecutable) this::toJsonFn;
            case "fromJson" -> (ProxyExecutable) this::fromJsonFn;
            case "parse" -> (ProxyExecutable) this::parseFn;
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

    // ── JS 方法 ──

    /**
     * toJson(jsValue) → JsonElement
     * 将 JS 值转换为 Gson JsonElement。
     */
    private Object toJsonFn(Value... args) {
        if (args.length == 0) {
            return JsonNull.INSTANCE;
        }
        Object normalized = context.jsToJava(args[0], Object.class);
        return javaToJsonElement(normalized);
    }

    /**
     * fromJson(jsonElement) → JS 值
     * 将 Gson JsonElement 转换为 JS 值。
     */
    private Object fromJsonFn(Value... args) {
        if (args.length == 0) {
            return null;
        }
        Object arg = context.jsToJava(args[0], Object.class);
        if (arg instanceof JsonElement element) {
            return context.javaToJs(jsonElementToJava(element));
        }
        if (arg instanceof String str) {
            return context.javaToJs(jsonElementToJava(JsonParser.parseString(str)));
        }
        return null;
    }

    /**
     * parse(jsonString) → JS 值
     * 解析 JSON 字符串为 JS 值。
     */
    private Object parseFn(Value... args) {
        if (args.length == 0 || !args[0].isString()) {
            throw new IllegalArgumentException("GSON.parse() requires a string argument");
        }
        JsonElement element = JsonParser.parseString(args[0].asString());
        return context.javaToJs(jsonElementToJava(element));
    }

    // ── 转换方法（静态，可供外部使用）──

    /**
     * Java 对象 → JsonElement。
     */
    public static JsonElement javaToJsonElement(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof JsonElement element) {
            return element;
        }
        if (value instanceof String str) {
            return new JsonPrimitive(str);
        }
        if (value instanceof Number num) {
            return new JsonPrimitive(num);
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Character ch) {
            return new JsonPrimitive(ch);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                obj.add(String.valueOf(entry.getKey()), javaToJsonElement(entry.getValue()));
            }
            return obj;
        }
        if (value instanceof List<?> list) {
            JsonArray arr = new JsonArray(list.size());
            for (Object element : list) {
                arr.add(javaToJsonElement(element));
            }
            return arr;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray arr = new JsonArray();
            for (Object element : iterable) {
                arr.add(javaToJsonElement(element));
            }
            return arr;
        }
        // 回退：转为字符串
        return new JsonPrimitive(String.valueOf(value));
    }

    /**
     * JsonElement → Java 对象（Map/List/String/Number/Boolean/null）。
     */
    public static Object jsonElementToJava(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            }
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                Number num = primitive.getAsNumber();
                // 尝试保持整数精度
                double d = num.doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    long l = num.longValue();
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        return (int) l;
                    }
                    return l;
                }
                return d;
            }
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                map.put(entry.getKey(), jsonElementToJava(entry.getValue()));
            }
            return map;
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            java.util.ArrayList<Object> list = new java.util.ArrayList<>(arr.size());
            for (JsonElement item : arr) {
                list.add(jsonElementToJava(item));
            }
            return list;
        }
        return null;
    }
}
