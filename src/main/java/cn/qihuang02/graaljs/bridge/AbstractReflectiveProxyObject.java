package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于反射描述符的通用对象代理。
 */
abstract class AbstractReflectiveProxyObject implements ProxyObject, ProxyValue {
    protected final GraaljsContext context;
    protected final CachedClassInfo cachedClassInfo;
    private Map<String, CustomMember> customMembers;

    protected AbstractReflectiveProxyObject(GraaljsContext context, Class<?> type) {
        this.context = context;
        this.cachedClassInfo = context.getFactory().getCachedClassStorage().get(type);
    }

    protected abstract Object target();

    protected abstract boolean staticOnly();

    protected boolean allowInstanceStaticFallback() {
        return false;
    }

    @Override
    public Object getMember(String key) {
        Map<String, CustomMember> customMembers = customMembers();
        CustomMember customMember = customMembers.get(key);
        if (customMember != null) {
            return context.javaToJs(customMember.value());
        }

        Field field = cachedClassInfo.findField(key, staticOnly());
        Object fieldTarget = target();
        if (field == null && allowInstanceStaticFallback()) {
            field = cachedClassInfo.findField(key, true);
            fieldTarget = null;
        }
        if (field != null) {
            try {
                return context.javaToJs(field.get(fieldTarget));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to access field: " + field, exception);
            }
        }

        List<java.lang.reflect.Method> methods = cachedClassInfo.findMethods(key, staticOnly());
        if ((methods == null || methods.isEmpty()) && allowInstanceStaticFallback()) {
            methods = cachedClassInfo.findMethods(key, true);
        }
        if (methods != null && !methods.isEmpty()) {
            return new JavaMethodProxy(context, target(), methods);
        }

        return null;
    }

    @Override
    public Object getMemberKeys() {
        Map<String, CustomMember> customMembers = customMembers();
        Set<String> keys = new java.util.LinkedHashSet<>(cachedClassInfo.memberKeys(staticOnly()));
        if (allowInstanceStaticFallback()) {
            keys.addAll(cachedClassInfo.memberKeys(true));
        }
        keys.addAll(customMembers.keySet());
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        Map<String, CustomMember> customMembers = customMembers();
        return customMembers.containsKey(key)
                || cachedClassInfo.findField(key, staticOnly()) != null
                || cachedClassInfo.findMethods(key, staticOnly()) != null
                || allowInstanceStaticFallback() && (
                cachedClassInfo.findField(key, true) != null
                        || cachedClassInfo.findMethods(key, true) != null
        );
    }

    @Override
    public void putMember(String key, Value value) {
        Map<String, CustomMember> customMembers = customMembers();
        CustomMember customMember = customMembers.get(key);
        if (customMember != null) {
            customMembers.put(key, new CustomMember(customMember.name(), customMember.type(), context.jsToJava(value, customMember.type())));
            return;
        }

        Field field = cachedClassInfo.findField(key, staticOnly());
        Object fieldTarget = target();
        if (field == null && allowInstanceStaticFallback()) {
            field = cachedClassInfo.findField(key, true);
            fieldTarget = null;
        }
        if (field == null) {
            throw new UnsupportedOperationException("Unknown member: " + key);
        }

        try {
            field.set(fieldTarget, context.jsToJava(value, field.getType()));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to set field: " + field, exception);
        }
    }

    @Override
    public boolean removeMember(String key) {
        return customMembers().remove(key) != null;
    }

    private Map<String, CustomMember> collectCustomMembers(Object target) {
        Map<String, CustomMember> map = new LinkedHashMap<>();
        if (target instanceof CustomMemberProvider provider) {
            Collection<CustomMember> members = provider.getCustomMembers();
            if (members != null) {
                for (CustomMember member : members) {
                    if (member != null && member.name() != null && !member.name().isBlank()) {
                        map.put(member.name(), member);
                    }
                }
            }
        }
        return map;
    }

    private Map<String, CustomMember> customMembers() {
        if (customMembers == null) {
            customMembers = collectCustomMembers(target());
        }
        return customMembers;
    }
}
