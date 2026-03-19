package cn.qihuang02.graaljs.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法返回 {@code this}（即返回调用者自身）。
 * 桥接层在 {@link cn.qihuang02.graaljs.bridge.JavaMethodProxy} 中识别此注解，
 * 返回值直接返回原始代理对象而非重新包装，优化链式调用。
 *
 * <pre>{@code
 * public class Builder {
 *     @ReturnsSelf
 *     public Builder withName(String name) {
 *         this.name = name;
 *         return this;
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReturnsSelf {
}
