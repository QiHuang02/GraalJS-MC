package cn.qihuang02.graaljs.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按前缀批量生成脚本侧别名。
 */
@Documented
@Repeatable(RemapPrefixForJSContainer.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RemapPrefixForJS {
    String value();
}
