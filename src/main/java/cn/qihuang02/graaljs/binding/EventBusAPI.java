package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.Graaljs;
import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文内事件总线，为后续 Minecraft 事件桥接提供统一入口。
 */
public class EventBusAPI {
    private final GraaljsContext context;
    private final Map<String, List<Listener>> listeners;
    private ForgeEventBridge forgeBridge;

    public EventBusAPI(GraaljsContext context) {
        this.context = context;
        this.listeners = new LinkedHashMap<>();
    }

    /**
     * 设置 Forge 事件桥接器。
     */
    public void setForgeBridge(ForgeEventBridge bridge) {
        this.forgeBridge = bridge;
    }

    /**
     * 获取 Forge 事件桥接器。
     */
    public ForgeEventBridge getForgeBridge() {
        return forgeBridge;
    }

    public int on(String eventName, Value callback) {
        return addListener(eventName, callback, false);
    }

    public int once(String eventName, Value callback) {
        return addListener(eventName, callback, true);
    }

    public int off(String eventName, Value callback) {
        List<Listener> eventListeners = listeners.get(eventName);
        if (eventListeners == null || eventListeners.isEmpty()) {
            return 0;
        }

        int removed = 0;
        Iterator<Listener> iterator = eventListeners.iterator();
        while (iterator.hasNext()) {
            Listener listener = iterator.next();
            if (listener.callback().equals(callback)) {
                iterator.remove();
                removed++;
            }
        }

        if (eventListeners.isEmpty()) {
            listeners.remove(eventName);
        }
        return removed;
    }

    public int emit(String eventName, Object payload) {
        return emitInternal(eventName, payload);
    }

    public int emitHost(String eventName, Object payload) {
        return emitInternal(eventName, payload);
    }

    public boolean clear(String eventName) {
        return listeners.remove(eventName) != null;
    }

    public void clearAll() {
        listeners.clear();
    }

    public int listenerCount(String eventName) {
        List<Listener> callbacks = listeners.get(eventName);
        return callbacks == null ? 0 : callbacks.size();
    }

    /**
     * 注册 Forge 事件监听器。
     *
     * @param eventName Forge 事件名（需先通过 ForgeEventBridge 注册映射）
     * @param callback  JS 回调函数
     * @return 当前事件名下的监听器数量
     */
    public int onForge(String eventName, Value callback) {
        requireForgeBridge();
        return forgeBridge.subscribe(eventName, callback);
    }

    /**
     * 移除 Forge 事件监听器。
     *
     * @return 移除的监听器数量
     */
    public int offForge(String eventName, Value callback) {
        requireForgeBridge();
        return forgeBridge.unsubscribe(eventName, callback);
    }

    /**
     * 返回所有可用的 Forge 事件名。
     */
    public String[] forgeEvents() {
        if (forgeBridge == null) {
            return new String[0];
        }
        return forgeBridge.availableEvents();
    }

    /**
     * 返回指定 Forge 事件名下的活跃监听器数量。
     */
    public int forgeListenerCount(String eventName) {
        if (forgeBridge == null) {
            return 0;
        }
        return forgeBridge.listenerCount(eventName);
    }

    /**
     * 关闭 Forge 事件桥接器，注销所有 Forge 事件监听器。
     */
    public void closeForgeBridge() {
        if (forgeBridge != null) {
            forgeBridge.close();
        }
    }

    private int addListener(String eventName, Value callback, boolean once) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be blank");
        }
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Event callback must be executable");
        }
        String sourceName = resolveSourceName();
        listeners.computeIfAbsent(eventName, ignored -> new ArrayList<>()).add(new Listener(callback, once, sourceName));
        return listeners.get(eventName).size();
    }

    private int emitInternal(String eventName, Object payload) {
        int delivered = dispatch(eventName, payload);
        if (!"*".equals(eventName)) {
            delivered += dispatch("*", createWildcardPayload(eventName, payload));
        }
        return delivered;
    }

    private int dispatch(String eventName, Object payload) {
        List<Listener> eventListeners = listeners.get(eventName);
        if (eventListeners == null || eventListeners.isEmpty()) {
            return 0;
        }

        Object jsPayload = context.javaToJs(payload);
        int delivered = 0;
        Iterator<Listener> iterator = eventListeners.iterator();
        while (iterator.hasNext()) {
            Listener listener = iterator.next();
            try {
                listener.callback().execute(jsPayload);
            } catch (Exception e) {
                String source = listener.sourceName() != null ? listener.sourceName() : "unknown";
                Graaljs.LOGGER.error("Event listener error for '{}' (registered in {}): {}", eventName, source, e.getMessage(), e);
            }
            delivered++;
            if (listener.once()) {
                iterator.remove();
            }
        }

        if (eventListeners.isEmpty()) {
            listeners.remove(eventName);
        }
        return delivered;
    }

    private static Object createWildcardPayload(String eventName, Object payload) {
        if (payload instanceof Map<?, ?> map && map.containsKey("name") && map.containsKey("payload")) {
            return payload;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("name", eventName);
        envelope.put("payload", payload);
        return envelope;
    }

    private record Listener(Value callback, boolean once, String sourceName) {
    }

    private void requireForgeBridge() {
        if (forgeBridge == null) {
            throw new IllegalStateException("Forge event bridge is not configured for this context");
        }
    }

    /**
     * 从 ModuleLoader 的脚本路径栈获取当前注册来源。
     */
    private String resolveSourceName() {
        if (context.getModuleLoader() != null) {
            java.nio.file.Path path = context.getModuleLoader().currentScriptPath();
            if (path != null) {
                return path.toString();
            }
        }
        return null;
    }
}
