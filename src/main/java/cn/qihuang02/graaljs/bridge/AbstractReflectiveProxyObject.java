package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 基于反射描述符的通用对象代理。
 */
abstract class AbstractReflectiveProxyObject implements ProxyObject, ProxyValue {
    protected final GraaljsContext context;
    protected final CachedClassInfo cachedClassInfo;
    protected final CachedMemberLookup lookup;
    private Map<String, CustomMember> customMembers;

    protected AbstractReflectiveProxyObject(GraaljsContext context, Class<?> type) {
        this.context = context;
        this.cachedClassInfo = context.getFactory().getCachedClassStorage().get(type);
        this.lookup = cachedClassInfo.getMemberLookup();
    }

    protected abstract Object target();

    protected abstract boolean staticOnly();

    protected boolean allowInstanceStaticFallback() {
        return false;
    }

    @Override
    public Object getMember(String key) {
        if (lookup == null) return null;

        Map<String, CustomMember> customMembers = customMembers();
        CustomMember customMember = customMembers.get(key);
        if (customMember != null) {
            Object value = customMember.value();
            if (value instanceof CustomProperty cp) {
                value = cp.get(context);
            }
            return context.javaToJs(value);
        }

        CachedFieldInfo fieldInfo = lookup.findField(key, staticOnly());
        Object fieldTarget = target();
        if (fieldInfo == null && allowInstanceStaticFallback()) {
            fieldInfo = lookup.findField(key, true);
            fieldTarget = null;
        }
        if (fieldInfo != null) {
            try {
                return context.javaToJs(fieldInfo.field().get(fieldTarget));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to access field: " + fieldInfo.name(), exception);
            }
        }

        // Bean property lookup (after field, before method)
        CachedBeanPropertyInfo beanProp = lookup.findBeanProperty(key, staticOnly());
        if (beanProp == null && allowInstanceStaticFallback()) {
            beanProp = lookup.findBeanProperty(key, true);
        }
        if (beanProp != null && beanProp.getter() != null) {
            try {
                Object result = beanProp.getter().invoke(target());
                return context.javaToJs(result);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Failed to invoke bean property getter: " + beanProp.name(), exception);
            }
        }

        CachedMethodGroupInfo methodGroup = lookup.findMethodGroup(key, staticOnly());
        if (methodGroup == null && allowInstanceStaticFallback()) {
            methodGroup = lookup.findMethodGroup(key, true);
        }
        if (methodGroup != null) {
            JavaMethodProxy proxy = new JavaMethodProxy(context, target(), methodGroup.methods());
            proxy.setOwnerProxy(this);
            return proxy;
        }

        return null;
    }

    @Override
    public Object getMemberKeys() {
        if (lookup == null) return new String[0];

        Map<String, CustomMember> customMembers = customMembers();
        Set<String> keys = new java.util.LinkedHashSet<>(lookup.memberKeys(staticOnly()));
        if (allowInstanceStaticFallback()) {
            keys.addAll(lookup.memberKeys(true));
        }
        keys.addAll(customMembers.keySet());
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        if (lookup == null) return false;

        Map<String, CustomMember> customMembers = customMembers();
        return customMembers.containsKey(key)
                || lookup.findField(key, staticOnly()) != null
                || lookup.findBeanProperty(key, staticOnly()) != null
                || lookup.findMethodGroup(key, staticOnly()) != null
                || allowInstanceStaticFallback() && (
                lookup.findField(key, true) != null
                        || lookup.findBeanProperty(key, true) != null
                        || lookup.findMethodGroup(key, true) != null
        );
    }

    @Override
    public void putMember(String key, Value value) {
        if (lookup == null) throw new UnsupportedOperationException("Unknown member: " + key);

        Map<String, CustomMember> customMembers = customMembers();
        CustomMember customMember = customMembers.get(key);
        if (customMember != null) {
            customMembers.put(key, new CustomMember(customMember.name(), customMember.type(), context.jsToJava(value, customMember.type())));
            return;
        }

        CachedFieldInfo fieldInfo = lookup.findField(key, staticOnly());
        Object fieldTarget = target();
        if (fieldInfo == null && allowInstanceStaticFallback()) {
            fieldInfo = lookup.findField(key, true);
            fieldTarget = null;
        }
        if (fieldInfo != null) {
            try {
                fieldInfo.field().set(fieldTarget, context.jsToJava(value, fieldInfo.field().getType()));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to set field: " + fieldInfo.name(), exception);
            }
            return;
        }

        // Bean property setter lookup
        CachedBeanPropertyInfo beanProp = lookup.findBeanProperty(key, staticOnly());
        if (beanProp == null && allowInstanceStaticFallback()) {
            beanProp = lookup.findBeanProperty(key, true);
        }
        if (beanProp != null) {
            if (beanProp.setter() == null) {
                throw new IllegalStateException("Read-only property: " + key);
            }
            try {
                beanProp.setter().invoke(target(), context.jsToJava(value, beanProp.setter().getParameterTypes()[0]));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Failed to invoke bean property setter: " + beanProp.name(), exception);
            }
            return;
        }

        throw new UnsupportedOperationException("Unknown member: " + key);
    }

    @Override
    public boolean removeMember(String key) {
        return customMembers().remove(key) != null;
    }

    // ── 外部注入 CustomMember ──

    /**
     * 动态注入自定义成员。允许外部代码（如 TypeWrapper）在包装后添加成员。
     */
    public void addCustomMember(CustomMember member) {
        if (member != null && member.name() != null && !member.name().isBlank()) {
            customMembers().put(member.name(), member);
        }
    }

    /**
     * 注入动态计算属性。
     */
    public void addCustomProperty(String name, Class<?> type, CustomProperty getter) {
        addCustomMember(new CustomMember(name, type, getter));
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

    // ── SpecialEquality / ToStringJS 钩子 ──

    @Override
    public boolean equals(Object obj) {
        Object self = target();
        if (self == null) {
            return obj == null;
        }
        Object other = obj;
        if (other instanceof ProxyValue pv) {
            other = pv.unwrap();
        }
        Boolean specialResult = SpecialEquality.checkSpecialEquality(context, self, other, false);
        if (specialResult != null) {
            return specialResult;
        }
        return self.equals(other);
    }

    @Override
    public int hashCode() {
        Object self = target();
        return self == null ? 0 : self.hashCode();
    }

    @Override
    public String toString() {
        Object self = target();
        if (self instanceof ToStringJS ts) {
            return ts.toStringJS();
        }
        return self == null ? "null" : self.toString();
    }
}
