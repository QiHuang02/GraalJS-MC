package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.util.HideFromJS;
import cn.qihuang02.graaljs.util.ClassVisibilityContext;
import cn.qihuang02.graaljs.util.RemapForJS;
import cn.qihuang02.graaljs.util.RemapPrefixForJS;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单个类型的可见成员缓存。
 */
public final class CachedClassInfo {

    /**
     * 合成的 Bean 属性（getter/setter 对）。
     */
    public record BeanProperty(String name, Method getter, Method setter, Class<?> type) {
    }

    private final CachedClassStorage storage;
    private final Class<?> type;
    private final Map<String, Field> instanceFields;
    private final Map<String, Field> staticFields;
    private final Map<String, List<Method>> instanceMethods;
    private final Map<String, List<Method>> staticMethods;
    private final Map<String, BeanProperty> instanceBeanProperties;
    private final Map<String, BeanProperty> staticBeanProperties;
    private final List<Constructor<?>> constructors;
    private final Set<String> instanceMemberKeys;
    private final Set<String> staticMemberKeys;
    private final boolean hidden;

    CachedClassInfo(CachedClassStorage storage, Class<?> type) {
        this.storage = storage;
        this.type = type;
        this.instanceFields = new LinkedHashMap<>();
        this.staticFields = new LinkedHashMap<>();
        this.instanceMethods = new LinkedHashMap<>();
        this.staticMethods = new LinkedHashMap<>();
        this.instanceBeanProperties = new LinkedHashMap<>();
        this.staticBeanProperties = new LinkedHashMap<>();
        this.constructors = new ArrayList<>();
        this.hidden = isHidden(type);

        if (!hidden) {
            collectFields(type.getFields());
            collectMethods(type.getMethods());
            synthesizeBeanProperties();
            collectConstructors(type.getConstructors());
        }

        this.instanceMemberKeys = Set.copyOf(joinKeys(
                instanceFields.keySet(), instanceMethods.keySet(), instanceBeanProperties.keySet()));
        this.staticMemberKeys = Set.copyOf(joinKeys(
                staticFields.keySet(), staticMethods.keySet(), staticBeanProperties.keySet()));
    }

    public Class<?> type() {
        return type;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Field findField(String name, boolean staticOnly) {
        return staticOnly ? staticFields.get(name) : instanceFields.get(name);
    }

    public List<Method> findMethods(String name, boolean staticOnly) {
        return staticOnly ? staticMethods.get(name) : instanceMethods.get(name);
    }

    public BeanProperty findBeanProperty(String name, boolean staticOnly) {
        return staticOnly ? staticBeanProperties.get(name) : instanceBeanProperties.get(name);
    }

    public Set<String> memberKeys(boolean staticOnly) {
        return staticOnly ? staticMemberKeys : instanceMemberKeys;
    }

    public List<Constructor<?>> constructors() {
        return List.copyOf(constructors);
    }

    /**
     * 返回此类型的调试信息摘要。
     */
    public List<String> getDebugInfo() {
        List<String> info = new ArrayList<>();
        info.add("type: " + type.getName());
        if (hidden) {
            info.add("hidden: true");
            return info;
        }
        if (!instanceFields.isEmpty()) {
            info.add("fields: " + String.join(", ", instanceFields.keySet()));
        }
        if (!staticFields.isEmpty()) {
            info.add("staticFields: " + String.join(", ", staticFields.keySet()));
        }
        if (!instanceMethods.isEmpty()) {
            info.add("methods: " + String.join(", ", instanceMethods.keySet()));
        }
        if (!staticMethods.isEmpty()) {
            info.add("staticMethods: " + String.join(", ", staticMethods.keySet()));
        }
        if (!instanceBeanProperties.isEmpty()) {
            info.add("beanProperties: " + String.join(", ", instanceBeanProperties.keySet()));
        }
        if (!constructors.isEmpty()) {
            info.add("constructors: " + constructors.size());
        }
        return info;
    }

    private void collectFields(Field[] fields) {
        for (Field field : fields) {
            if (!include(field) || isHidden(field) || !storage.visibleToScripts(field.getType(), ClassVisibilityContext.MEMBER)) {
                continue;
            }
            field.setAccessible(true);
            Map<String, Field> target = Modifier.isStatic(field.getModifiers()) ? staticFields : instanceFields;
            addFieldName(target, field, field.getName());

            RemapForJS remap = field.getAnnotation(RemapForJS.class);
            if (remap != null && !remap.value().isBlank()) {
                addFieldName(target, field, remap.value().trim());
            }
        }
    }

    private void collectMethods(Method[] methods) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (RemapPrefixForJS prefix : type.getAnnotationsByType(RemapPrefixForJS.class)) {
            if (!prefix.value().isBlank()) {
                prefixes.add(prefix.value().trim());
            }
        }

        for (Method method : methods) {
            if (!include(method)
                    || method.getDeclaringClass() == Object.class
                    || isHidden(method)
                    || !storage.visibleToScripts(method.getReturnType(), ClassVisibilityContext.RETURN_TYPE)
                    || !allParametersVisible(method)) {
                continue;
            }

            method.setAccessible(true);
            Map<String, List<Method>> target = Modifier.isStatic(method.getModifiers()) ? staticMethods : instanceMethods;
            addMethodName(target, method, method.getName());

            RemapForJS remap = method.getAnnotation(RemapForJS.class);
            if (remap != null && !remap.value().isBlank()) {
                addMethodName(target, method, remap.value().trim());
            }

            for (String prefix : prefixes) {
                String alias = createPrefixAlias(method, prefix);
                if (alias != null) {
                    addMethodName(target, method, alias);
                }
            }
        }
    }

