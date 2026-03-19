package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyIterable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 为 List 提供原生数组式访问，同时暴露 JS 风格的数组方法。
 */
public class JavaListProxy implements ProxyArray, ProxyObject, ProxyIterable, ProxyValue {
    private static final Set<String> MEMBER_KEYS = Set.of(
            "length", "push", "pop", "splice",
            "forEach", "map", "filter", "find",
            "includes", "indexOf", "join", "slice",
            "toString",
            "reduce", "every", "some", "findIndex",
            "sort", "reverse", "concat", "flat",
            "fill", "entries", "keys", "values",
            // Rhino 兼容方法
            "shift", "unshift", "reduceRight", "findLast", "findLastIndex"
    );

    private final GraaljsContext context;
    private final List<Object> list;

    @SuppressWarnings("unchecked")
    public JavaListProxy(GraaljsContext context, List<?> list) {
        this.context = context;
        this.list = (List<Object>) list;
    }

    // ── ProxyIterable ──

    @Override
    public Object getIterator() {
        return new JavaIteratorProxy(context, list.iterator());
    }

    // ── ProxyArray ──

    @Override
    public Object get(long index) {
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return context.javaToJs(list.get((int) index));
    }

    @Override
    public void set(long index, Value value) {
        if (index < 0 || index > Integer.MAX_VALUE) {
            throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
        }
        int targetIndex = (int) index;
        Object converted = context.jsToJava(value, Object.class);
        while (list.size() < targetIndex) {
            list.add(null);
        }
        if (targetIndex == list.size()) {
            list.add(converted);
        } else {
            list.set(targetIndex, converted);
        }
    }

    @Override
    public boolean remove(long index) {
        if (index < 0 || index >= list.size()) {
            return false;
        }
        Object removed = list.remove((int) index);
        Deletable.deleteObject(removed);
        return true;
    }

    @Override
    public long getSize() {
        return list.size();
    }

