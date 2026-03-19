package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.typewrap.GenericTypeInfo;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import cn.qihuang02.graaljs.util.ReturnsSelf;
import cn.qihuang02.graaljs.util.ReturnsSelfContainer;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方法代理，支持简单重载与可变参数分派。
 */
public class JavaMethodProxy implements ProxyExecutable {
    private final GraaljsContext context;
    private final Object target;
    private final List<Method> methods;
    /**
     * 重载解析缓存：参数类型签名 → 最佳匹配的 Method。
     * 仅当方法列表有多个重载时启用缓存。
     */
    private final Map<ArgSignature, Method> resolveCache;
    /** 拥有此方法代理的 ProxyObject，用于 @ReturnsSelf 优化 */
    private Object ownerProxy;

    public JavaMethodProxy(GraaljsContext context, Object target, List<Method> methods) {
        this.context = context;
        this.target = target;
        this.methods = methods;
        this.resolveCache = methods.size() > 1 ? new ConcurrentHashMap<>() : null;
    }

    /**
     * 设置拥有此方法代理的 ProxyObject，用于 @ReturnsSelf 优化。
     */
    public void setOwnerProxy(Object ownerProxy) {
        this.ownerProxy = ownerProxy;
    }

    @Override
    public Object execute(Value... arguments) {
        MethodMatch match = resolve(arguments);
        if (match == null) {
            throw new IllegalArgumentException("No matching overload for method call: " + methods);
        }

        try {
            Object result = match.method().invoke(target, match.arguments());
            // @ReturnsSelf 优化：如果方法标注了 @ReturnsSelf 且返回值就是 target，
            // 直接返回原始代理对象避免重新包装
            if (ownerProxy != null && result == target && isReturnsSelf(match.method())) {
                return ownerProxy;
            }
            return context.javaToJs(result);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            Throwable cause = exception instanceof InvocationTargetException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            if (cause != exception && !context.getFactory().visibleToScripts(cause.getClass().getName(), ClassVisibilityContext.EXCEPTION)) {
                throw new IllegalStateException("Host exception is not visible to scripts", exception);
            }
            throw new IllegalStateException("Failed to invoke Java method: " + match.method(), cause);
        }
    }

    private MethodMatch resolve(Value[] arguments) {
        // 如果有缓存，先查缓存中的 Method，直接用它做 tryMatch
        if (resolveCache != null) {
            ArgSignature sig = ArgSignature.of(arguments);
            Method cached = resolveCache.get(sig);
            if (cached != null) {
                MethodMatch match = tryMatch(cached, arguments);
                if (match != null) {
                    return match;
                }
                // 缓存失效（极少见），移除后走全量解析
                resolveCache.remove(sig);
            }
            MethodMatch result = resolveAll(arguments);
            if (result != null) {
                resolveCache.put(sig, result.method());
            }
            return result;
        }
        return resolveAll(arguments);
    }

    private MethodMatch resolveAll(Value[] arguments) {
        MethodMatch bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        int bestCount = 0;

        for (Method method : methods) {
            MethodMatch match = tryMatch(method, arguments);
            if (match != null) {
                if (match.score() < bestScore) {
                    bestMatch = match;
                    bestScore = match.score();
                    bestCount = 1;
                } else if (match.score() == bestScore) {
                    bestCount++;
                }
            }
        }

        if (bestCount > 1) {
            throw new AmbiguousOverloadException(
                    "Ambiguous overload: " + bestCount + " methods match with score " + bestScore
                            + " for " + methods.get(0).getName() + " with " + arguments.length + " argument(s)");
        }

        return bestMatch;
    }

    private MethodMatch tryMatch(Method method, Value[] arguments) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        boolean varArgs = method.isVarArgs();

        if ((!varArgs && parameterTypes.length != arguments.length)
                || (varArgs && arguments.length < parameterTypes.length - 1)) {
            return null;
        }

        Object[] invocationArguments = new Object[parameterTypes.length];
        int score = 0;

        for (int i = 0; i < parameterTypes.length; i++) {
            if (varArgs && i == parameterTypes.length - 1) {
                Class<?> componentType = parameterTypes[i].getComponentType();
                int size = arguments.length - i;
                Object array = Array.newInstance(componentType, size);
                for (int j = 0; j < size; j++) {
                    Object converted = convertArgument(arguments[i + j], componentType);
                    if (converted == null && componentType.isPrimitive()) {
                        return null;
                    }
                    Array.set(array, j, converted);
                    score += conversionScore(arguments[i + j], componentType, converted);
                }
                invocationArguments[i] = array;
                return new MethodMatch(method, invocationArguments, score + OverloadScoring.varargsPenalty());
            }

            Object converted = convertArgument(arguments[i], genericParameterTypes[i]);
            if (converted == null && parameterTypes[i].isPrimitive()) {
                return null;
            }
            invocationArguments[i] = converted;
            score += conversionScore(arguments[i], parameterTypes[i], converted);
        }

