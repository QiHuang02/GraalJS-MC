package cn.qihuang02.graaljs.bridge;

import org.graalvm.polyglot.Value;

import java.util.Map;

/**
 * 重载分派评分工具。用于方法和构造器的参数匹配评分。
 * <p>
 * 分数越低越优先。
 */
public final class OverloadScoring {

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_BOXED = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            char.class, Character.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class
    );

    /**
     * 数值类型的"宽度"排序，用于判断提升方向。
     * 值越小表示越窄。
     */
    private static final Map<Class<?>, Integer> NUMERIC_WIDTH = Map.of(
            Byte.class, 1,
            Short.class, 2,
            Integer.class, 3,
            Long.class, 4,
            Float.class, 5,
            Double.class, 6
    );

    private OverloadScoring() {
    }

    /**
     * 计算单个参数的匹配评分。
     *
     * @param argument      JS 传入的 Value
     * @param parameterType 目标 Java 参数类型
     * @param converted     转换后的 Java 对象
     * @return 评分
     */
    public static int score(Value argument, Class<?> parameterType, Object converted) {
        if (converted == null) {
            return 10;
        }

        Class<?> boxedParam = box(parameterType);
        Class<?> convertedClass = converted.getClass();

        // 数值类型需要特殊处理：convertNumber 总是返回目标类型的精确值，
        // 所以不能用 convertedClass == boxedParam 来判断"精确匹配"。
        // 需要基于 JS Value 的自然精度来评分。
        if (argument.isNumber() && Number.class.isAssignableFrom(boxedParam)) {
            Class<?> naturalType = naturalNumericType(argument);
            if (naturalType == boxedParam) {
                return 0; // 自然精度精确匹配
            }
            Integer naturalWidth = NUMERIC_WIDTH.get(naturalType);
            Integer paramWidth = NUMERIC_WIDTH.get(boxedParam);
            if (naturalWidth != null && paramWidth != null) {
                if (paramWidth > naturalWidth) {
                    return 3; // 无损提升（窄→宽）
                }
                return 4; // 有损收窄（宽→窄）
            }
            return 4;
        }

        // 精确类型匹配（非数值）
        if (convertedClass == boxedParam) {
            return 0;
        }

        // 子类匹配
        if (parameterType.isInstance(converted)) {
            return 1;
        }

        // HostObject 经过转换
        if (argument.isHostObject()) {
            return 2;
        }

        // String/Boolean 转换
        if (argument.isString() || argument.isBoolean()) {
            return 5;
        }

        return 7;
    }

    /**
     * varargs 匹配的额外惩罚分。
     */
    public static int varargsPenalty() {
        return 1;
    }

    /**
     * 根据 JS Value 的精度确定其"自然"Java 数值类型。
     */
    private static Class<?> naturalNumericType(Value value) {
        if (value.fitsInInt()) {
            return Integer.class;
        }
        if (value.fitsInLong()) {
            return Long.class;
        }
        return Double.class;
    }

    private static Class<?> box(Class<?> type) {
        Class<?> boxed = PRIMITIVE_TO_BOXED.get(type);
        return boxed != null ? boxed : type;
    }
}
