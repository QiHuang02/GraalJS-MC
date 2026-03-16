package cn.qihuang02.graaljs.bridge;

/**
 * 为脚本代理提供原始 Java 值回溯能力。
 */
public interface ProxyValue {
    Object unwrap();
}
