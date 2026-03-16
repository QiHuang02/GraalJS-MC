package cn.qihuang02.graaljs.binding;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class BindingsBuilder {
    private final Map<String, Object> bindings = new LinkedHashMap<>();

    public BindingsBuilder add(String name, Object value) {
        bindings.put(name, value);
        return this;
    }

    public BindingsBuilder addClass(String name, Class<?> type) {
        bindings.put(name, type);
        return this;
    }

    public BindingsBuilder addAll(Map<String, ?> values) {
        bindings.putAll(values);
        return this;
    }

    public BindingsBuilder addFunction(String name, DynamicFunction.Callback callback) {
        bindings.put(name, new DynamicFunction(callback));
        return this;
    }

    public BindingsBuilder addTypedFunction(String name, CustomFunction.Func callback, Class<?>... argTypes) {
        bindings.put(name, new CustomFunction(name, callback, Arrays.copyOf(argTypes, argTypes.length)));
        return this;
    }

    public Map<String, Object> build() {
        return Map.copyOf(bindings);
    }
}
