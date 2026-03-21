package cn.qihuang02.graaljs.bridge;

import java.lang.reflect.Field;

/**
 * 缓存的字段信息。
 */
public record CachedFieldInfo(String name, Field field, boolean isStatic) implements CachedMemberInfo {
}
