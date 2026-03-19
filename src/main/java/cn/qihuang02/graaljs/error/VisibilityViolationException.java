package cn.qihuang02.graaljs.error;

/**
 * 可见性违规异常。
 * 当脚本尝试访问被黑名单禁止的类时抛出。
 */
public class VisibilityViolationException extends HostBridgeException {
    private final String className;

    public VisibilityViolationException(String message, String className) {
        super(message);
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
}
