package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 Byte Buddy 将 JS 对象适配为 Java 抽象类实例。
 */
public final class AbstractClassAdapter {
    private static final Map<AdapterSignature, GeneratedAdapter> CACHE = new ConcurrentHashMap<>();

    private AbstractClassAdapter() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T adapt(GraaljsContext context, Value value, Class<T> abstractType, Object... constructorArgs) {
        return adapt(context, value, abstractType, new Class<?>[0], constructorArgs);
    }

    @SuppressWarnings("unchecked")
    public static <T> T adapt(GraaljsContext context, Value value, Class<T> abstractType, Class<?>[] interfaceTypes, Object... constructorArgs) {
        if (abstractType.isInterface()) {
            throw new IllegalArgumentException("Target type is an interface, use InterfaceAdapter instead: " + abstractType.getName());
        }
        if (Modifier.isFinal(abstractType.getModifiers())) {
            throw new IllegalArgumentException("Cannot adapt final class: " + abstractType.getName());
        }
        if (!context.getFactory().visibleToScripts(abstractType.getName(), ClassVisibilityContext.ADAPTER_SUPER)) {
            throw new IllegalArgumentException("Abstract class is not visible to scripts: " + abstractType.getName());
        }
        for (Class<?> interfaceType : interfaceTypes) {
            if (!interfaceType.isInterface()) {
                throw new IllegalArgumentException("Additional type is not an interface: " + interfaceType.getName());
            }
            if (!context.getFactory().visibleToScripts(interfaceType.getName(), ClassVisibilityContext.ADAPTER_INTERFACE)) {
                throw new IllegalArgumentException("Interface is not visible to scripts: " + interfaceType.getName());
            }
        }

        AdapterSignature signature = new AdapterSignature(abstractType, List.copyOf(Arrays.asList(interfaceTypes)));
        GeneratedAdapter generatedAdapter = CACHE.computeIfAbsent(signature, AbstractClassAdapter::generateAdapter);
        Object instance = instantiate(context, generatedAdapter.generatedType(), constructorArgs);
        try {
            generatedAdapter.interceptorField().set(instance, new JsAbstractMethodInterceptor(context, value, signature));
            return (T) instance;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to initialize abstract class adapter for " + abstractType.getName(), exception);
        }
    }

