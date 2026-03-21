package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.typewrap.GenericTypeInfo;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Java Class 的静态成员代理。
 */
public class JavaClassProxy extends AbstractReflectiveProxyObject implements ProxyInstantiable, ProxyExecutable {
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
    public Object getMember(String key) {
        // __javaObject__ 返回底层 Class 对象
        if ("__javaObject__".equals(key)) {
            return type;
        }
        // 先走正常的静态成员查找
        Object result = super.getMember(key);
        if (result != null) {
            return result;
        }
        // 回退：查找嵌套类
        return findNestedClass(key);
    }

    @Override
    public boolean hasMember(String key) {
        if ("__javaObject__".equals(key)) {
            return true;
        }
        return super.hasMember(key) || findNestedClass(key) != null;
    }

    /**
     * 查找嵌套类（内部类）。
     * 支持 {@code Java.type('some.Outer').Inner} 访问模式。
     */
    private Object findNestedClass(String name) {
        String nestedClassName = type.getName() + '$' + name;
        try {
            Class<?> nestedClass = Class.forName(nestedClassName, false, type.getClassLoader());
            if (context.getFactory().visibleToScripts(nestedClassName)) {
                return new JavaClassProxy(context, nestedClass);
            }
        } catch (ClassNotFoundException ignored) {
            // 不存在嵌套类
        }
        return null;
    }

    /**
     * 类型转换调用语义：{@code JavaClass(obj)} 当单参数时尝试类型转换/解包。
     * 多参数或转换失败时回退到构造函数。
     */
    @Override
    public Object execute(Value... arguments) {
        if (arguments.length == 1) {
            // 尝试解包并检查 instanceof
            Object unwrapped = ProxyValue.unwrapped(context.jsToJava(arguments[0], Object.class));
            if (unwrapped != null && type.isInstance(unwrapped)) {
                return context.javaToJs(unwrapped);
            }
            // 尝试通过 TypeWrapper 转换
            try {
                Object converted = context.jsToJava(arguments[0], type);
                if (converted != null) {
                    return context.javaToJs(converted);
                }
            } catch (RuntimeException ignored) {
                // 转换失败，回退到构造
            }
        }
        return newInstance(arguments);
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
        int bestCount = 0;

        CachedConstructorGroupInfo ctorGroup = lookup != null ? lookup.constructorGroup() : null;
        List<Constructor<?>> constructors = ctorGroup != null ? ctorGroup.constructors() : List.of();
        for (Constructor<?> constructor : constructors) {
            ConstructorMatch match = tryMatch(constructor, arguments);
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
                    "Ambiguous constructor overload: " + bestCount + " constructors match with score " + bestScore
                            + " for " + type.getName() + " with " + arguments.length + " argument(s)");
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
                return new ConstructorMatch(constructor, invocationArguments, score + OverloadScoring.varargsPenalty());
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
        return OverloadScoring.score(argument, parameterType, converted);
    }

    private record ConstructorMatch(Constructor<?> constructor, Object[] arguments, int score) {
    }
}
