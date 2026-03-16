package cn.qihuang02.graaljs.util;

/**
 * 标记类可见性校验发生的上下文。
 */
public enum ClassVisibilityContext {
    UNKNOWN,
    BINDING,
    CLASS_LOOKUP,
    PACKAGE_LOOKUP,
    MEMBER,
    ARGUMENT,
    RETURN_TYPE,
    EXCEPTION,
    ADAPTER_INTERFACE,
    ADAPTER_SUPER
}
