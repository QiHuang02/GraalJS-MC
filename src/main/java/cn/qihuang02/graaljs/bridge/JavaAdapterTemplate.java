package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Arrays;

/**
 * 可复用的 JavaAdapter 模板，统一抽象类适配和接口代理入口。
 */
public class JavaAdapterTemplate implements ProxyObject, ProxyInstantiable, ProxyValue {
    private final GraaljsContext context;
    private final Class<?> superClass;
    private final Class<?>[] interfaceTypes;

    public JavaAdapterTemplate(GraaljsContext context, Class<?> superClass, Class<?>[] interfaceTypes) {
        this.context = context;
        this.superClass = superClass;
        this.interfaceTypes = interfaceTypes == null ? new Class<?>[0] : Arrays.copyOf(interfaceTypes, interfaceTypes.length);
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "kind" -> superClass == null ? "interface" : "abstract";
            case "superClassName" -> superClass == null ? null : superClass.getName();
            case "interfaceNames" -> Arrays.stream(interfaceTypes).map(Class::getName).toArray(String[]::new);
            case "create" -> (ProxyExecutable) this::newInstance;
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return new String[]{"kind", "superClassName", "interfaceNames", "create"};
    }

    @Override
    public boolean hasMember(String key) {
        return "kind".equals(key)
                || "superClassName".equals(key)
                || "interfaceNames".equals(key)
                || "create".equals(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("JavaAdapter template is read-only");
    }

    @Override
    public Object newInstance(Value... arguments) {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("JavaAdapter requires an implementation object or function");
        }

        Value implementation = arguments[0];
        Object[] constructorArgs = new Object[Math.max(0, arguments.length - 1)];
        System.arraycopy(arguments, 1, constructorArgs, 0, constructorArgs.length);

        Object instance;
        if (superClass != null) {
            instance = context.asAbstractClass(implementation, superClass, interfaceTypes, constructorArgs);
        } else {
            instance = context.asInterfaces(implementation, interfaceTypes);
        }
        return context.javaToJs(instance);
    }

    @Override
    public Object unwrap() {
        return superClass == null ? Arrays.copyOf(interfaceTypes, interfaceTypes.length) : superClass;
    }
}
