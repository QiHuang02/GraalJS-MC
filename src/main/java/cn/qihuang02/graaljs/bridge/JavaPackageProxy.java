package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟 Rhino NativeJavaPackage 的按需类/包解析。
 */
public class JavaPackageProxy implements ProxyObject {
    private final GraaljsContext context;
    private final GraaljsContextFactory factory;
    private final String packageName;
    private final Map<String, Object> cache;

    public JavaPackageProxy(GraaljsContext context, GraaljsContextFactory factory, String packageName) {
        this.context = context;
        this.factory = factory;
        this.packageName = packageName == null ? "" : packageName;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Object getMember(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(key, this::resolveMember);
    }

    @Override
    public Object getMemberKeys() {
        return cache.keySet().toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return key != null && !key.isBlank();
    }

    @Override
    public void putMember(String key, org.graalvm.polyglot.Value value) {
        throw new UnsupportedOperationException("Java package bindings are read-only");
    }

    private Object resolveMember(String key) {
        String fullName = packageName.isBlank() ? key : packageName + "." + key;
        Class<?> javaClass = factory.resolveVisibleClassOrNull(fullName, ClassVisibilityContext.PACKAGE_LOOKUP);
        if (javaClass != null) {
            return context.wrapJavaClass(javaClass);
        }
        return factory.createPackageProxy(context, fullName);
    }
}
