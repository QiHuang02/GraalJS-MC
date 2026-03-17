package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.typewrap.GenericTypeInfo;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyInstantiable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Java Class 的静态成员代理。
 */
public class JavaClassProxy extends AbstractReflectiveProxyObject implements ProxyInstantiable {
    private final Class<?> type;

    public JavaClassProxy(GraaljsContext context, Class<?> type) {
        super(context, type);
        this.type = type;
    }

    @Override
    protected Object target() {
        return null;
    }

    @Override
    protected boolean staticOnly() {
        return true;
    }

    @Override
    public Object unwrap() {
        return type;
    }

    @Override
    public Object newInstance(Value... arguments) {
        if (type.isRecord() && arguments.length == 1 && arguments[0].hasMembers()) {
            Object instance = context.jsToJava(arguments[0], type);
            return context.javaToJs(instance);
        }

        ConstructorMatch match = resolve(arguments);
        if (match == null) {
            throw new IllegalArgumentException("No matching constructor for " + type.getName());
        }

        try {
            Object instance = match.constructor().newInstance(match.arguments());
            return context.javaToJs(instance);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to instantiate " + type.getName(), exception);
        }
    }

    private ConstructorMatch resolve(Value[] arguments) {
        ConstructorMatch bestMatch = null;
        int bestScore = Integer.MAX_VALUE;

        List<Constructor<?>> constructors = cachedClassInfo.constructors();
        for (Constructor<?> constructor : constructors) {
            ConstructorMatch match = tryMatch(constructor, arguments);
            if (match != null && match.score() < bestScore) {
                bestMatch = match;
                bestScore = match.score();
            }
        }

        return bestMatch;
    }

    private ConstructorMatch tryMatch(Constructor<?> constructor, Value[] arguments) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Type[] genericParameterTypes = constructor.getGenericParameterTypes();
        boolean varArgs = constructor.isVarArgs();

        if ((!varArgs && parameterTypes.length != arguments.length)
                || (varArgs && arguments.length < parameterTypes.length - 1)) {
            return null;
        }

        Object[] invocationArguments = new Object[parameterTypes.length];
        int score = 0;

        for (int i = 0; i < parameterTypes.length; i++) {
            if (varArgs && i == parameterTypes.length - 1) {
                Class<?> componentType = parameterTypes[i].getComponentType();
                Object array = java.lang.reflect.Array.newInstance(componentType, arguments.length - i);
                for (int j = i; j < arguments.length; j++) {
                    Object converted = convertArgument(arguments[j], componentType);
                    if (converted == null && componentType.isPrimitive()) {
                        return null;
                    }
                    java.lang.reflect.Array.set(array, j - i, converted);
                    score += argumentScore(arguments[j], componentType, converted);
                }
                invocationArguments[i] = array;
                return new ConstructorMatch(constructor, invocationArguments, score);
            }

            Object converted = convertArgument(arguments[i], genericParameterTypes[i]);
            if (converted == null && parameterTypes[i].isPrimitive()) {
                return null;
            }
            invocationArguments[i] = converted;
            score += argumentScore(arguments[i], parameterTypes[i], converted);
        }

        return new ConstructorMatch(constructor, invocationArguments, score);
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

    private int argumentScore(Value argument, Class<?> parameterType, Object converted) {
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

    private record ConstructorMatch(Constructor<?> constructor, Object[] arguments, int score) {
    }
}