    private static GeneratedAdapter generateAdapter(AdapterSignature signature) {
        try {
            Class<?> generatedType = new ByteBuddy()
                    .subclass(signature.superClass(), net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.IMITATE_SUPER_CLASS_OPENING)
                    .implement(signature.interfaces().toArray(Class<?>[]::new))
                    .defineField("__graaljsInterceptor", JsAbstractMethodInterceptor.class, Visibility.PRIVATE)
                    // 拦截所有可覆盖方法（抽象 + 非 final 非 static 的 public/protected）
                    .method(ElementMatchers.isAbstract()
                            .or(ElementMatchers.not(ElementMatchers.isFinal())
                                    .and(ElementMatchers.not(ElementMatchers.isStatic()))
                                    .and(ElementMatchers.not(ElementMatchers.isNative()))
                                    .and(ElementMatchers.isPublic().or(ElementMatchers.isProtected()))
                                    .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)))))
                    .intercept(MethodDelegation.toField("__graaljsInterceptor"))
                    .defineMethod("__graaljsSetInterceptor", void.class, Visibility.PUBLIC)
                    .withParameters(JsAbstractMethodInterceptor.class)
                    .intercept(FieldAccessor.ofField("__graaljsInterceptor"))
                    .make()
                    .load(signature.superClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();

            Field interceptorField = generatedType.getDeclaredField("__graaljsInterceptor");
            interceptorField.setAccessible(true);
            return new GeneratedAdapter(generatedType, interceptorField);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to generate abstract class adapter for " + signature.superClass().getName(), exception);
        }
    }

    private static Object instantiate(GraaljsContext context, Class<?> generatedType, Object[] constructorArgs) {
        Constructor<?> bestConstructor = null;
        Object[] bestArguments = null;
        int bestScore = Integer.MAX_VALUE;
        int bestCount = 0;

        for (Constructor<?> constructor : generatedType.getConstructors()) {
            ConstructorMatch match = tryMatch(context, constructor, constructorArgs);
            if (match != null) {
                if (match.score() < bestScore) {
                    bestConstructor = constructor;
                    bestArguments = match.arguments();
                    bestScore = match.score();
                    bestCount = 1;
                } else if (match.score() == bestScore) {
                    bestCount++;
                }
            }
        }

        if (bestConstructor == null) {
            throw new IllegalArgumentException("No matching constructor for abstract adapter: " + generatedType.getSuperclass().getName());
        }

        if (bestCount > 1) {
            throw new AmbiguousOverloadException(
                    "Ambiguous constructor overload: " + bestCount + " constructors match with score " + bestScore
                            + " for " + generatedType.getSuperclass().getName() + " with " + constructorArgs.length + " argument(s)");
        }

        try {
            return bestConstructor.newInstance(bestArguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate abstract adapter for " + generatedType.getSuperclass().getName(), exception);
        }
    }

    private static ConstructorMatch tryMatch(GraaljsContext context, Constructor<?> constructor, Object[] sourceArguments) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        boolean varArgs = constructor.isVarArgs();

        if ((!varArgs && parameterTypes.length != sourceArguments.length)
                || (varArgs && sourceArguments.length < parameterTypes.length - 1)) {
            return null;
        }

        Object[] invocationArguments = new Object[parameterTypes.length];
        int score = 0;

        for (int i = 0; i < parameterTypes.length; i++) {
            if (varArgs && i == parameterTypes.length - 1) {
                Class<?> componentType = parameterTypes[i].getComponentType();
                Object array = java.lang.reflect.Array.newInstance(componentType, sourceArguments.length - i);
                for (int j = i; j < sourceArguments.length; j++) {
                    Object converted = convertArgument(context, sourceArguments[j], componentType);
                    if (converted == null && componentType.isPrimitive()) {
                        return null;
                    }
                    java.lang.reflect.Array.set(array, j - i, converted);
                    score += argumentScore(sourceArguments[j], componentType, converted);
                }
                invocationArguments[i] = array;
                return new ConstructorMatch(invocationArguments, score);
            }

            Object converted = convertArgument(context, sourceArguments[i], parameterTypes[i]);
            if (converted == null && parameterTypes[i].isPrimitive()) {
                return null;
            }
            invocationArguments[i] = converted;
            score += argumentScore(sourceArguments[i], parameterTypes[i], converted);
        }

        return new ConstructorMatch(invocationArguments, score);
    }

    private static Object convertArgument(GraaljsContext context, Object source, Class<?> targetType) {
        try {
            return context.jsToJava(source, targetType);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int argumentScore(Object source, Class<?> parameterType, Object converted) {
        return OverloadScoring.scoreJavaArg(source, parameterType, converted);
    }

    private record GeneratedAdapter(Class<?> generatedType, Field interceptorField) {
    }

    private record AdapterSignature(Class<?> superClass, List<Class<?>> interfaces) {
    }

    private record ConstructorMatch(Object[] arguments, int score) {
    }

    /**
     * 负责把方法调用转发到 JS 对象。
     * 对于非抽象方法，如果 JS 对象没有提供实现，则回退到 super 调用。
     */
    public static final class JsAbstractMethodInterceptor {
        private final GraaljsContext context;
        private final Value target;
        private final AdapterSignature signature;

        public JsAbstractMethodInterceptor(GraaljsContext context, Value target, AdapterSignature signature) {
            this.context = context;
            this.target = target;
            this.signature = signature;
        }

        @RuntimeType
        public Object intercept(@This Object self, @Origin Method method, @AllArguments Object[] args,
                                @SuperCall(nullIfImpossible = true) java.util.concurrent.Callable<?> superCall) throws Exception {
            Object[] safeArgs = args == null ? new Object[0] : args;
            String methodName = method.getName();

            // 检查 JS 对象是否提供了该方法
            boolean jsHasMethod = target.hasMember(methodName) && target.getMember(methodName).canExecute();

            if (jsHasMethod) {
                Object[] jsArgs = new Object[safeArgs.length];
                for (int i = 0; i < safeArgs.length; i++) {
                    jsArgs[i] = context.javaToJs(safeArgs[i]);
                }
                Value member = target.getMember(methodName);
                return context.jsToJava(member.execute(jsArgs), method.getGenericReturnType());
            }

            // JS 没有提供实现，尝试调用 super
            if (superCall != null) {
                return superCall.call();
            }

            // 抽象方法且 JS 没有实现
            throw new IllegalStateException("JS value does not implement abstract method '" + methodName
                    + "' for " + signature.superClass().getName() + " with args " + Arrays.toString(safeArgs));
        }
    }
}
