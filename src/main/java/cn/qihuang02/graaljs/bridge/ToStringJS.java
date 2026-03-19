package cn.qihuang02.graaljs.bridge;

/**
 * 实现此接口的 Java 对象在桥接层的 toString 转换中使用自定义逻辑。
 * 所有 Proxy 的 toString() 会优先检查此接口。
 */
public interface ToStringJS {
    /**
     * 返回此对象在 JS 侧的字符串表示。
     */
    String toStringJS();
}
