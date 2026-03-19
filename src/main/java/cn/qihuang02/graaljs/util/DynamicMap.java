package cn.qihuang02.graaljs.util;

import java.util.HashMap;
import java.util.function.Function;

/**
 * 按需创建值的 HashMap，{@link #containsKey(Object)} 始终返回 true。
 * 用于实现懒加载的命名空间映射。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class DynamicMap<K, V> extends HashMap<K, V> {
    private final Function<K, V> valueFactory;

    public DynamicMap(Function<K, V> valueFactory) {
        this.valueFactory = valueFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        return computeIfAbsent((K) key, valueFactory);
    }

    @Override
    public boolean containsKey(Object key) {
        return true;
    }
}
