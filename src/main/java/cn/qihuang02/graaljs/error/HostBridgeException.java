package cn.qihuang02.graaljs.error;

/**
 * 桥接层错误的基类。
 * 涵盖类型转换、可见性违规、重载歧义等桥接层面的问题。
 */
public class HostBridgeException extends GraaljsException {
    public HostBridgeException(String message) {
        super(message);
    }

    public HostBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
