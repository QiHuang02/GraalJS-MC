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

        // char 特殊处理：JS 长度为 1 的字符串对 char 参数给予较低评分
        if (boxedParam == Character.class) {
            if (argument.isString()) {
                String str = argument.asString();
                return str.length() == 1 ? 1 : 9; // 单字符次优匹配，多字符高惩罚
            }
            // 数值转 char 也可以，但评分较高
            if (argument.isNumber()) {
                return 6;
            }
            return 9;
        }

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
                    // 无损提升（窄→宽）：宽度差越小越优先
                    // 评分范围 1~5，始终低于非数值子类匹配(7)
                    return paramWidth - naturalWidth;
                }
                // 有损收窄（宽→窄）：始终高惩罚
                return 10 + (naturalWidth - paramWidth);
            }
            return 8;
        }

        // 数值参数匹配 Object/Number 等泛型参数时，给予较高评分
        // 确保具体数值类型（int/long/double）优先于 Object
        if (argument.isNumber() && parameterType.isInstance(converted)) {
            return 7;
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
     * 计算 Java 对象参数的匹配评分（用于 AbstractClassAdapter 等场景，参数已是 Java 对象）。
     *
     * @param source        原始 Java 参数
     * @param parameterType 目标参数类型
     * @param converted     转换后的 Java 对象
     * @return 评分
     */
    public static int scoreJavaArg(Object source, Class<?> parameterType, Object converted) {
        if (converted == null) {
            return 10;
        }

        Class<?> boxedParam = box(parameterType);
        Class<?> convertedClass = converted.getClass();

        // 精确类型匹配
        if (convertedClass == boxedParam) {
            return 0;
        }

        // 子类匹配
        if (parameterType.isInstance(converted)) {
            return 1;
        }

        // 数值类型提升/收窄
        if (source instanceof Number && Number.class.isAssignableFrom(boxedParam)) {
            Class<?> sourceBoxed = box(source.getClass());
            Integer sourceWidth = NUMERIC_WIDTH.get(sourceBoxed);
            Integer paramWidth = NUMERIC_WIDTH.get(boxedParam);
            if (sourceWidth != null && paramWidth != null) {
                if (paramWidth.equals(sourceWidth)) {
                    return 0;
                }
                if (paramWidth > sourceWidth) {
                    return paramWidth - sourceWidth;
                }
                return 10 + (sourceWidth - paramWidth);
            }
            return 8;
        }

        // String/Boolean 转换
        if (source instanceof String || source instanceof Boolean) {
            return 5;
        }

        return 7;
    }

    /**
     * 根据 JS Value 的精度确定其"自然"Java 数值类型。
     * <p>
     * GraalJS Value 没有 fitsInByte/fitsInShort/fitsInFloat，
     * 通过 fitsInInt + 范围检查实现细粒度判断。
     */
    static Class<?> naturalNumericType(Value value) {
        if (value.fitsInInt()) {
            int intVal = value.asInt();
            if (intVal >= Byte.MIN_VALUE && intVal <= Byte.MAX_VALUE) {
                return Byte.class;
            }
            if (intVal >= Short.MIN_VALUE && intVal <= Short.MAX_VALUE) {
                return Short.class;
            }
            return Integer.class;
        }
        if (value.fitsInLong()) {
            return Long.class;
        }
        // 检查是否可以无损表示为 float
        double doubleVal = value.asDouble();
        if (doubleVal >= -Float.MAX_VALUE && doubleVal <= Float.MAX_VALUE
                && Double.compare(doubleVal, (double) (float) doubleVal) == 0) {
            return Float.class;
        }
        return Double.class;
    }

    private static Class<?> box(Class<?> type) {
        Class<?> boxed = PRIMITIVE_TO_BOXED.get(type);
        return boxed != null ? boxed : type;
    }
}
