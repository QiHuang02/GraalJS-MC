package cn.qihuang02.graaljs.bridge;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 缓存的方法组信息（同名方法的重载集合）。
 */
public record CachedMethodGroupInfo(String name, List<Method> methods, boolean isStatic) implements CachedMemberInfo {
}
