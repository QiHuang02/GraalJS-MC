package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;

/**
 * 普通 Java 对象代理。
 */
public class JavaObjectProxy extends AbstractReflectiveProxyObject {
    private final Object object;

    public JavaObjectProxy(GraaljsContext context, Object object) {
        super(context, object.getClass());
        this.object = object;
    }

    @Override
    protected Object target() {
        return object;
    }

    @Override
    protected boolean staticOnly() {
        return false;
    }

    @Override
    protected boolean allowInstanceStaticFallback() {
        return context.getFactory().getInstanceStaticFallback();
    }

    @Override
    public Object unwrap() {
        return object;
    }
}
