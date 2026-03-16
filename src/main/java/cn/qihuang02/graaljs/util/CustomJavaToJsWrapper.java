package cn.qihuang02.graaljs.util;

import cn.qihuang02.graaljs.core.GraaljsContext;

/**
 * 允许对象自定义自身的脚本侧包装结果。
 */
@FunctionalInterface
public interface CustomJavaToJsWrapper {
    Object convertJavaToJs(GraaljsContext context);
}
