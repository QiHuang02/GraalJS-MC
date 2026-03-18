package cn.qihuang02.graaljs;

import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.core.ScriptType;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Map;

@Mod(Graaljs.MODID)
public class Graaljs {
    public static final String MODID = "graaljs";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static GraaljsContextFactory factory;

    private final GraaljsContextFactory localFactory;

    public Graaljs() {
        LOGGER.info("GraalJS-MC initializing...");

        localFactory = new GraaljsContextFactory();
        factory = localFactory;
        localFactory.createAndLoad(ScriptType.STARTUP);
        localFactory.emitEvent(ScriptType.STARTUP, "game.startup", Map.of(
                "modId", MODID
        ));

        MinecraftForge.EVENT_BUS.register(this);
    }

    public static GraaljsContextFactory getFactory() {
        return factory;
    }

    @SubscribeEvent
    public void onServerStarting(@NotNull ServerStartingEvent event) {
        localFactory.createAndLoad(ScriptType.SERVER);
        localFactory.emitEvent(ScriptType.SERVER, "server.starting", Map.of(
                "server", event.getServer()
        ));
    }

    @SubscribeEvent
    public void onServerStarted(@NotNull ServerStartedEvent event) {
        localFactory.emitEvent(ScriptType.SERVER, "server.started", Map.of(
                "server", event.getServer()
        ));
    }

    @SubscribeEvent
    public void onServerStopping(@NotNull ServerStoppingEvent event) {
        localFactory.emitEvent(ScriptType.SERVER, "server.stopping", Map.of(
                "server", event.getServer()
        ));
        localFactory.close(ScriptType.SERVER);
    }

    @SubscribeEvent
    public void onServerTick(@NotNull TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                localFactory.tickScheduler(ScriptType.SERVER, server.getTickCount() * 50L);
            }
        }
    }
}
