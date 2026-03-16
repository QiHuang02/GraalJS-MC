package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.core.ScriptType;
import cn.qihuang02.graaljs.bridge.JavaAdapterTemplate;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import cn.qihuang02.graaljs.util.RemapForJS;
import org.graalvm.polyglot.Value;

/**
 * 提供脚本侧可显式调用的运行时能力。
 */
public class RuntimeAPI {
    private final GraaljsContextFactory factory;
    private final GraaljsContext context;

    public RuntimeAPI(GraaljsContextFactory factory, GraaljsContext context) {
        this.factory = factory;
        this.context = context;
    }

    public String scriptType() {
        return context.getType().name().toLowerCase();
    }

    public Object wrap(Object value) {
        return context.javaToJs(value);
    }

    public Object type(String className) throws ClassNotFoundException {
        return context.wrapJavaClass(factory.resolveVisibleClass(className, ClassVisibilityContext.CLASS_LOOKUP));
    }

    public Object adapt(Value value, String interfaceClassName) throws ClassNotFoundException {
        Class<?> interfaceType = factory.resolveVisibleClass(interfaceClassName, ClassVisibilityContext.ADAPTER_INTERFACE);
        if (!interfaceType.isInterface()) {
            throw new IllegalArgumentException("Target type is not an interface: " + interfaceClassName);
        }
        return context.asInterface(value, interfaceType);
    }

    public Object proxy(Value value, String... interfaceClassNames) throws ClassNotFoundException {
        if (interfaceClassNames.length == 0) {
            throw new IllegalArgumentException("At least one interface class name is required");
        }

        Class<?>[] interfaceTypes = new Class<?>[interfaceClassNames.length];
        for (int i = 0; i < interfaceClassNames.length; i++) {
            Class<?> interfaceType = factory.resolveVisibleClass(interfaceClassNames[i], ClassVisibilityContext.ADAPTER_INTERFACE);
            if (!interfaceType.isInterface()) {
                throw new IllegalArgumentException("Target type is not an interface: " + interfaceClassNames[i]);
            }
            interfaceTypes[i] = interfaceType;
        }
        return context.asInterfaces(value, interfaceTypes);
    }

    public Object adapterType(String className) throws ClassNotFoundException {
        return new JavaAdapterTemplate(
                context,
                resolveAdapterSuper(className),
                resolveAdapterInterfaces(className, new String[0])
        );
    }

    public Object adapterType(String className, Value interfaceClassNames) throws ClassNotFoundException {
        String[] interfaceNames = context.jsToJava(interfaceClassNames, String[].class);
        return new JavaAdapterTemplate(
                context,
                resolveAdapterSuper(className),
                resolveAdapterInterfaces(className, interfaceNames)
        );
    }

    public Object extend(Value value, String abstractClassName, Object... constructorArgs) throws ClassNotFoundException {
        Class<?> abstractType = factory.resolveVisibleClass(abstractClassName, ClassVisibilityContext.ADAPTER_SUPER);
        return context.asAbstractClass(value, abstractType, constructorArgs);
    }

    public Object extendWithInterfaces(Value value, String abstractClassName, Value interfaceClassNames, Object... constructorArgs) throws ClassNotFoundException {
        Class<?> abstractType = factory.resolveVisibleClass(abstractClassName, ClassVisibilityContext.ADAPTER_SUPER);
        String[] interfaceNames = context.jsToJava(interfaceClassNames, String[].class);
        Class<?>[] interfaceTypes = new Class<?>[interfaceNames.length];
        for (int i = 0; i < interfaceNames.length; i++) {
            Class<?> interfaceType = factory.resolveVisibleClass(interfaceNames[i], ClassVisibilityContext.ADAPTER_INTERFACE);
            if (!interfaceType.isInterface()) {
                throw new IllegalArgumentException("Additional type is not an interface: " + interfaceNames[i]);
            }
            interfaceTypes[i] = interfaceType;
        }
        return context.asAbstractClass(value, abstractType, interfaceTypes, constructorArgs);
    }

    private Class<?> resolveAdapterSuper(String className) throws ClassNotFoundException {
        Class<?> type = factory.resolveVisibleClass(className, ClassVisibilityContext.CLASS_LOOKUP);
        if (!type.isInterface() && !factory.visibleToScripts(className, ClassVisibilityContext.ADAPTER_SUPER)) {
            throw new IllegalArgumentException("Abstract class is not visible to scripts: " + className);
        }
        return type.isInterface() ? null : type;
    }

    private Class<?>[] resolveAdapterInterfaces(String className, String[] interfaceNames) throws ClassNotFoundException {
        Class<?> type = factory.resolveVisibleClass(className, ClassVisibilityContext.CLASS_LOOKUP);
        int offset = type.isInterface() ? 1 : 0;
        Class<?>[] interfaceTypes = new Class<?>[interfaceNames.length + offset];
        if (type.isInterface()) {
            if (!factory.visibleToScripts(className, ClassVisibilityContext.ADAPTER_INTERFACE)) {
                throw new IllegalArgumentException("Interface is not visible to scripts: " + className);
            }
            interfaceTypes[0] = type;
        } else if (!java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("JavaAdapter target must be an interface or abstract class: " + className);
        }

        for (int i = 0; i < interfaceNames.length; i++) {
            Class<?> interfaceType = factory.resolveVisibleClass(interfaceNames[i], ClassVisibilityContext.ADAPTER_INTERFACE);
            if (!interfaceType.isInterface()) {
                throw new IllegalArgumentException("Additional type is not an interface: " + interfaceNames[i]);
            }
            interfaceTypes[i + offset] = interfaceType;
        }
        return interfaceTypes;
    }

    @RemapForJS("reload")
    public boolean reloadCurrent() {
        factory.reload(context.getType());
        return true;
    }

    public boolean reload(String scriptType) {
        factory.reload(ScriptType.valueOf(scriptType.trim().toUpperCase()));
        return true;
    }
}
