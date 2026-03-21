package cn.qihuang02.graaljs.core;

/**
 * 数字类型转换工具。
 */
public final class NumberConverter {

    private NumberConverter() {}

    /**
     * 将 Number 转换为指定的数字类型。
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Class<T> target, Object normalized) {
        if (!(normalized instanceof Number number)) {
            throw new IllegalArgumentException("Cannot convert non-number to " + target.getName());
        }

        Object result;
        if (target == Integer.class || target == int.class) {
            result = number.intValue();
        } else if (target == Long.class || target == long.class) {
            result = number.longValue();
        } else if (target == Double.class || target == double.class) {
            result = number.doubleValue();
        } else if (target == Float.class || target == float.class) {
            result = number.floatValue();
        } else if (target == Short.class || target == short.class) {
            result = number.shortValue();
        } else if (target == Byte.class || target == byte.class) {
            result = number.byteValue();
        } else {
            result = number;
        }
        if (target.isPrimitive()) {
            return (T) result;
        }
        return target.cast(result);
    }

    /**
     * 将基本类型装箱。
     */
    public static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    /**
     * 返回基本类型的默认值。
     */
    public static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
