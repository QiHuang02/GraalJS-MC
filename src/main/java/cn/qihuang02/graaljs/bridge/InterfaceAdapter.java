package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 将 JS 函数或对象适配为 Java 接口。
 */
public final class InterfaceAdapter {
    private InterfaceAdapter() {
    }

    public static <T> T adapt(GraaljsContext context, Value value, Class<T> interfaceType) {
        if (!interfaceType.isInterface()) {
            throw new IllegalArgumentException("Target type is not an interface: " + interfaceType.getName());
        }

        if (value.canExecute() && isFunctionalInterface(interfaceType)) {
            return value.as(interfaceType);
        }

        Object proxy = adaptInterfaces(context, value, new Class<?>[]{interfaceType});
        return interfaceType.cast(proxy);
    }

    public static Object adaptInterfaces(GraaljsContext context, Value value, Class<?>... interfaceTypes) {
        if (interfaceTypes.length == 0) {
            throw new IllegalArgumentException("At least one interface type is required");
        }
        for (Class<?> interfaceType : interfaceTypes) {
            if (!interfaceType.isInterface()) {
                throw new IllegalArgumentException("Target type is not an interface: " + interfaceType.getName());
            }
            if (!context.getFactory().visibleToScripts(interfaceType.getName(), ClassVisibilityContext.ADAPTER_INTERFACE)) {
                throw new IllegalArgumentException("Interface is not visible to scripts: " + interfaceType.getName());
            }
        }

        InterfaceContract contract = createContract(interfaceTypes);
        if (value.canExecute() && !contract.supportsFunctionTarget()) {
            throw new IllegalArgumentException("JS function can only implement interfaces whose abstract methods share the same signature: " + Arrays.toString(interfaceTypes));
        }

        InvocationHandler handler = new JsInterfaceInvocationHandler(context, value, contract);
        Constructor<?> proxyHelper = VMBridge.getInterfaceProxyHelper(interfaceTypes);
        return VMBridge.newInterfaceProxy(proxyHelper, handler);
    }

    private static boolean isFunctionalInterface(Class<?> type) {
        int abstractMethodCount = 0;
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (Modifier.isAbstract(method.getModifiers()) && ++abstractMethodCount > 1) {
                return false;
            }
        }
        return abstractMethodCount == 1;
    }

    private static InterfaceContract createContract(Class<?>[] interfaceTypes) {
        List<Method> abstractMethods = new ArrayList<>();
        for (Class<?> interfaceType : interfaceTypes) {
            for (Method method : interfaceType.getMethods()) {
                if (method.getDeclaringClass() == Object.class || method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    abstractMethods.add(method);
                }
            }
        }

        MethodSignature signature = null;
        boolean compatibleForFunctionTarget = true;
        for (Method method : abstractMethods) {
            MethodSignature current = MethodSignature.of(method);
            if (signature == null) {
                signature = current;
            } else if (!signature.equals(current)) {
                compatibleForFunctionTarget = false;
                break;
            }
        }

        return new InterfaceContract(interfaceTypes[0], abstractMethods, compatibleForFunctionTarget);
    }

    private record JsInterfaceInvocationHandler(GraaljsContext context, Value target, InterfaceContract contract) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }

            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            Object[] convertedArgs = args == null ? new Object[0] : args;
            Object[] jsArgs = new Object[convertedArgs.length];
            for (int i = 0; i < convertedArgs.length; i++) {
                jsArgs[i] = context.javaToJs(convertedArgs[i]);
            }
            Value member = null;
            if (target.hasMember(method.getName())) {
                member = target.getMember(method.getName());
            }

            if (member != null && member.canExecute()) {
                return context.jsToJava(member.execute(jsArgs), method.getGenericReturnType());
            }
            if (target.canInvokeMember(method.getName())) {
                return context.jsToJava(target.invokeMember(method.getName(), jsArgs), method.getGenericReturnType());
            }
            if (target.canExecute()) {
                return context.jsToJava(target.execute(jsArgs), method.getGenericReturnType());
            }

            throw new IllegalStateException("JS value does not implement method '" + method.getName() + "' for interface " + contract.primaryInterface().getName());
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> "JsInterfaceProxy[" + contract.primaryInterface().getName() + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }
    }

    private record InterfaceContract(Class<?> primaryInterface, List<Method> abstractMethods, boolean supportsFunctionTarget) {
    }

    private record MethodSignature(Class<?> returnType, Class<?>[] parameterTypes) {
        private static MethodSignature of(Method method) {
            return new MethodSignature(method.getReturnType(), method.getParameterTypes());
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof MethodSignature signature)) {
                return false;
            }
            return returnType == signature.returnType && Arrays.equals(parameterTypes, signature.parameterTypes);
        }

        @Override
        public int hashCode() {
            return 31 * returnType.hashCode() + Arrays.hashCode(parameterTypes);
        }
    }
}