    private void collectConstructors(Constructor<?>[] constructors) {
        for (Constructor<?> constructor : constructors) {
            if (!include(constructor) || constructor.isAnnotationPresent(HideFromJS.class) || !allParametersVisible(constructor)) {
                continue;
            }
            constructor.setAccessible(true);
            this.constructors.add(constructor);
        }
    }

    /**
     * 从已收集的方法中合成 Bean 属性。
     * <p>识别 getXxx()/isXxx() (无参、有返回值) 和 setXxx(T) (单参、void) 模式。
     * <p>跳过条件：属性名与已有 field 同名、属性名与方法别名冲突。
     */
    private void synthesizeBeanProperties() {
        synthesizeBeanPropertiesFor(instanceMethods, instanceFields, instanceBeanProperties);
        synthesizeBeanPropertiesFor(staticMethods, staticFields, staticBeanProperties);
    }

    private void synthesizeBeanPropertiesFor(
            Map<String, List<Method>> methods,
            Map<String, Field> fields,
            Map<String, BeanProperty> target) {

        // 收集所有原始方法（按原始名分组）
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, Method> isGetters = new LinkedHashMap<>();
        Map<String, Method> setters = new LinkedHashMap<>();

        for (Map.Entry<String, List<Method>> entry : methods.entrySet()) {
            for (Method method : entry.getValue()) {
                String name = method.getName();
                if (name.length() > 3 && name.startsWith("get")
                        && method.getParameterCount() == 0
                        && method.getReturnType() != void.class) {
                    String propName = decapitalize(name.substring(3));
                    if (propName != null) {
                        getters.putIfAbsent(propName, method);
                    }
                } else if (name.length() > 2 && name.startsWith("is")
                        && method.getParameterCount() == 0
                        && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    String propName = decapitalize(name.substring(2));
                    if (propName != null) {
                        isGetters.putIfAbsent(propName, method);
                    }
                } else if (name.length() > 3 && name.startsWith("set")
                        && method.getParameterCount() == 1
                        && method.getReturnType() == void.class) {
                    String propName = decapitalize(name.substring(3));
                    if (propName != null) {
                        setters.putIfAbsent(propName, method);
                    }
                }
            }
        }

        // 合并 getter 和 isGetter（get 优先）
        Set<String> allPropNames = new LinkedHashSet<>(getters.keySet());
        allPropNames.addAll(isGetters.keySet());
        allPropNames.addAll(setters.keySet());

        for (String propName : allPropNames) {
            // 跳过：与已有 field 同名
            if (fields.containsKey(propName)) {
                continue;
            }
            // 跳过：与已有方法别名冲突（说明 @RemapPrefixForJS 或 @RemapForJS 已注册了同名方法）
            if (methods.containsKey(propName)) {
                continue;
            }

            Method getter = getters.get(propName);
            if (getter == null) {
                getter = isGetters.get(propName);
            }
            Method setter = setters.get(propName);

            // 至少有 getter 或 setter
            if (getter == null && setter == null) {
                continue;
            }

            Class<?> propType = getter != null ? getter.getReturnType() : setter.getParameterTypes()[0];
            target.put(propName, new BeanProperty(propName, getter, setter, propType));
        }
    }

    private static String decapitalize(String suffix) {
        if (suffix.isEmpty()) {
            return null;
        }
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private boolean include(Member member) {
        int modifiers = member.getModifiers();
        return Modifier.isPublic(modifiers) || storage.includeProtected() && Modifier.isProtected(modifiers);
    }

    private boolean allParametersVisible(java.lang.reflect.Executable executable) {
        for (Class<?> parameterType : executable.getParameterTypes()) {
            if (!storage.visibleToScripts(parameterType, ClassVisibilityContext.ARGUMENT)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> joinKeys(Collection<String> fields, Collection<String> methods, Collection<String> beanProperties) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(fields);
        keys.addAll(methods);
        keys.addAll(beanProperties);
        return keys;
    }

    private static void addFieldName(Map<String, Field> target, Field field, String name) {
        if (!name.isBlank()) {
            target.putIfAbsent(name, field);
        }
    }

    private static void addMethodName(Map<String, List<Method>> target, Method method, String name) {
        if (!name.isBlank()) {
            target.computeIfAbsent(name, key -> new ArrayList<>()).add(method);
        }
    }

    private static String createPrefixAlias(Method method, String prefix) {
        if (method.getParameterCount() != 0
                || !method.getName().startsWith(prefix)
                || method.getName().length() <= prefix.length()) {
            return null;
        }
        String suffix = method.getName().substring(prefix.length());
        if (suffix.isBlank()) {
            return null;
        }
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static boolean isHidden(Member member) {
        return member instanceof Field field && field.isAnnotationPresent(HideFromJS.class)
                || member instanceof Method method && method.isAnnotationPresent(HideFromJS.class);
    }

    private static boolean isHidden(Class<?> type) {
        return type.isAnnotationPresent(HideFromJS.class)
                || type.getPackage() != null && type.getPackage().isAnnotationPresent(HideFromJS.class);
    }
}
