package cn.qihuang02.graaljs.bridge;

/**
 * 缓存的成员信息基础接口。
 */
public sealed interface CachedMemberInfo permits CachedFieldInfo, CachedMethodGroupInfo, CachedBeanPropertyInfo, CachedConstructorGroupInfo {
    String name();
}
