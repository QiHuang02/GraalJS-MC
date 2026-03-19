package cn.qihuang02.graaljs.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法或类的返回值为 {@code this}（即返回调用者自身）。
 * 桥接层在 {@link cn.qihuang02.graaljs.bridge.JavaMethodProxy} 中识别此注解，
 * 返回值直接返回原始代理对象而非重新包装，优化链式调用。
 *
 * <p>标注在方法上时，该方法被视为 returnsSelf。
 * <p>标注在类上时，所有返回类型匹配 {@link #value()} 的方法自动视为 returnsSelf。
 *
 * <pre>{@code
 * // 方法级别
 * public class Builder {
 *     @ReturnsSelf
 *     public Builder withName(String name) { ... }
 * }
 *
 * // 类级别：所有返回 Builder 的方法自动视为 returnsSelf
 * @ReturnsSelf
 * public class Builder {
 *     public Builder withName(String name) { ... }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(ReturnsSelfContainer.class)
public @interface ReturnsSelf {
    /**
     * 匹配的返回类型。默认 {@code Object.class} 表示使用声明类本身。
     * 仅在类级别标注时有意义：返回类型可赋值给此值的方法才被视为 returnsSelf。
     */
    Class<?> value() default Object.class;

    /**
     * 标记返回的是副本而非 this 本身。
     * 当为 true 时，桥接层不会将返回值替换为原始代理对象。
     */
    boolean copy() default false;
}
