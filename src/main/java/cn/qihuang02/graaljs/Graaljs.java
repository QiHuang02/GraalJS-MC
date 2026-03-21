package cn.qihuang02.graaljs;

import cn.qihuang02.graaljs.core.GraaljsContextFactory;
import cn.qihuang02.graaljs.core.ScriptType;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

@Mod(Graaljs.MODID)
public class Graaljs {
    public static final String MODID = "graaljs";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static GraaljsContextFactory factory;

    private final GraaljsContextFactory localFactory;

    public Graaljs() {
        LOGGER.info("GraalJS-MC initializing...");

        localFactory = new GraaljsContextFactory(FMLPaths.GAMEDIR.get());
        factory = localFactory;
        localFactory.createAndLoad(ScriptType.STARTUP);

        MinecraftForge.EVENT_BUS.register(this);
    }

    public static GraaljsContextFactory getFactory() {
        return factory;
    }

    /**
     * 完全关闭所有上下文。
     * 在 mod 卸载或需要完全重置时调用。
     */
    public static void shutdownAll() {
        if (factory != null) {
            factory.closeAll();
        }
    }

    @SubscribeEvent
    public void onServerStarting(@NotNull ServerStartingEvent event) {
        localFactory.createAndLoad(ScriptType.SERVER);
    }

    @SubscribeEvent
    public void onServerStopping(@NotNull ServerStoppingEvent event) {
        localFactory.close(ScriptType.SERVER);
    }
}
