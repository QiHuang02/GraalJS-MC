package cn.qihuang02.graaljs.util;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一的数组值提供者抽象，支持从多种来源创建数组/列表/集合。
 * 在类型转换中用于统一处理各种"类数组"输入。
 */
public sealed interface ArrayValueProvider permits
        ArrayValueProvider.FromList,
        ArrayValueProvider.FromArray,
        ArrayValueProvider.FromIterable,
        ArrayValueProvider.FromSingle,
        ArrayValueProvider.Empty {

    /** 元素数量 */
    int size();

    /** 获取第 i 个元素 */
    @Nullable Object get(int index);

    /** 转为 List */
    default List<Object> toList() {
        int n = size();
        List<Object> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(get(i));
        }
        return list;
    }

    /** 转为 Set */
    default Set<Object> toSet() {
        int n = size();
        Set<Object> set = new LinkedHashSet<>(n);
        for (int i = 0; i < n; i++) {
            set.add(get(i));
        }
        return set;
    }

    /** 转为 Java 数组 */
    default Object toArray(Class<?> componentType) {
        int n = size();
        Object array = Array.newInstance(componentType, n);
        for (int i = 0; i < n; i++) {
            Array.set(array, i, get(i));
        }
        return array;
    }

    // ── 工厂方法 ──

    static ArrayValueProvider of(@Nullable Object value) {
        if (value == null) {
            return new Empty();
        }
        if (value instanceof List<?> list) {
            return new FromList(list);
        }
        if (value.getClass().isArray()) {
            return new FromArray(value);
        }
        if (value instanceof Iterable<?> iterable) {
            return new FromIterable(iterable);
        }
        return new FromSingle(value);
    }

    // ── 实现 ──

    record FromList(List<?> list) implements ArrayValueProvider {
        @Override
        public int size() {
            return list.size();
        }

        @Override
        public Object get(int index) {
            return list.get(index);
        }

        @Override
        public List<Object> toList() {
            return new ArrayList<>(list);
        }
    }

    record FromArray(Object array) implements ArrayValueProvider {
        @Override
        public int size() {
            return Array.getLength(array);
        }

        @Override
        public Object get(int index) {
            return Array.get(array, index);
        }
    }

    record FromIterable(Iterable<?> iterable) implements ArrayValueProvider {
        @Override
        public int size() {
            if (iterable instanceof Collection<?> c) {
                return c.size();
            }
            int count = 0;
            for (Object ignored : iterable) {
                count++;
            }
            return count;
        }

        @Override
        public Object get(int index) {
            int i = 0;
            for (Object element : iterable) {
                if (i == index) {
                    return element;
                }
                i++;
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public List<Object> toList() {
            List<Object> list = new ArrayList<>();
            iterable.forEach(list::add);
            return list;
        }
    }

    record FromSingle(Object value) implements ArrayValueProvider {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public Object get(int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException(index);
            }
            return value;
        }

        @Override
        public List<Object> toList() {
            return new ArrayList<>(Collections.singletonList(value));
        }
    }

    record Empty() implements ArrayValueProvider {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public Object get(int index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public List<Object> toList() {
            return new ArrayList<>();
        }
    }
}
