package cn.qihuang02.graaljs.typewrap;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.lang.reflect.GenericArrayType;

/**
 * 轻量的泛型描述符，封装 java.lang.reflect.Type 的解析逻辑。
 * 内部委托给 {@link TypeInfo} 进行类型语义描述。
 */
public final class GenericTypeInfo {
    private final Class<?> rawType;
    private final Type[] typeArguments;
    private TypeInfo typeInfo; // 延迟构建

    private GenericTypeInfo(Class<?> rawType, Type[] typeArguments) {
        this.rawType = rawType;
        this.typeArguments = typeArguments;
    }

    /**
     * 从原始 Class 创建（无泛型参数）。
     */
    public static GenericTypeInfo of(Class<?> rawType) {
        return new GenericTypeInfo(rawType, new Type[0]);
    }

    /**
     * 从反射 Type 解析。
     */
    public static GenericTypeInfo of(Type type) {
        if (type instanceof Class<?> clazz) {
            return new GenericTypeInfo(clazz, new Type[0]);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> raw = (Class<?>) parameterizedType.getRawType();
            return new GenericTypeInfo(raw, parameterizedType.getActualTypeArguments());
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0 && upperBounds[0] != Object.class) {
                return of(upperBounds[0]);
            }
            return of(Object.class);
        }
        if (type instanceof GenericArrayType genericArrayType) {
            GenericTypeInfo componentInfo = of(genericArrayType.getGenericComponentType());
            Class<?> arrayClass = java.lang.reflect.Array.newInstance(componentInfo.rawType(), 0).getClass();
            return new GenericTypeInfo(arrayClass, new Type[0]);
        }
        // TypeVariable 等无法解析的情况，退回 Object
        return of(Object.class);
    }

    /**
     * 手动构造带泛型参数的描述符。
     */
    public static GenericTypeInfo of(Class<?> raw, Type... args) {
        return new GenericTypeInfo(raw, args == null ? new Type[0] : args);
    }

    public Class<?> rawType() {
        return rawType;
    }

    public Type[] typeArguments() {
        return typeArguments;
    }

    /**
     * 获取第 N 个泛型参数的 GenericTypeInfo。
     */
    public GenericTypeInfo typeArgument(int index) {
        if (index < 0 || index >= typeArguments.length) {
            return of(Object.class);
        }
        return of(typeArguments[index]);
    }

    public boolean hasTypeArguments() {
        return typeArguments.length > 0;
    }

    /**
     * 转换为 TypeInfo 语义描述符。
     */
    public TypeInfo toTypeInfo() {
        if (typeInfo == null) {
            if (typeArguments.length > 0) {
                TypeInfo[] argInfos = new TypeInfo[typeArguments.length];
                for (int i = 0; i < typeArguments.length; i++) {
                    argInfos[i] = TypeInfo.of(typeArguments[i]);
                }
                typeInfo = new TypeInfo.ParameterizedTypeInfo(rawType, argInfos);
            } else {
                typeInfo = TypeInfo.ofClass(rawType);
            }
        }
        return typeInfo;
    }

    /**
     * 用于错误信息的可读类型描述，委托给 TypeInfo。
     */
    public String describe() {
        return toTypeInfo().describe();
    }
}
