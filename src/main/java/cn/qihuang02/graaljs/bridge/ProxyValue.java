package cn.qihuang02.graaljs.bridge;

/**
 * 为脚本代理提供原始 Java 值回溯能力。
 */
public interface ProxyValue {
    Object unwrap();

    /**
     * 递归解包嵌套的 ProxyValue，返回最终的 Java 对象。
     */
    static Object unwrapped(Object o) {
        while (o instanceof ProxyValue pv) {
            Object inner = pv.unwrap();
            if (inner == o) {
                break; // 防止无限循环
            }
            o = inner;
        }
        return o;
    }
}
