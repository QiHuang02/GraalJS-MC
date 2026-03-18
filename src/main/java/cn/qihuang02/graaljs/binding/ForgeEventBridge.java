package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.Graaljs;
import cn.qihuang02.graaljs.core.GraaljsContext;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 将 Forge 事件总线桥接到 JS 脚本。
 * <p>
 * 脚本通过 {@code events.onForge("eventName", callback)} 注册监听器，
 * 当对应的 Forge 事件触发时，事件对象会通过桥接层传递给 JS 回调。
 */
public class ForgeEventBridge {
    private final GraaljsContext context;
    private final IEventBus eventBus;
    private final Map<String, ForgeEventMapping<?>> registry;
    private final Map<String, List<ForgeListener>> activeListeners;

    public ForgeEventBridge(GraaljsContext context, IEventBus eventBus) {
        this.context = context;
        this.eventBus = eventBus;
        this.registry = new ConcurrentHashMap<>();
        this.activeListeners = new LinkedHashMap<>();
    }

    /**
     * 注册事件名到 Forge Event 类的映射。
     *
     * @param eventName  JS 侧使用的事件名（如 "player.join"）
     * @param eventClass 对应的 Forge Event 类
     */
    public <T extends Event> void registerMapping(String eventName, Class<T> eventClass) {
        registerMapping(eventName, eventClass, EventPriority.NORMAL);
    }

    /**
     * 注册事件名到 Forge Event 类的映射，指定优先级。
     */
    public <T extends Event> void registerMapping(String eventName, Class<T> eventClass, EventPriority priority) {
        registry.put(eventName, new ForgeEventMapping<>(eventClass, priority));
    }

    /**
     * 注册一个 JS 回调监听指定的 Forge 事件。
     *
     * @param eventName JS 事件名
     * @param callback  JS 回调函数
     * @return 当前事件名下的监听器数量
     */
    public int subscribe(String eventName, Value callback) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be blank");
        }
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }

        ForgeEventMapping<?> mapping = registry.get(eventName);
        if (mapping == null) {
            throw new IllegalArgumentException("Unknown Forge event: " + eventName
                    + ". Use events.forgeEvents() to list available events.");
        }

        ForgeListener listener = createListener(eventName, mapping, callback);
        activeListeners.computeIfAbsent(eventName, k -> new ArrayList<>()).add(listener);
        return activeListeners.get(eventName).size();
    }

    /**
     * 移除指定事件名下匹配的 JS 回调。
     *
     * @return 移除的监听器数量
     */
    public int unsubscribe(String eventName, Value callback) {
        List<ForgeListener> listeners = activeListeners.get(eventName);
        if (listeners == null || listeners.isEmpty()) {
            return 0;
        }

        int removed = 0;
        Iterator<ForgeListener> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            ForgeListener listener = iterator.next();
            if (listener.callback.equals(callback)) {
                eventBus.unregister(listener.forgeConsumer);
                iterator.remove();
                removed++;
            }
        }

        if (listeners.isEmpty()) {
            activeListeners.remove(eventName);
        }
        return removed;
    }

    /**
     * 返回所有已注册的 Forge 事件名。
     */
    public String[] availableEvents() {
        return registry.keySet().toArray(String[]::new);
    }

    /**
     * 返回指定事件名下的活跃监听器数量。
     */
    public int listenerCount(String eventName) {
        List<ForgeListener> listeners = activeListeners.get(eventName);
        return listeners == null ? 0 : listeners.size();
    }

    /**
     * 关闭桥接器，注销所有 Forge 事件监听器。
     */
    public void close() {
        for (List<ForgeListener> listeners : activeListeners.values()) {
            for (ForgeListener listener : listeners) {
                eventBus.unregister(listener.forgeConsumer);
            }
        }
        activeListeners.clear();
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> ForgeListener createListener(String eventName, ForgeEventMapping<T> mapping, Value callback) {
        String sourceName = resolveSourceName();
        Consumer<T> consumer = event -> {
            try {
                Object jsEvent = context.javaToJs(event);
                Value result = callback.execute(jsEvent);
                // 事件取消：JS 回调返回 false 或 { cancelled: true }
                if (event.isCancelable()) {
                    if (result != null) {
                        if (result.isBoolean() && !result.asBoolean()) {
                            event.setCanceled(true);
                        } else if (result.hasMembers() && result.hasMember("cancelled")) {
                            Value cancelledVal = result.getMember("cancelled");
                            if (cancelledVal != null && cancelledVal.isBoolean() && cancelledVal.asBoolean()) {
                                event.setCanceled(true);
                            }
                        }
                    }
                }
                // 事件结果回写：JS 回调返回 "allow" / "deny" / "default"
                if (event.hasResult() && result != null && result.isString()) {
                    String resultStr = result.asString();
                    switch (resultStr) {
                        case "allow" -> event.setResult(Event.Result.ALLOW);
                        case "deny" -> event.setResult(Event.Result.DENY);
                        case "default" -> event.setResult(Event.Result.DEFAULT);
                    }
                }
            } catch (Exception e) {
                String source = sourceName != null ? sourceName : "unknown";
                Graaljs.LOGGER.error("Forge event listener error for '{}' (registered in {}): {}", eventName, source, e.getMessage(), e);
            }
        };

        eventBus.addListener(mapping.priority(), false, mapping.eventClass(), consumer);
        return new ForgeListener(callback, (Consumer<? extends Event>) consumer, sourceName);
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

    private record ForgeEventMapping<T extends Event>(Class<T> eventClass, EventPriority priority) {
    }

    private record ForgeListener(Value callback, Consumer<? extends Event> forgeConsumer, String sourceName) {
    }
}
