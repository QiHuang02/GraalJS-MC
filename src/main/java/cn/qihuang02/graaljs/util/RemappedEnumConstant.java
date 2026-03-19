package cn.qihuang02.graaljs.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许枚举常量在 JS 侧使用不同的名称。
 * 标注在枚举常量上，{@link cn.qihuang02.graaljs.typewrap.EnumTypeWrapper} 会识别此注解，
 * 支持通过重映射名称查找枚举值。
 *
 * <pre>{@code
 * public enum Direction {
 *     @RemappedEnumConstant("up")
 *     NORTH,
 *     @RemappedEnumConstant("down")
 *     SOUTH
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RemappedEnumConstant {
    /**
     * JS 侧使用的别名。
     */
    String value();
}
