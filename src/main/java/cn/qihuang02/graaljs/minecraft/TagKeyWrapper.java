package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Map;

/**
 * 将 JS 值转换为 {@link TagKey}。
 * <p>
 * 支持的输入格式：
 * <ul>
 *   <li>{@code TagKey} — 直通</li>
 *   <li>{@code String}（如 {@code "#minecraft:logs"}）— 去掉 {@code #} 前缀解析 ResourceLocation，registry 默认为 {@code minecraft:root}</li>
 *   <li>{@code Map { registry: "minecraft:block", tag: "minecraft:logs" }} — 完整构造</li>
 * </ul>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class TagKeyWrapper {

    // 延迟初始化以避免在测试环境中触发 Minecraft 类的静态初始化
    private static volatile ResourceKey defaultRegistry;

    private static ResourceKey getDefaultRegistry() {
        if (defaultRegistry == null) {
            defaultRegistry = ResourceKey.createRegistryKey(new ResourceLocation("minecraft", "root"));
        }
        return defaultRegistry;
    }

    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(TagKey.class)) {
            return;
        }
        wrappers.register((Class) TagKey.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof TagKey<?> key) {
                return key;
            }
            if (from instanceof Map<?, ?> map) {
                return fromMap(map);
            }
            if (from instanceof CharSequence sequence) {
                return fromString(sequence.toString());
            }
            throw new IllegalArgumentException("Cannot convert value to TagKey: " + from);
        });
    }

    private static TagKey<?> fromString(String input) {
        String cleaned = input.startsWith("#") ? input.substring(1) : input;
        ResourceLocation location = ResourceLocation.tryParse(cleaned);
        if (location == null) {
            throw new IllegalArgumentException("Invalid TagKey string: " + input);
        }
        return TagKey.create(getDefaultRegistry(), location);
    }

    private static TagKey<?> fromMap(Map<?, ?> map) {
        Object tagObj = map.get("tag");
        if (tagObj == null) {
            throw new IllegalArgumentException("TagKey map must contain 'tag' key");
        }

        String tagStr = tagObj.toString();
        if (tagStr.startsWith("#")) {
            tagStr = tagStr.substring(1);
        }
        ResourceLocation tagLocation = ResourceLocation.tryParse(tagStr);
        if (tagLocation == null) {
            throw new IllegalArgumentException("Invalid TagKey tag: " + tagObj);
        }

        Object registryObj = map.get("registry");
        ResourceKey registryKey;
        if (registryObj != null) {
            ResourceLocation registryLoc = ResourceLocation.tryParse(registryObj.toString());
            if (registryLoc == null) {
                throw new IllegalArgumentException("Invalid TagKey registry: " + registryObj);
            }
            registryKey = ResourceKey.createRegistryKey(registryLoc);
        } else {
            registryKey = getDefaultRegistry();
        }

        return TagKey.create(registryKey, tagLocation);
    }
}
