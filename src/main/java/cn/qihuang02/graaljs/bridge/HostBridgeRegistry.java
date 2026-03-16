package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import cn.qihuang02.graaljs.util.CustomJavaToJsWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一维护 Java 到脚本值的桥接顺序。
 */
public class HostBridgeRegistry {
    private final List<HostBridge> bridges = new ArrayList<>();

    public HostBridgeRegistry() {
        addDefault(new HostBridge() {
            @Override
            public boolean supports(Object value) {
                return value instanceof CustomJavaToJsWrapper;
            }

            @Override
            public Object toJs(GraaljsContext context, Object value) {
                return ((CustomJavaToJsWrapper) value).convertJavaToJs(context);
            }
        });
        addDefault(new HostBridge() {
            @Override
            public boolean supports(Object value) {
                return value instanceof Map<?, ?>;
            }

            @Override
            public Object toJs(GraaljsContext context, Object value) {
                return new JavaMapProxy(context, (Map<?, ?>) value);
            }
        });
        addDefault(new HostBridge() {
            @Override
            public boolean supports(Object value) {
                return value instanceof List<?>;
            }

            @Override
            public Object toJs(GraaljsContext context, Object value) {
                return new JavaListProxy(context, (List<?>) value);
            }
        });
        addDefault(new HostBridge() {
            @Override
            public boolean supports(Object value) {
                return value instanceof Set<?>;
            }

            @Override
            public Object toJs(GraaljsContext context, Object value) {
                return new JavaSetProxy(context, (Set<?>) value);
            }
        });
        addDefault(new HostBridge() {
            @Override
            public boolean supports(Object value) {
                return value instanceof Class<?>;
            }

            @Override
            public Object toJs(GraaljsContext context, Object value) {
                return new JavaClassProxy(context, (Class<?>) value);
            }
        });
        addDefault(new HostBridge() {
            @Override
            public boolean supports(Object value) {
                return true;
            }

            @Override
            public Object toJs(GraaljsContext context, Object value) {
                return new JavaObjectProxy(context, value);
            }
        });
    }

    public void register(HostBridge bridge) {
        bridges.add(0, bridge);
    }

    public Object toJs(GraaljsContext context, Object value) {
        for (HostBridge bridge : bridges) {
            if (bridge.supports(value)) {
                return bridge.toJs(context, value);
            }
        }
        return value;
    }

    private void addDefault(HostBridge bridge) {
        bridges.add(bridge);
    }
}
