package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.resources.ResourceLocation;

public final class ResourceLocationWrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(ResourceLocation.class)) {
            return;
        }
        wrappers.register(ResourceLocation.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof ResourceLocation location) {
                return location;
            }
            if (from instanceof CharSequence sequence) {
                ResourceLocation location = ResourceLocation.tryParse(sequence.toString());
                if (location != null) {
                    return location;
                }
            }
            throw new IllegalArgumentException("Cannot convert value to ResourceLocation: " + from);
        });
    }
}
