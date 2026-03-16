package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.bridge.JavaAdapterTemplate;
import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import org.graalvm.polyglot.Value;

/**
 * 提供接近 Rhino JavaAdapter 的统一适配入口。
 */
public class JavaAdapterAPI {
    private final GraaljsContextFactory factory;
    private final GraaljsContext context;

    public JavaAdapterAPI(GraaljsContextFactory factory, GraaljsContext context) {
        this.factory = factory;
        this.context = context;
    }

    public Object type(String className) throws ClassNotFoundException {
        return buildTemplate(className, new Class<?>[0]);
    }

    public Object type(String className, Value interfaceClassNames) throws ClassNotFoundException {
        return buildTemplate(className, resolveInterfaces(interfaceClassNames));
    }

    public Object create(String className, Value implementation, Object... constructorArgs) throws ClassNotFoundException {
        return instantiate(resolveTemplate(className, new Class<?>[0]), implementation, constructorArgs);
    }

    public Object createWithInterfaces(String className, Value interfaceClassNames, Value implementation, Object... constructorArgs) throws ClassNotFoundException {
        return instantiate(resolveTemplate(className, resolveInterfaces(interfaceClassNames)), implementation, constructorArgs);
    }

    private Object buildTemplate(String className, Class<?>[] extraInterfaces) throws ClassNotFoundException {
        ResolvedAdapterTarget target = resolveTemplate(className, extraInterfaces);
        return new JavaAdapterTemplate(context, target.superClass(), target.interfaceTypes());
    }

    private Object instantiate(ResolvedAdapterTarget target, Value implementation, Object... constructorArgs) {
        if (target.superClass() != null) {
            return context.asAbstractClass(implementation, target.superClass(), target.interfaceTypes(), constructorArgs);
        }
        return context.asInterfaces(implementation, target.interfaceTypes());
    }

    private ResolvedAdapterTarget resolveTemplate(String className, Class<?>[] extraInterfaces) throws ClassNotFoundException {
        Class<?> type = factory.resolveVisibleClass(className, ClassVisibilityContext.CLASS_LOOKUP);
        if (type.isInterface()) {
            if (!factory.visibleToScripts(className, ClassVisibilityContext.ADAPTER_INTERFACE)) {
                throw new IllegalArgumentException("Interface is not visible to scripts: " + className);
            }
            Class<?>[] interfaces = new Class<?>[extraInterfaces.length + 1];
            interfaces[0] = type;
            System.arraycopy(extraInterfaces, 0, interfaces, 1, extraInterfaces.length);
            return new ResolvedAdapterTarget(null, interfaces);
        }
        if (!factory.visibleToScripts(className, ClassVisibilityContext.ADAPTER_SUPER)) {
            throw new IllegalArgumentException("Abstract class is not visible to scripts: " + className);
        }
        if (!java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("JavaAdapter target must be an interface or abstract class: " + className);
        }
        return new ResolvedAdapterTarget(type, extraInterfaces);
    }

    private Class<?>[] resolveInterfaces(Value interfaceClassNames) throws ClassNotFoundException {
        String[] interfaceNames = context.jsToJava(interfaceClassNames, String[].class);
        Class<?>[] interfaceTypes = new Class<?>[interfaceNames.length];
        for (int i = 0; i < interfaceNames.length; i++) {
            Class<?> interfaceType = factory.resolveVisibleClass(interfaceNames[i], ClassVisibilityContext.ADAPTER_INTERFACE);
            if (!interfaceType.isInterface()) {
                throw new IllegalArgumentException("Additional type is not an interface: " + interfaceNames[i]);
            }
            interfaceTypes[i] = interfaceType;
        }
        return interfaceTypes;
    }

    private record ResolvedAdapterTarget(Class<?> superClass, Class<?>[] interfaceTypes) {
    }
}
