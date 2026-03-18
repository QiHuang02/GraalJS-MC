package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.util.CustomJavaToJsWrapper;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/**
 * 不做类型转换的动态函数包装。
 */
public class DynamicFunction implements CustomJavaToJsWrapper {
    private final Callback callback;

    public DynamicFunction(Callback callback) {
        this.callback = callback;
    }

    @Override
    public Object convertJavaToJs(GraaljsContext context) {
        return (ProxyExecutable) callback::call;
    }

    @FunctionalInterface
    public interface Callback {
        Object call(Value[] args);
    }
}
