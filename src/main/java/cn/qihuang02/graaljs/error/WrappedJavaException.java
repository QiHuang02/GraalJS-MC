package cn.qihuang02.graaljs.error;

/**
 * 包装的 Java 异常。
 * 当 Java 方法调用抛出异常且该异常需要传递给 JS 侧时使用。
 */
public class WrappedJavaException extends GraaljsException {
    private final Throwable wrappedException;

    public WrappedJavaException(String message, Throwable wrappedException) {
        super(message, wrappedException);
        this.wrappedException = wrappedException;
    }

    public WrappedJavaException(Throwable wrappedException) {
        this("Java exception: " + wrappedException.getMessage(), wrappedException);
    }

    /**
     * 获取被包装的原始 Java 异常。
     */
    public Throwable getWrappedException() {
        return wrappedException;
    }
}
