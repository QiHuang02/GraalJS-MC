package cn.qihuang02.graaljs.bridge;

/**
 * 枚举常量可实现此接口以提供 JS 侧的重映射名称。
 * 与 {@link cn.qihuang02.graaljs.util.RemappedEnumConstant} 注解形式互补，
 * 兼容 Rhino 的接口约定（KubeJS 等下游 mod 的枚举类会实现此接口）。
 *
 * <pre>{@code
 * public enum Direction implements RemappedEnumConstant {
 *     NORTH, SOUTH;
 *
 *     @Override
 *     public String getRemappedEnumConstantName() {
 *         return name().toLowerCase();
 *     }
 * }
 * }</pre>
 */
public interface RemappedEnumConstant {
    /**
     * 返回此枚举常量在 JS 侧使用的别名。
     *
     * @return 重映射名称，返回 null 或空字符串表示不重映射
     */
    String getRemappedEnumConstantName();
}