    // ── ProxyObject ──

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "length" -> list.size();
            case "push" -> (ProxyExecutable) this::push;
            case "pop" -> (ProxyExecutable) this::pop;
            case "splice" -> (ProxyExecutable) this::splice;
            case "forEach" -> (ProxyExecutable) this::forEach;
            case "map" -> (ProxyExecutable) this::mapFn;
            case "filter" -> (ProxyExecutable) this::filterFn;
            case "find" -> (ProxyExecutable) this::findFn;
            case "includes" -> (ProxyExecutable) this::includes;
            case "indexOf" -> (ProxyExecutable) this::indexOf;
            case "join" -> (ProxyExecutable) this::join;
            case "slice" -> (ProxyExecutable) this::slice;
            case "toString" -> (ProxyExecutable) this::toStringFn;
            case "reduce" -> (ProxyExecutable) this::reduceFn;
            case "every" -> (ProxyExecutable) this::everyFn;
            case "some" -> (ProxyExecutable) this::someFn;
            case "findIndex" -> (ProxyExecutable) this::findIndexFn;
            case "sort" -> (ProxyExecutable) this::sortFn;
            case "reverse" -> (ProxyExecutable) this::reverseFn;
            case "concat" -> (ProxyExecutable) this::concatFn;
            case "flat" -> (ProxyExecutable) this::flatFn;
            case "fill" -> (ProxyExecutable) this::fillFn;
            case "entries" -> (ProxyExecutable) this::entriesFn;
            case "keys" -> (ProxyExecutable) this::keysFn;
            case "values" -> (ProxyExecutable) this::valuesFn;
            case "shift" -> (ProxyExecutable) this::shiftFn;
            case "unshift" -> (ProxyExecutable) this::unshiftFn;
            case "reduceRight" -> (ProxyExecutable) this::reduceRightFn;
            case "findLast" -> (ProxyExecutable) this::findLastFn;
            case "findLastIndex" -> (ProxyExecutable) this::findLastIndexFn;
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
        // 只读成员，忽略
    }

    // ── ProxyValue ──

    @Override
    public Object unwrap() {
        return list;
    }

    // ── JS 方法实现 ──

    private Object push(Value... args) {
        for (Value arg : args) {
            list.add(context.jsToJava(arg, Object.class));
        }
        return list.size();
    }

    private Object pop(Value... args) {
        if (list.isEmpty()) {
            return null;
        }
        Object removed = list.remove(list.size() - 1);
        Deletable.deleteObject(removed);
        return context.javaToJs(removed);
    }

    private Object splice(Value... args) {
        int start = args.length > 0 ? normalizeIndex(args[0].asInt()) : 0;
        int deleteCount = args.length > 1 ? args[1].asInt() : list.size() - start;
        deleteCount = Math.max(0, Math.min(deleteCount, list.size() - start));

        List<Object> removed = new ArrayList<>();
        for (int i = 0; i < deleteCount; i++) {
            Object element = list.remove(start);
            Deletable.deleteObject(element);
            removed.add(element);
        }

        for (int i = 2; i < args.length; i++) {
            list.add(start + (i - 2), context.jsToJava(args[i], Object.class));
        }

        return new JavaListProxy(context, removed);
    }

    private Object forEach(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        for (int i = 0; i < list.size(); i++) {
            callback.execute(context.javaToJs(list.get(i)), i, this);
        }
        return null;
    }

    private Object mapFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        List<Object> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Value mapped = callback.execute(context.javaToJs(list.get(i)), i, this);
            result.add(context.jsToJava(mapped, Object.class));
        }
        return new JavaListProxy(context, result);
    }

    private Object filterFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Value keep = callback.execute(context.javaToJs(list.get(i)), i, this);
            if (keep.asBoolean()) {
                result.add(list.get(i));
            }
        }
        return new JavaListProxy(context, result);
    }

    private Object findFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            Value result = callback.execute(context.javaToJs(element), i, this);
            if (result.asBoolean()) {
                return context.javaToJs(element);
            }
        }
        return null;
    }

    private Object includes(Value... args) {
        if (args.length == 0) {
            return false;
        }
        Object target = context.jsToJava(args[0], Object.class);
        return list.stream().anyMatch(element -> Objects.equals(element, target));
    }

    private Object indexOf(Value... args) {
        if (args.length == 0) {
            return -1;
        }
        Object target = context.jsToJava(args[0], Object.class);
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i), target)) {
                return i;
            }
        }
        return -1;
    }

    private Object join(Value... args) {
        String separator = args.length > 0 && args[0].isString() ? args[0].asString() : ",";
        StringJoiner joiner = new StringJoiner(separator);
        for (Object element : list) {
            joiner.add(element == null ? "null" : String.valueOf(element));
        }
        return joiner.toString();
    }

    private Object slice(Value... args) {
        int start = args.length > 0 ? normalizeIndex(args[0].asInt()) : 0;
        int end = args.length > 1 ? normalizeIndex(args[1].asInt()) : list.size();
        start = Math.max(0, Math.min(start, list.size()));
        end = Math.max(start, Math.min(end, list.size()));
        return new JavaListProxy(context, new ArrayList<>(list.subList(start, end)));
    }

    private Object toStringFn(Value... args) {
        return join();
    }

    // ── 新增 JS 方法 ──

    private Object reduceFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        int startIndex;
        Object accumulator;
        if (args.length > 1) {
            accumulator = context.jsToJava(args[1], Object.class);
            startIndex = 0;
        } else {
            if (list.isEmpty()) {
                throw new IllegalStateException("Reduce of empty array with no initial value");
            }
            accumulator = list.get(0);
            startIndex = 1;
        }
        for (int i = startIndex; i < list.size(); i++) {
            Value result = callback.execute(
                    context.javaToJs(accumulator),
                    context.javaToJs(list.get(i)),
                    i,
                    this
            );
            accumulator = context.jsToJava(result, Object.class);
        }
        return context.javaToJs(accumulator);
    }

    private Object everyFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        for (int i = 0; i < list.size(); i++) {
            Value result = callback.execute(context.javaToJs(list.get(i)), i, this);
            if (!result.asBoolean()) {
                return false;
            }
        }
        return true;
    }

    private Object someFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        for (int i = 0; i < list.size(); i++) {
            Value result = callback.execute(context.javaToJs(list.get(i)), i, this);
            if (result.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private Object findIndexFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        for (int i = 0; i < list.size(); i++) {
            Value result = callback.execute(context.javaToJs(list.get(i)), i, this);
            if (result.asBoolean()) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private Object sortFn(Value... args) {
        if (args.length > 0 && args[0].canExecute()) {
            Value comparator = args[0];
            list.sort((a, b) -> {
                Value result = comparator.execute(context.javaToJs(a), context.javaToJs(b));
                return result.asInt();
            });
        } else {
            list.sort((a, b) -> {
                String sa = a == null ? "null" : String.valueOf(a);
                String sb = b == null ? "null" : String.valueOf(b);
                return sa.compareTo(sb);
            });
        }
        return this;
    }

    private Object reverseFn(Value... args) {
        Collections.reverse(list);
        return this;
    }

    private Object concatFn(Value... args) {
        List<Object> result = new ArrayList<>(list);
        for (Value arg : args) {
            if (arg.hasArrayElements()) {
                long size = arg.getArraySize();
                for (long i = 0; i < size; i++) {
                    result.add(context.jsToJava(arg.getArrayElement(i), Object.class));
                }
            } else {
                result.add(context.jsToJava(arg, Object.class));
            }
        }
        return new JavaListProxy(context, result);
    }

    private Object flatFn(Value... args) {
        int depth = args.length > 0 ? args[0].asInt() : 1;
        List<Object> result = new ArrayList<>();
        flattenInto(result, list, depth);
        return new JavaListProxy(context, result);
    }

    private void flattenInto(List<Object> result, List<?> source, int depth) {
        for (Object element : source) {
            if (depth > 0 && element instanceof List<?> nested) {
                flattenInto(result, nested, depth - 1);
            } else {
                result.add(element);
            }
        }
    }

    private Object fillFn(Value... args) {
        if (args.length == 0) {
            return this;
        }
        Object value = context.jsToJava(args[0], Object.class);
        int start = args.length > 1 ? normalizeIndex(args[1].asInt()) : 0;
        int end = args.length > 2 ? normalizeIndex(args[2].asInt()) : list.size();
        start = Math.max(0, Math.min(start, list.size()));
        end = Math.max(start, Math.min(end, list.size()));
        for (int i = start; i < end; i++) {
            list.set(i, value);
        }
        return this;
    }

    private Object entriesFn(Value... args) {
        List<Object> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            entries.add(new JavaListProxy(context, new ArrayList<>(List.of(i, list.get(i)))));
        }
        return new JavaListProxy(context, entries);
    }

    private Object keysFn(Value... args) {
        List<Object> keys = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            keys.add(i);
        }
        return new JavaListProxy(context, keys);
    }

    private Object valuesFn(Value... args) {
        return new JavaListProxy(context, new ArrayList<>(list));
    }

    private Object shiftFn(Value... args) {
        if (list.isEmpty()) {
            return null;
        }
        Object removed = list.remove(0);
        Deletable.deleteObject(removed);
        return context.javaToJs(removed);
    }

    private Object unshiftFn(Value... args) {
        for (int i = args.length - 1; i >= 0; i--) {
            list.add(0, context.jsToJava(args[i], Object.class));
        }
        return list.size();
    }

    private Object reduceRightFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        int startIndex;
        Object accumulator;
        if (args.length > 1) {
            accumulator = context.jsToJava(args[1], Object.class);
            startIndex = list.size() - 1;
        } else {
            if (list.isEmpty()) {
                throw new IllegalStateException("Reduce of empty array with no initial value");
            }
            accumulator = list.get(list.size() - 1);
            startIndex = list.size() - 2;
        }
        for (int i = startIndex; i >= 0; i--) {
            Value result = callback.execute(
                    context.javaToJs(accumulator),
                    context.javaToJs(list.get(i)),
                    i,
                    this
            );
            accumulator = context.jsToJava(result, Object.class);
        }
        return context.javaToJs(accumulator);
    }

    private Object findLastFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        for (int i = list.size() - 1; i >= 0; i--) {
            Object element = list.get(i);
            Value result = callback.execute(context.javaToJs(element), i, this);
            if (result.asBoolean()) {
                return context.javaToJs(element);
            }
        }
        return null;
    }

    private Object findLastIndexFn(Value... args) {
        requireCallback(args);
        Value callback = args[0];
        for (int i = list.size() - 1; i >= 0; i--) {
            Value result = callback.execute(context.javaToJs(list.get(i)), i, this);
            if (result.asBoolean()) {
                return i;
            }
        }
        return -1;
    }

    // ── 工具方法 ──

    private int normalizeIndex(int index) {
        return index < 0 ? Math.max(0, list.size() + index) : index;
    }

    private static void requireCallback(Value[] args) {
        if (args.length == 0 || !args[0].canExecute()) {
            throw new IllegalArgumentException("First argument must be a function");
        }
    }
}