        return new MethodMatch(method, invocationArguments, score);
    }

    private Object convertArgument(Value argument, Type parameterType) {
        GenericTypeInfo info = GenericTypeInfo.of(parameterType);
        if (info.rawType() == Value.class) {
            return argument;
        }
        try {
            return context.jsToJava(argument, info);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private int conversionScore(Value argument, Class<?> parameterType, Object converted) {
        return OverloadScoring.score(argument, parameterType, converted);
    }

    private record MethodMatch(Method method, Object[] arguments, int score) {
        @Override
        public String toString() {
            return method + " " + Arrays.toString(arguments);
        }
    }

    /**
     * 判断方法是否为 returnsSelf（方法级别或类级别 @ReturnsSelf）。
     * 方法级别：直接标注 @ReturnsSelf 且 copy=false。
     * 类级别：声明类标注 @ReturnsSelf，且方法返回类型匹配 value() 指定的类型，且 copy=false。
     */
    private static boolean isReturnsSelf(Method method) {
        // 方法级别检查
        ReturnsSelf methodAnnotation = method.getAnnotation(ReturnsSelf.class);
        if (methodAnnotation != null) {
            return !methodAnnotation.copy();
        }
        // 类级别检查
        Class<?> declaringClass = method.getDeclaringClass();
        ReturnsSelf[] classAnnotations = declaringClass.getAnnotationsByType(ReturnsSelf.class);
        if (classAnnotations.length == 0) {
            return false;
        }
        Class<?> returnType = method.getReturnType();
        for (ReturnsSelf annotation : classAnnotations) {
            if (annotation.copy()) {
                continue;
            }
            Class<?> matchType = annotation.value();
            // Object.class 表示使用声明类本身
            if (matchType == Object.class) {
                matchType = declaringClass;
            }
            if (matchType.isAssignableFrom(returnType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 参数类型签名，用于重载解析缓存的 key。
     * 基于每个参数的 JS 类型特征（而非具体值）。
     */
    static final class ArgSignature {
        private static final byte TYPE_NULL = 0;
        private static final byte TYPE_BOOLEAN = 1;
        private static final byte TYPE_INT = 2;
        private static final byte TYPE_LONG = 3;
        private static final byte TYPE_DOUBLE = 4;
        private static final byte TYPE_STRING = 5;
        private static final byte TYPE_HOST = 6;
        private static final byte TYPE_ARRAY = 7;
        private static final byte TYPE_OBJECT = 8;
        private static final byte TYPE_EXECUTABLE = 9;

        private final byte[] types;
        private final Class<?>[] hostClasses; // 仅 host object 参数记录具体类
        private final int hashCode;

        private ArgSignature(byte[] types, Class<?>[] hostClasses) {
            this.types = types;
            this.hostClasses = hostClasses;
            this.hashCode = Arrays.hashCode(types) * 31 + Arrays.hashCode(hostClasses);
        }

        static ArgSignature of(Value[] arguments) {
            byte[] types = new byte[arguments.length];
            Class<?>[] hostClasses = null;
            for (int i = 0; i < arguments.length; i++) {
                Value arg = arguments[i];
                if (arg.isNull()) {
                    types[i] = TYPE_NULL;
                } else if (arg.isHostObject()) {
                    types[i] = TYPE_HOST;
                    if (hostClasses == null) {
                        hostClasses = new Class<?>[arguments.length];
                    }
                    hostClasses[i] = arg.asHostObject().getClass();
                } else if (arg.isBoolean()) {
                    types[i] = TYPE_BOOLEAN;
                } else if (arg.isNumber()) {
                    if (arg.fitsInInt()) {
                        types[i] = TYPE_INT;
                    } else if (arg.fitsInLong()) {
                        types[i] = TYPE_LONG;
                    } else {
                        types[i] = TYPE_DOUBLE;
                    }
                } else if (arg.isString()) {
                    types[i] = TYPE_STRING;
                } else if (arg.hasArrayElements()) {
                    types[i] = TYPE_ARRAY;
                } else if (arg.canExecute()) {
                    types[i] = TYPE_EXECUTABLE;
                } else {
                    types[i] = TYPE_OBJECT;
                }
            }
            return new ArgSignature(types, hostClasses);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArgSignature that)) return false;
            return Arrays.equals(types, that.types) && Arrays.equals(hostClasses, that.hostClasses);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
