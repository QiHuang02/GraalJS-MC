package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.typewrap.GenericTypeInfo;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

/**
 * 方法代理，支持简单重载与可变参数分派。
 */
public class JavaMethodProxy implements ProxyExecutable {
    private final GraaljsContext context;
    private final Object target;
    private final List<Method> methods;

    public JavaMethodProxy(GraaljsContext context, Object target, List<Method> methods) {
        this.context = context;
        this.target = target;
        this.methods = methods;
    }

    @Override
    public Object execute(Value... arguments) {
        MethodMatch match = resolve(arguments);
        if (match == null) {
            throw new IllegalArgumentException("No matching overload for method call: " + methods);
        }

        try {
            Object result = match.method().invoke(target, match.arguments());
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
        MethodMatch bestMatch = null;
        int bestScore = Integer.MAX_VALUE;

        for (Method method : methods) {
            MethodMatch match = tryMatch(method, arguments);
            if (match != null && match.score() < bestScore) {
                bestMatch = match;
                bestScore = match.score();
            }
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
                return new MethodMatch(method, invocationArguments, score);
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
        if (converted == null) {
            return 10;
        }
        if (parameterType.isInstance(converted)) {
            return 0;
        }
        if (argument.isHostObject()) {
            return 1;
        }
        if (argument.isNumber()) {
            return 2;
        }
        if (argument.isString() || argument.isBoolean()) {
            return 3;
        }
        return 5;
    }

    private record MethodMatch(Method method, Object[] arguments, int score) {
        @Override
        public String toString() {
            return method + " " + Arrays.toString(arguments);
        }
    }
}
