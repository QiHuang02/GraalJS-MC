package cn.qihuang02.graaljs;

import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.core.ScriptType;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Map;

@Mod(Graaljs.MODID)
public class Graaljs {
    public static final String MODID = "graaljs";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final GraaljsContextFactory factory;

    public Graaljs() {
        LOGGER.info("GraalJS-MC initializing...");

        factory = new GraaljsContextFactory();
        factory.createAndLoad(ScriptType.STARTUP);
        factory.emitEvent(ScriptType.STARTUP, "game.startup", Map.of(
                "modId", MODID
        ));

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        factory.createAndLoad(ScriptType.SERVER);
        factory.emitEvent(ScriptType.SERVER, "server.starting", Map.of(
                "server", event.getServer()
        ));
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        factory.emitEvent(ScriptType.SERVER, "server.started", Map.of(
                "server", event.getServer()
        ));
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        factory.emitEvent(ScriptType.SERVER, "server.stopping", Map.of(
                "server", event.getServer()
        ));
        factory.close(ScriptType.SERVER);
    }
}
