package cn.qihuang02.graaljs.typewrap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class TypeWrappers {
    private final Map<Class<?>, TypeWrapper<?>> wrappers = new IdentityHashMap<>();

    public <T> void register(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory) {
        if (target == null || target == Object.class) {
            throw new IllegalArgumentException("target 不能为空且不能是 Object.class");
        }
        if (wrappers.containsKey(target)) {
            throw new IllegalArgumentException("目标类型已注册包装器: " + target.getName());
        }
        wrappers.put(target, new TypeWrapper<>(target, validator, factory));
    }

    public <T> void register(Class<T> target, TypeWrapperFactory<T> factory) {
        register(target, TypeWrapperValidator.ALWAYS, factory);
    }

    public <T> void registerDirect(Class<T> target, DirectTypeWrapperFactory<T> factory) {
        register(target, TypeWrapperValidator.ALWAYS, factory);
    }

    @SuppressWarnings("unchecked")
    public <T> TypeWrapperFactory<T> getWrapperFactory(Class<T> target, Object from) {
        var wrapper = (TypeWrapper<T>) wrappers.get(target);
        if (wrapper != null && wrapper.validator().test(from)) {
            return wrapper.factory();
        }
        return null;
    }

    public boolean contains(Class<?> target) {
        return wrappers.containsKey(target);
    }

    public void forEach(BiConsumer<Class<?>, TypeWrapper<?>> action) {
        wrappers.forEach(action);
    }

    public boolean isEmpty() {
        return wrappers.isEmpty();
    }

    public int size() {
        return wrappers.size();
    }
}
