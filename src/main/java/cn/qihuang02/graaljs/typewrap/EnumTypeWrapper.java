package cn.qihuang02.graaljs.typewrap;

import cn.qihuang02.graaljs.core.GraaljsContext;

public class EnumTypeWrapper<T extends Enum<T>> implements TypeWrapperFactory<T> {
    private final Class<T> enumClass;
    private final T[] constants;

    public EnumTypeWrapper(Class<T> enumClass) {
        this.enumClass = enumClass;
        this.constants = enumClass.getEnumConstants();
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
        // 忽略大小写匹配
        for (T constant : constants) {
            if (constant.name().equalsIgnoreCase(name)) {
                return constant;
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
}
