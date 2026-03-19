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
        // 以下 Wrapper 依赖 Minecraft 运行时类（ResourceKey 等需要 SharedConstants 初始化），
        // 在纯测试环境中可能因类加载失败而跳过
        safeRegister("ResourceKeyWrapper", wrappers);
        safeRegister("TagKeyWrapper", wrappers);
        safeRegister("HolderWrapper", wrappers);
        safeRegister("BlockHitResultWrapper", wrappers);
    }

    private static void safeRegister(String wrapperClassName, TypeWrappers wrappers) {
        try {
            String fullName = MinecraftTypeWrappers.class.getPackageName() + "." + wrapperClassName;
            Class<?> clazz = Class.forName(fullName);
            java.lang.reflect.Method registerMethod = clazz.getMethod("register", TypeWrappers.class);
            registerMethod.invoke(null, wrappers);
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            // 在测试环境中，某些 Minecraft 类可能无法加载
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register wrapper: " + wrapperClassName, e);
        }
    }
}
