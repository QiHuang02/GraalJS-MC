package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.util.ClassVisibilityContext;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 反射缓存根节点。
 */
public class CachedClassStorage {
    public static final CachedClassStorage GLOBAL_PUBLIC = new CachedClassStorage(false, (type, context) -> true);
    public static final CachedClassStorage GLOBAL_PROTECTED = new CachedClassStorage(true, (type, context) -> true);

    private final Map<Class<?>, CachedClassInfo> cache;
    private final boolean includeProtected;
    private final VisibilityFilter visibilityFilter;

    public CachedClassStorage(boolean includeProtected, VisibilityFilter visibilityFilter) {
        this.cache = new IdentityHashMap<>();
        this.includeProtected = includeProtected;
        this.visibilityFilter = visibilityFilter;
    }

    public synchronized CachedClassInfo get(Class<?> type) {
        return cache.computeIfAbsent(type, key -> new CachedClassInfo(this, key));
    }

    public boolean includeProtected() {
        return includeProtected;
    }

    public boolean visibleToScripts(Class<?> type, ClassVisibilityContext context) {
        return visibilityFilter.visible(type, context);
    }

    @FunctionalInterface
    public interface VisibilityFilter {
        boolean visible(Class<?> type, ClassVisibilityContext context);
    }
}
