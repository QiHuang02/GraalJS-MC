package cn.qihuang02.graaljs.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link ReturnsSelf} 的容器注解，支持 {@code @Repeatable}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ReturnsSelfContainer {
    ReturnsSelf[] value();
}
