package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrappers;

/**
 * Minecraft 类型包装器集中注册入口。
 */
public final class MinecraftTypeWrappers {
    public static void registerAll(TypeWrappers wrappers) {
        ResourceLocationWrapper.register(wrappers);
        BlockPosWrapper.register(wrappers);
        Vec3Wrapper.register(wrappers);
        ComponentWrapper.register(wrappers);
        ItemStackWrapper.register(wrappers);
        CompoundTagWrapper.register(wrappers);
        AABBWrapper.register(wrappers);
        BlockStateWrapper.register(wrappers);
    }
}
