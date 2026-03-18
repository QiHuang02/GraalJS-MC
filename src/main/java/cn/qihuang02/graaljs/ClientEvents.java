package cn.qihuang02.graaljs;

import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.core.ScriptType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * 客户端事件处理。
 * <p>
 * 使用 {@code Dist.CLIENT} 确保专用服务器不会加载此类。
 */
@Mod.EventBusSubscriber(modid = Graaljs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {
    private static boolean initialized = false;
    private static long clientTickCount = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        GraaljsContextFactory factory = Graaljs.getFactory();
        if (factory == null) {
            return;
        }

        // 首次客户端 tick 时创建并加载 CLIENT 上下文
        if (!initialized) {
            initialized = true;
            Graaljs.LOGGER.info("Initializing CLIENT script context");
            factory.createAndLoad(ScriptType.CLIENT);
            factory.emitEvent(ScriptType.CLIENT, "client.started", Map.of());
        }

        clientTickCount++;
        factory.tickScheduler(ScriptType.CLIENT, clientTickCount * 50L);
    }

    /**
     * 关闭客户端上下文。由 {@link GraaljsContextFactory#closeAll()} 或 mod 卸载时调用。
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        GraaljsContextFactory factory = Graaljs.getFactory();
        if (factory != null) {
            factory.emitEvent(ScriptType.CLIENT, "client.stopping", Map.of());
            factory.close(ScriptType.CLIENT);
        }
        initialized = false;
        clientTickCount = 0;
    }
}
