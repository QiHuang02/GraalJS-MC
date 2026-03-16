package cn.qihuang02.graaljs.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注此注解的方法或字段将在 JS 中以指定的名称暴露。
 * 通过 GraalVM 的 HostAccess.Export + 代理实现。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface RemapForJS {
    String value();
}
