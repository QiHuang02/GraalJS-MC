package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.bridge.ProxyValue;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JS Value 到 Java 对象的标准化工具。
 * <p>
 * 将 GraalVM {@link Value} 递归展开为 Java 原生类型（String、Number、Boolean、List、Map 等）。
 */
public final class JsValueNormalizer {

    private JsValueNormalizer() {}

    /**
     * 将 JS Value 标准化为 Java 对象。
     * <ul>
     *   <li>null / isNull → null</li>
     *   <li>ProxyObject(ProxyValue) → unwrap</li>
     *   <li>HostObject → asHostObject</li>
     *   <li>String/Boolean/Number → 对应 Java 类型</li>
     *   <li>Array → List&lt;Object&gt;</li>
     *   <li>Object with members → Map&lt;String, Object&gt;</li>
     * </ul>
     */
    public static Object normalize(Object from) {
        if (!(from instanceof Value value)) {
            return from;
        }
        if (value.isNull()) {
            return null;
        }
        if (value.isProxyObject()) {
            Object proxyObject = value.asProxyObject();
            if (proxyObject instanceof ProxyValue proxyValue) {
                return proxyValue.unwrap();
            }
            return proxyObject;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(normalize(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String memberKey : value.getMemberKeys()) {
                map.put(memberKey, normalize(value.getMember(memberKey)));
            }
            return map;
        }
        return value.as(Object.class);
    }
}
