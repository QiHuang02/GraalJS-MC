package cn.qihuang02.graaljs.bridge;

/**
 * 实现此接口的 Java 对象在桥接层的相等性比较中使用自定义逻辑。
 * 当 {@link AbstractReflectiveProxyObject} 进行 equals 比较时，
 * 如果目标对象实现了此接口，会优先调用 {@link #specialEquals(Object)}。
 */
public interface SpecialEquality {
    /**
     * 自定义相等性比较。
     *
     * @param other 要比较的对象（已解包的 Java 对象）
     * @return 如果认为相等则返回 true
     */
    boolean specialEquals(Object other);
}
