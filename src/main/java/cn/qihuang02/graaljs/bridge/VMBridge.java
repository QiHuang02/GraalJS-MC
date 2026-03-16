package cn.qihuang02.graaljs.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一封装基于 JDK Proxy 的接口代理创建逻辑。
 */
public final class VMBridge {
    private static final Map<List<Class<?>>, Constructor<?>> PROXY_CACHE = new ConcurrentHashMap<>();

    private VMBridge() {
    }

    public static Constructor<?> getInterfaceProxyHelper(Class<?>... interfaces) {
        List<Class<?>> key = List.copyOf(Arrays.asList(interfaces));
        return PROXY_CACHE.computeIfAbsent(key, ignored -> createProxyConstructor(interfaces));
    }

    public static Object newInterfaceProxy(Constructor<?> proxyHelper, InvocationHandler handler) {
        try {
            return proxyHelper.newInstance(handler);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create interface proxy", exception);
        }
    }

    private static Constructor<?> createProxyConstructor(Class<?>[] interfaces) {
        try {
            ClassLoader loader = resolveClassLoader(interfaces);
            Class<?> proxyClass = Proxy.getProxyClass(loader, interfaces);
            Constructor<?> constructor = proxyClass.getConstructor(InvocationHandler.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to prepare interface proxy helper for " + Arrays.toString(interfaces), exception);
        }
    }

    private static ClassLoader resolveClassLoader(Class<?>[] interfaces) {
        for (Class<?> interfaceType : interfaces) {
            ClassLoader loader = interfaceType.getClassLoader();
            if (loader != null) {
                return loader;
            }
        }
        return VMBridge.class.getClassLoader();
    }
}
