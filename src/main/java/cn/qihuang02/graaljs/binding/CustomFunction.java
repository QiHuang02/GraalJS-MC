package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.util.CustomJavaToJsWrapper;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/**
 * 带参数类型转换的函数包装。
 */
public class CustomFunction implements CustomJavaToJsWrapper {
    private final String name;
    private final Func callback;
    private final Class<?>[] argTypes;

    public CustomFunction(String name, Func callback, Class<?>[] argTypes) {
        this.name = name;
        this.callback = callback;
        this.argTypes = argTypes == null ? new Class<?>[0] : argTypes;
    }

    @Override
    public Object convertJavaToJs(GraaljsContext context) {
        return (ProxyExecutable) arguments -> {
            Object[] converted = new Object[Math.min(arguments.length, argTypes.length)];
            for (int i = 0; i < converted.length; i++) {
                converted[i] = context.jsToJava(arguments[i], argTypes[i]);
            }
            return context.javaToJs(callback.call(context, converted));
        };
    }

    public String name() {
        return name;
    }

    @FunctionalInterface
    public interface Func {
        Object call(GraaljsContext context, Object[] args);
    }
}
