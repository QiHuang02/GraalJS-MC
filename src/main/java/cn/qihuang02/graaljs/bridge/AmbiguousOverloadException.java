package cn.qihuang02.graaljs.bridge;

/**
 * 当多个重载方法/构造器的匹配评分相同时抛出。
 */
public class AmbiguousOverloadException extends RuntimeException {
    public AmbiguousOverloadException(String message) {
        super(message);
    }
}
