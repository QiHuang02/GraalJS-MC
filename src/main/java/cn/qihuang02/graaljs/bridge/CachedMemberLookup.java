package cn.qihuang02.graaljs.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成员查找表，提供按名称的高效成员解析。
 * <p>
 * 内部维护实例成员和静态成员的分离索引。
 */
public final class CachedMemberLookup {
    private final Map<String, CachedFieldInfo> instanceFields;
    private final Map<String, CachedFieldInfo> staticFields;
    private final Map<String, CachedMethodGroupInfo> instanceMethods;
    private final Map<String, CachedMethodGroupInfo> staticMethods;
    private final Map<String, CachedBeanPropertyInfo> instanceBeanProperties;
    private final Map<String, CachedBeanPropertyInfo> staticBeanProperties;
    private final CachedConstructorGroupInfo constructorGroup;
    private final Set<String> instanceMemberKeys;
    private final Set<String> staticMemberKeys;

    CachedMemberLookup(
            Map<String, Field> instanceFieldMap,
            Map<String, Field> staticFieldMap,
            Map<String, List<Method>> instanceMethodMap,
            Map<String, List<Method>> staticMethodMap,
            Map<String, CachedClassInfo.BeanProperty> instanceBeanPropertyMap,
            Map<String, CachedClassInfo.BeanProperty> staticBeanPropertyMap,
            List<Constructor<?>> constructors) {

        this.instanceFields = toFieldInfoMap(instanceFieldMap, false);
        this.staticFields = toFieldInfoMap(staticFieldMap, true);
        this.instanceMethods = toMethodGroupMap(instanceMethodMap, false);
        this.staticMethods = toMethodGroupMap(staticMethodMap, true);
        this.instanceBeanProperties = toBeanPropertyMap(instanceBeanPropertyMap, false);
        this.staticBeanProperties = toBeanPropertyMap(staticBeanPropertyMap, true);
        this.constructorGroup = constructors.isEmpty() ? null : new CachedConstructorGroupInfo(List.copyOf(constructors));

        this.instanceMemberKeys = Set.copyOf(joinKeys(
                instanceFields.keySet(), instanceMethods.keySet(), instanceBeanProperties.keySet()));
        this.staticMemberKeys = Set.copyOf(joinKeys(
                staticFields.keySet(), staticMethods.keySet(), staticBeanProperties.keySet()));
    }

    public CachedFieldInfo findField(String name, boolean staticOnly) {
        return staticOnly ? staticFields.get(name) : instanceFields.get(name);
    }

    public CachedMethodGroupInfo findMethodGroup(String name, boolean staticOnly) {
        return staticOnly ? staticMethods.get(name) : instanceMethods.get(name);
    }

    public CachedBeanPropertyInfo findBeanProperty(String name, boolean staticOnly) {
        return staticOnly ? staticBeanProperties.get(name) : instanceBeanProperties.get(name);
    }

    public CachedConstructorGroupInfo constructorGroup() {
        return constructorGroup;
    }

    public Set<String> memberKeys(boolean staticOnly) {
        return staticOnly ? staticMemberKeys : instanceMemberKeys;
    }

    private static Map<String, CachedFieldInfo> toFieldInfoMap(Map<String, Field> raw, boolean isStatic) {
        if (raw.isEmpty()) return Map.of();
        Map<String, CachedFieldInfo> result = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, Field> entry : raw.entrySet()) {
            result.put(entry.getKey(), new CachedFieldInfo(entry.getKey(), entry.getValue(), isStatic));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, CachedMethodGroupInfo> toMethodGroupMap(Map<String, List<Method>> raw, boolean isStatic) {
        if (raw.isEmpty()) return Map.of();
        Map<String, CachedMethodGroupInfo> result = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, List<Method>> entry : raw.entrySet()) {
            result.put(entry.getKey(), new CachedMethodGroupInfo(entry.getKey(), List.copyOf(entry.getValue()), isStatic));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, CachedBeanPropertyInfo> toBeanPropertyMap(Map<String, CachedClassInfo.BeanProperty> raw, boolean isStatic) {
        if (raw.isEmpty()) return Map.of();
        Map<String, CachedBeanPropertyInfo> result = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, CachedClassInfo.BeanProperty> entry : raw.entrySet()) {
            CachedClassInfo.BeanProperty bp = entry.getValue();
            result.put(entry.getKey(), new CachedBeanPropertyInfo(bp.name(), bp.getter(), bp.setter(), bp.type(), isStatic));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> joinKeys(Set<String> fields, Set<String> methods, Set<String> beanProperties) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(fields);
        keys.addAll(methods);
        keys.addAll(beanProperties);
        return keys;
    }
}
