package cn.qihuang02.graaljs.error;

/**
 * GraalJS-MC 异常体系的抽象基类。
 * 所有桥接层和脚本执行相关的异常都应继承此类。
 */
public abstract class GraaljsException extends RuntimeException {
    protected GraaljsException(String message) {
        super(message);
    }

    protected GraaljsException(String message, Throwable cause) {
        super(message, cause);
    }
}
