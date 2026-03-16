package cn.qihuang02.graaljs.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link RemapPrefixForJS} 的重复注解容器。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RemapPrefixForJSContainer {
    RemapPrefixForJS[] value();
}
