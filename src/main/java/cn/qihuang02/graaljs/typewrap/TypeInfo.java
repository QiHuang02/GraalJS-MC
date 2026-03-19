package cn.qihuang02.graaljs.typewrap;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.Collectors;

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
                TypeInfo.FunctionalInterfaceTypeInfo {

    /** 原始 Class 对象 */
    Class<?> rawType();

    /** 判断给定对象是否可赋值给此类型 */
    boolean isAssignableFrom(Object value);

    /** 用于错误信息的可读类型描述 */
    String describe();

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
        if (isFunctionalInterface(clazz)) {
            return new FunctionalInterfaceTypeInfo(clazz);
        }
        return new ClassTypeInfo(clazz);
    }

    private static boolean isFunctionalInterface(Class<?> clazz) {
        if (!clazz.isInterface()) return false;
        if (clazz.isAnnotationPresent(FunctionalInterface.class)) return true;
        // 检查是否只有一个抽象方法
        long abstractCount = Arrays.stream(clazz.getMethods())
                .filter(m -> java.lang.reflect.Modifier.isAbstract(m.getModifiers()))
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
            java.lang.reflect.RecordComponent[] components = rawType.getRecordComponents();
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
