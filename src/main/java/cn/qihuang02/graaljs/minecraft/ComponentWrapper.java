package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public final class ComponentWrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(Component.class)) {
            return;
        }
        wrappers.register(Component.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof Component component) {
                return component;
            }
            if (from instanceof CharSequence sequence) {
                return Component.literal(sequence.toString());
            }
            if (from instanceof Map<?, ?> map) {
                if (map.containsKey("translate")) {
                    String key = String.valueOf(map.get("translate"));
                    Object withArgs = map.get("with");
                    if (withArgs instanceof List<?> argList && !argList.isEmpty()) {
                        return Component.translatable(key, argList.toArray());
                    }
                    return Component.translatable(key);
                }
                if (map.containsKey("text")) {
                    return Component.literal(String.valueOf(map.get("text")));
                }
            }
            throw new IllegalArgumentException("Cannot convert value to Component: " + from);
        });
    }
}
