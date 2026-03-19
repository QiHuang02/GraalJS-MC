package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 将 JS 值转换为 {@link ResourceKey}。
 * <p>
 * 支持的输入格式：
 * <ul>
 *   <li>{@code ResourceKey} — 直通</li>
 *   <li>{@code String}（如 {@code "minecraft:stone"}）— 仅解析 location 部分，registry 默认为 {@code minecraft:root}</li>
 *   <li>{@code Map { registry: "minecraft:item", location: "minecraft:stone" }} — 完整构造</li>
 * </ul>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ResourceKeyWrapper {

    // 延迟初始化以避免在测试环境中触发 Minecraft 类的静态初始化
    private static volatile ResourceKey<? extends net.minecraft.core.Registry<?>> defaultRegistry;

    private static ResourceKey<? extends net.minecraft.core.Registry<?>> getDefaultRegistry() {
        if (defaultRegistry == null) {
            defaultRegistry = ResourceKey.createRegistryKey(new ResourceLocation("minecraft", "root"));
        }
        return defaultRegistry;
    }

    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(ResourceKey.class)) {
            return;
        }
        wrappers.register((Class) ResourceKey.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof ResourceKey<?> key) {
                return key;
            }
            if (from instanceof Map<?, ?> map) {
                return fromMap(map);
            }
            if (from instanceof CharSequence sequence) {
                ResourceLocation location = ResourceLocation.tryParse(sequence.toString());
                if (location != null) {
                    return ResourceKey.create((ResourceKey) getDefaultRegistry(), location);
                }
            }
            throw new IllegalArgumentException("Cannot convert value to ResourceKey: " + from);
        });
    }

    private static ResourceKey<?> fromMap(Map<?, ?> map) {
        Object registryObj = map.get("registry");
        Object locationObj = map.get("location");
        if (locationObj == null) {
            throw new IllegalArgumentException("ResourceKey map must contain 'location' key");
        }

        ResourceLocation location = ResourceLocation.tryParse(locationObj.toString());
        if (location == null) {
            throw new IllegalArgumentException("Invalid ResourceKey location: " + locationObj);
        }

        ResourceKey<? extends net.minecraft.core.Registry<?>> registryKey;
        if (registryObj != null) {
            ResourceLocation registryLoc = ResourceLocation.tryParse(registryObj.toString());
            if (registryLoc == null) {
                throw new IllegalArgumentException("Invalid ResourceKey registry: " + registryObj);
            }
            registryKey = ResourceKey.createRegistryKey(registryLoc);
        } else {
            registryKey = getDefaultRegistry();
        }

        return ResourceKey.create((ResourceKey) registryKey, location);
    }
}
