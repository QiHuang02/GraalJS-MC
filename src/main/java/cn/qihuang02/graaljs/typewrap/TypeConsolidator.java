package cn.qihuang02.graaljs.typewrap;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

/**
 * 泛型类型变量解析器。
 * 递归展平继承链的类型变量映射，将 TypeVariable 解析为具体类型。
 *
 * <pre>{@code
 * // 给定 class StringList extends ArrayList<String>
 * TypeConsolidator c = TypeConsolidator.of(StringList.class);
 * // c.resolve(ArrayList 的 E) → String.class
 * }</pre>
 */
public final class TypeConsolidator {
    private final Map<TypeVariable<?>, Type> typeVariableMap;

    private TypeConsolidator(Map<TypeVariable<?>, Type> typeVariableMap) {
        this.typeVariableMap = typeVariableMap;
    }

    /**
     * 从具体类构建 TypeConsolidator，递归收集所有继承链上的类型变量映射。
     */
    public static TypeConsolidator of(Class<?> clazz) {
        Map<TypeVariable<?>, Type> map = new HashMap<>();
        collectTypeVariables(clazz, map);
        return new TypeConsolidator(map);
    }

    /**
     * 解析类型变量为具体类型。如果无法解析，返回原始类型。
     */
    public Type resolve(Type type) {
        if (type instanceof TypeVariable<?> tv) {
            Type resolved = typeVariableMap.get(tv);
            if (resolved != null && resolved != tv) {
                return resolve(resolved);
            }
            // 回退到上界
            Type[] bounds = tv.getBounds();
            if (bounds.length > 0 && bounds[0] != Object.class) {
                return resolve(bounds[0]);
            }
            return Object.class;
        }
        if (type instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            Type[] resolved = new Type[args.length];
            boolean changed = false;
            for (int i = 0; i < args.length; i++) {
                resolved[i] = resolve(args[i]);
                if (resolved[i] != args[i]) {
                    changed = true;
                }
            }
            if (!changed) {
                return pt;
            }
            return new ResolvedParameterizedType(pt.getOwnerType(), (Class<?>) pt.getRawType(), resolved);
        }
        return type;
    }

    /**
     * 解析类型变量为原始 Class。如果无法解析，返回 Object.class。
     */
    public Class<?> resolveRaw(Type type) {
        Type resolved = resolve(type);
        if (resolved instanceof Class<?> clazz) {
            return clazz;
        }
        if (resolved instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        return Object.class;
    }

    /**
     * 获取所有已解析的类型变量映射。
     */
    public Map<TypeVariable<?>, Type> getTypeVariableMap() {
        return Map.copyOf(typeVariableMap);
    }

    private static void collectTypeVariables(Class<?> clazz, Map<TypeVariable<?>, Type> map) {
        // 处理父类
        Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType pt) {
            Class<?> rawSuper = (Class<?>) pt.getRawType();
            TypeVariable<?>[] typeParams = rawSuper.getTypeParameters();
            Type[] actualArgs = pt.getActualTypeArguments();
            for (int i = 0; i < typeParams.length && i < actualArgs.length; i++) {
                map.put(typeParams[i], resolveInMap(actualArgs[i], map));
            }
            collectTypeVariables(rawSuper, map);
        } else if (genericSuperclass instanceof Class<?> superClass && superClass != Object.class) {
            collectTypeVariables(superClass, map);
        }

        // 处理接口
        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType pt) {
                Class<?> rawIface = (Class<?>) pt.getRawType();
                TypeVariable<?>[] typeParams = rawIface.getTypeParameters();
                Type[] actualArgs = pt.getActualTypeArguments();
                for (int i = 0; i < typeParams.length && i < actualArgs.length; i++) {
                    map.put(typeParams[i], resolveInMap(actualArgs[i], map));
                }
                collectTypeVariables(rawIface, map);
            } else if (genericInterface instanceof Class<?> ifaceClass) {
                collectTypeVariables(ifaceClass, map);
            }
        }
    }

    private static Type resolveInMap(Type type, Map<TypeVariable<?>, Type> map) {
        if (type instanceof TypeVariable<?> tv) {
            Type resolved = map.get(tv);
            return resolved != null ? resolved : type;
        }
        return type;
    }

    /**
     * 解析后的 ParameterizedType 实现。
     */
    private record ResolvedParameterizedType(Type ownerType, Class<?> rawType, Type[] actualTypeArguments)
            implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(rawType.getName());
            if (actualTypeArguments.length > 0) {
                sb.append('<');
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(actualTypeArguments[i].getTypeName());
                }
                sb.append('>');
            }
            return sb.toString();
        }
    }
}
