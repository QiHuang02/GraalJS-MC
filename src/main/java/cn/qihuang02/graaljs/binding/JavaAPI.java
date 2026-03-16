package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import org.graalvm.polyglot.Value;

/**
 * 提供更接近 Rhino 的 Java 类查找入口。
 */
public class JavaAPI {
    private final GraaljsContextFactory factory;
    private final GraaljsContext context;

    public JavaAPI(GraaljsContextFactory factory, GraaljsContext context) {
        this.factory = factory;
        this.context = context;
    }

    public Object type(String className) throws ClassNotFoundException {
        return context.wrapJavaClass(factory.resolveVisibleClass(className, ClassVisibilityContext.CLASS_LOOKUP));
    }

    public Object typeOrNull(String className) {
        Class<?> type = factory.resolveVisibleClassOrNull(className, ClassVisibilityContext.CLASS_LOOKUP);
        return type == null ? null : context.wrapJavaClass(type);
    }

    public boolean isVisible(String className) {
        return factory.resolveVisibleClassOrNull(className, ClassVisibilityContext.CLASS_LOOKUP) != null;
    }

    public Object packageOf(String packageName) {
        return factory.createPackageProxy(context, packageName);
    }

    public Object extend(String abstractClassName, Value value, Object... constructorArgs) throws ClassNotFoundException {
        return context.asAbstractClass(value, factory.resolveVisibleClass(abstractClassName, ClassVisibilityContext.ADAPTER_SUPER), constructorArgs);
    }

    public Object extendWithInterfaces(String abstractClassName, Value value, Value interfaceClassNames, Object... constructorArgs) throws ClassNotFoundException {
        String[] interfaceNames = context.jsToJava(interfaceClassNames, String[].class);
        Class<?>[] interfaceTypes = new Class<?>[interfaceNames.length];
        for (int i = 0; i < interfaceNames.length; i++) {
            Class<?> interfaceType = factory.resolveVisibleClass(interfaceNames[i], ClassVisibilityContext.ADAPTER_INTERFACE);
            if (!interfaceType.isInterface()) {
                throw new IllegalArgumentException("Additional type is not an interface: " + interfaceNames[i]);
            }
            interfaceTypes[i] = interfaceType;
        }
        return context.asAbstractClass(value, factory.resolveVisibleClass(abstractClassName, ClassVisibilityContext.ADAPTER_SUPER), interfaceTypes, constructorArgs);
    }
}
