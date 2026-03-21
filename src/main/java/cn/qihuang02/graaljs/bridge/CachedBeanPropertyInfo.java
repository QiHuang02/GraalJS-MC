package cn.qihuang02.graaljs.bridge;

import java.lang.reflect.Method;

/**
 * 缓存的 Bean 属性信息（getter/setter 对）。
 */
public record CachedBeanPropertyInfo(String name, Method getter, Method setter, Class<?> type, boolean isStatic) implements CachedMemberInfo {
}
