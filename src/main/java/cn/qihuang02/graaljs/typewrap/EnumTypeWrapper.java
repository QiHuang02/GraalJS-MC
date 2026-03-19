package cn.qihuang02.graaljs.typewrap;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.bridge.RemappedEnumConstant;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class EnumTypeWrapper<T extends Enum<T>> implements TypeWrapperFactory<T> {
    private final Class<T> enumClass;
    private final T[] constants;
    /** 重映射名称 → 枚举常量 */
    private final Map<String, T> remappedNames;

    public EnumTypeWrapper(Class<T> enumClass) {
        this.enumClass = enumClass;
        this.constants = enumClass.getEnumConstants();
        this.remappedNames = buildRemappedNames();
    }

    @Override
    public T wrap(GraaljsContext cx, Object from, Class<?> target) {
        if (from instanceof CharSequence s) {
            return fromString(s.toString());
        }
        if (from instanceof Number n) {
            return fromOrdinal(n.intValue());
        }
        return null;
    }

    private T fromString(String name) {
        // 精确匹配
        for (T constant : constants) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        // 重映射名称精确匹配
        T remapped = remappedNames.get(name);
        if (remapped != null) {
            return remapped;
        }
        // 忽略大小写匹配
        for (T constant : constants) {
            if (constant.name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        // 重映射名称忽略大小写匹配
        for (Map.Entry<String, T> entry : remappedNames.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private T fromOrdinal(int ordinal) {
        if (ordinal >= 0 && ordinal < constants.length) {
            return constants[ordinal];
        }
        return null;
    }

    private Map<String, T> buildRemappedNames() {
        Map<String, T> map = new HashMap<>();
        for (T constant : constants) {
            // 优先检查接口形式（Rhino 兼容）
            if (constant instanceof RemappedEnumConstant remapped) {
                String name = remapped.getRemappedEnumConstantName();
                if (name != null && !name.isEmpty()) {
                    map.put(name, constant);
                    continue;
                }
            }
            // 其次检查注解形式
            try {
                Field field = enumClass.getField(constant.name());
                cn.qihuang02.graaljs.util.RemappedEnumConstant annotation =
                        field.getAnnotation(cn.qihuang02.graaljs.util.RemappedEnumConstant.class);
                if (annotation != null && !annotation.value().isEmpty()) {
                    map.put(annotation.value(), constant);
                }
            } catch (NoSuchFieldException ignored) {
                // 不应发生
            }
        }
        return map;
    }
}
