package cn.qihuang02.graaljs.bridge;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * 缓存的构造函数组信息。
 */
public record CachedConstructorGroupInfo(String name, List<Constructor<?>> constructors) implements CachedMemberInfo {
    public CachedConstructorGroupInfo(List<Constructor<?>> constructors) {
        this("<init>", constructors);
    }
}
