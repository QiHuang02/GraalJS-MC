package cn.qihuang02.graaljs.typewrap;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * 密封接口，统一描述 Java 类型在桥接层的语义。
 * 作为桥接元模型，提供类型判断、描述和工厂方法。
 */
public sealed interface TypeInfo
        permits TypeInfo.ClassTypeInfo,
                TypeInfo.ArrayTypeInfo,
                TypeInfo.ParameterizedTypeInfo,
                TypeInfo.EnumTypeInfo,
                TypeInfo.RecordTypeInfo,
                TypeInfo.FunctionalInterfaceTypeInfo,
                JSOrTypeInfo,
                JSObjectTypeInfo,
                JSFunctionTypeInfo,
                JSFixedArrayTypeInfo,
                JSBasicConstantTypeInfo,
                JSStringConstantTypeInfo,
                JSNumberConstantTypeInfo {

    /** 原始 Class 对象 */
    Class<?> rawType();

    /** 判断给定对象是否可赋值给此类型 */
    boolean isAssignableFrom(Object value);

    /** 用于错误信息的可读类型描述 */
    String describe();

    // ── 默认查询方法 ──

    /** 是否需要类型转换（非 Object 类型） */
    default boolean shouldConvert() {
        return rawType() != Object.class;
    }

    /** 是否为函数式接口 */
    default boolean isFunctionalInterface() {
        return this instanceof FunctionalInterfaceTypeInfo;
    }

    /** 获取 Record 组件信息，非 Record 类型返回空数组 */
    default RecordComponent[] recordComponents() {
        if (this instanceof RecordTypeInfo) {
            RecordComponent[] components = rawType().getRecordComponents();
            return components != null ? components : new RecordComponent[0];
        }
        return new RecordComponent[0];
    }

    /** 获取枚举常量，非枚举类型返回空数组 */
    default Object[] enumConstants() {
        if (this instanceof EnumTypeInfo) {
            Object[] constants = rawType().getEnumConstants();
            return constants != null ? constants : new Object[0];
        }
        return new Object[0];
    }

    /** 使用 TypeConsolidator 解析泛型类型变量 */
    default TypeInfo consolidate(Class<?> contextClass) {
        // 基础实现不做额外处理，ParameterizedTypeInfo 可覆盖
        return this;
    }

    /** 创建此类型的数组 */
    default Object newArray(int length) {
        return java.lang.reflect.Array.newInstance(rawType(), length);
    }

    // ── JS 类型构建方法 ──

    /**
     * 构建联合类型 {@code this | other}。
     * 如果任一侧已经是 JSOrTypeInfo，会自动展平。
     */
    default TypeInfo or(TypeInfo other) {
        return JSOrTypeInfo.of(this, other);
    }

    /**
     * 将此类型追加到 StringBuilder，使用给定的上下文控制格式。
     * Java 侧子类型默认使用 {@link #describe()}，JS* 子类型各自覆盖。
     */
    default void append(TypeStringContext ctx, StringBuilder sb) {
        if (rawType() != Object.class) {
            sb.append(ctx.getTypeName(rawType()));
        } else {
            sb.append(describe());
        }
    }

    /**
     * 使用默认上下文将类型序列化为字符串。
     */
    default String toString(TypeStringContext ctx) {
        StringBuilder sb = new StringBuilder();
        append(ctx, sb);
        return sb.toString();
    }

    // ── 工厂方法 ──

    /**
     * 从 {@link java.lang.reflect.Type} 构建 TypeInfo。
     */
    static TypeInfo of(Type type) {
        if (type instanceof Class<?> clazz) {
            return ofClass(clazz);
        }
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            Type[] args = pt.getActualTypeArguments();
            TypeInfo[] argInfos = new TypeInfo[args.length];
            for (int i = 0; i < args.length; i++) {
                argInfos[i] = of(args[i]);
            }
            return new ParameterizedTypeInfo(raw, argInfos);
        }
        if (type instanceof GenericArrayType gat) {
            TypeInfo component = of(gat.getGenericComponentType());
            Class<?> arrayClass = java.lang.reflect.Array.newInstance(component.rawType(), 0).getClass();
            return new ArrayTypeInfo(arrayClass, component);
        }
        if (type instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length > 0 && upper[0] != Object.class) {
                return of(upper[0]);
            }
            return ofClass(Object.class);
        }
        // TypeVariable 等无法解析的情况
        return ofClass(Object.class);
    }

    /**
     * 从 Class 构建 TypeInfo，自动识别枚举、Record、函数式接口、数组。
     */
    static TypeInfo ofClass(Class<?> clazz) {
        if (clazz.isArray()) {
            TypeInfo component = ofClass(clazz.getComponentType());
            return new ArrayTypeInfo(clazz, component);
        }
        if (clazz.isEnum()) {
            return new EnumTypeInfo(clazz);
        }
        if (clazz.isRecord()) {
            return new RecordTypeInfo(clazz);
        }
        if (checkFunctionalInterface(clazz)) {
            return new FunctionalInterfaceTypeInfo(clazz);
        }
        return new ClassTypeInfo(clazz);
    }

    /**
     * 从 Type 构建 TypeInfo，使用 TypeConsolidator 解析类型变量。
     */
    static TypeInfo of(Type type, TypeConsolidator consolidator) {
        Type resolved = consolidator.resolve(type);
        return of(resolved);
    }

    private static boolean checkFunctionalInterface(Class<?> clazz) {
        if (!clazz.isInterface()) return false;
        if (clazz.isAnnotationPresent(FunctionalInterface.class)) return true;
        long abstractCount = Arrays.stream(clazz.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .count();
        return abstractCount == 1;
    }

    // ── 子类型 ──

    /** 普通类/接口 */
    record ClassTypeInfo(Class<?> rawType) implements TypeInfo {
        @Override
        public boolean isAssignableFrom(Object value) {
            return value != null && rawType.isInstance(value);
        }

        @Override
        public String describe() {
            return rawType.getSimpleName();
        }
    }

    /** 数组类型 */
    record ArrayTypeInfo(Class<?> rawType, TypeInfo componentType) implements TypeInfo {
        @Override
        public boolean isAssignableFrom(Object value) {
            return value != null && rawType.isInstance(value);
        }

        @Override
        public String describe() {
            return componentType.describe() + "[]";
        }
    }

    /** 参数化泛型类型 */
    record ParameterizedTypeInfo(Class<?> rawType, TypeInfo[] typeArguments) implements TypeInfo {
        @Override
        public boolean isAssignableFrom(Object value) {
            // 运行时泛型擦除，只能检查原始类型
            return value != null && rawType.isInstance(value);
        }

        @Override
        public String describe() {
            if (typeArguments.length == 0) {
                return rawType.getSimpleName();
            }
            StringJoiner sj = new StringJoiner(", ", "<", ">");
            for (TypeInfo arg : typeArguments) {
                sj.add(arg.describe());
            }
            return rawType.getSimpleName() + sj;
        }

        /** 获取第 N 个类型参数 */
        public TypeInfo typeArgument(int index) {
            if (index < 0 || index >= typeArguments.length) {
                return new ClassTypeInfo(Object.class);
            }
            return typeArguments[index];
        }
    }

    /** 枚举类型 */
    record EnumTypeInfo(Class<?> rawType) implements TypeInfo {
        @Override
        public boolean isAssignableFrom(Object value) {
            return value != null && rawType.isInstance(value);
        }

        @Override
        public String describe() {
            return "enum " + rawType.getSimpleName();
        }

        /** 获取所有枚举常量名 */
        public String[] constantNames() {
            Object[] constants = rawType.getEnumConstants();
            if (constants == null) return new String[0];
            String[] names = new String[constants.length];
            for (int i = 0; i < constants.length; i++) {
                names[i] = ((Enum<?>) constants[i]).name();
            }
            return names;
        }
    }

    /** Record 类型 */
    record RecordTypeInfo(Class<?> rawType) implements TypeInfo {
        @Override
        public boolean isAssignableFrom(Object value) {
            return value != null && rawType.isInstance(value);
        }

        @Override
        public String describe() {
            return "record " + rawType.getSimpleName();
        }

        /** 获取 Record 组件名 */
        public String[] componentNames() {
            RecordComponent[] components = rawType.getRecordComponents();
            if (components == null) return new String[0];
            String[] names = new String[components.length];
            for (int i = 0; i < components.length; i++) {
                names[i] = components[i].getName();
            }
            return names;
        }
    }

    /** 函数式接口类型 */
    record FunctionalInterfaceTypeInfo(Class<?> rawType) implements TypeInfo {
        @Override
        public boolean isAssignableFrom(Object value) {
            return value != null && rawType.isInstance(value);
        }

        @Override
        public String describe() {
            return "functional " + rawType.getSimpleName();
        }
    }
}
