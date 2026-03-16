package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为 Map 提供对象式访问。
 */
public class JavaMapProxy implements ProxyObject, ProxyValue {
    private final GraaljsContext context;
    private final Map<Object, Object> map;

    @SuppressWarnings("unchecked")
    public JavaMapProxy(GraaljsContext context, Map<?, ?> source) {
        this.context = context;
        this.map = (Map<Object, Object>) source;
    }

    @Override
    public Object getMember(String key) {
        return context.javaToJs(map.get(key));
    }

    @Override
    public Object getMemberKeys() {
        return map.keySet().stream().map(String::valueOf).toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return map.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        map.put(key, context.jsToJava(value, Object.class));
    }

    @Override
    public boolean removeMember(String key) {
        return map.remove(key) != null;
    }

    @Override
    public Object unwrap() {
        return map;
    }
}
