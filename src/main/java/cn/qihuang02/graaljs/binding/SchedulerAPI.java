package cn.qihuang02.graaljs.binding;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.graalvm.polyglot.Value;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 提供 setTimeout / setInterval / clearTimeout / clearInterval 定时调度能力。
 * <p>
 * 所有回调在 {@link #tick(long)} 被调用时同步执行，适合游戏主循环驱动。
 */
public class SchedulerAPI {
    private final GraaljsContext context;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private long currentTimeMs = 0L;

    public SchedulerAPI(GraaljsContext context) {
        this.context = context;
    }

    /**
     * 注册一次性延迟回调。
     *
     * @return 任务 ID，可用于 {@link #clearTimeout(int)}
     */
    public int setTimeout(Value callback, long delayMs) {
        validateCallback(callback);
        if (delayMs < 0) {
            delayMs = 0;
        }
        int id = nextId.getAndIncrement();
        tasks.put(id, new ScheduledTask(id, callback, delayMs, 0, currentTimeMs + delayMs, false, resolveSourceName()));
        return id;
    }

    /**
     * 注册周期性回调。
     *
     * @return 任务 ID，可用于 {@link #clearInterval(int)}
     */
    public int setInterval(Value callback, long intervalMs) {
        validateCallback(callback);
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        int id = nextId.getAndIncrement();
        tasks.put(id, new ScheduledTask(id, callback, intervalMs, intervalMs, currentTimeMs + intervalMs, false, resolveSourceName()));
        return id;
    }

    /**
     * 取消由 {@link #setTimeout} 创建的任务。
     *
     * @return 是否成功取消（任务存在且未执行）
     */
    public boolean clearTimeout(int id) {
        return cancel(id);
    }

    /**
     * 取消由 {@link #setInterval} 创建的任务。
     *
     * @return 是否成功取消
     */
    public boolean clearInterval(int id) {
        return cancel(id);
    }

    /**
     * 推进调度器时间并执行到期的回调。
     *
     * @param currentTimeMs 当前时间（毫秒），通常由游戏循环提供
     * @return 本次 tick 中执行的回调数量
     */
    public int tick(long currentTimeMs) {
        this.currentTimeMs = currentTimeMs;
        int fired = 0;
        Iterator<Map.Entry<Integer, ScheduledTask>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ScheduledTask> entry = iterator.next();
            ScheduledTask task = entry.getValue();
            if (task.cancelled) {
                iterator.remove();
                continue;
            }
            if (currentTimeMs >= task.nextFireTimeMs) {
                try {
                    task.callback.execute();
                } catch (Exception e) {
                    String source = task.sourceName != null ? task.sourceName : "scheduler";
                    context.getFactory().getErrorReporter().error(
                            context,
                            "Scheduler callback error (id=" + task.id + "): " + e.getMessage(),
                            source, -1, "", -1, e
                    );
                }
                fired++;
                if (task.intervalMs > 0) {
                    task.nextFireTimeMs = currentTimeMs + task.intervalMs;
                } else {
                    iterator.remove();
                }
            }
        }
        return fired;
    }

    /**
     * 返回当前活跃（未取消、未过期的一次性 + 所有周期性）任务数量。
     */
    public int size() {
        return tasks.size();
    }

    /**
     * 清除所有已注册的任务。
     */
    public void close() {
        tasks.clear();
    }

    private boolean cancel(int id) {
        ScheduledTask task = tasks.remove(id);
        if (task != null) {
            task.cancelled = true;
            return true;
        }
        return false;
    }

    private static void validateCallback(Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
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

    private static final class ScheduledTask {
        final int id;
        final Value callback;
        final long delayMs;
        final long intervalMs;
        final String sourceName;
        long nextFireTimeMs;
        volatile boolean cancelled;

        ScheduledTask(int id, Value callback, long delayMs, long intervalMs, long nextFireTimeMs, boolean cancelled, String sourceName) {
            this.id = id;
            this.callback = callback;
            this.delayMs = delayMs;
            this.intervalMs = intervalMs;
            this.nextFireTimeMs = nextFireTimeMs;
            this.cancelled = cancelled;
            this.sourceName = sourceName;
        }
    }
}
