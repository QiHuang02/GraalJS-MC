package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class Vec3Wrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(Vec3.class)) {
            return;
        }
        wrappers.register(Vec3.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof Vec3 vec3) {
                return vec3;
            }
            if (from instanceof List<?> list && list.size() >= 3) {
                return new Vec3(
                        ((Number) list.get(0)).doubleValue(),
                        ((Number) list.get(1)).doubleValue(),
                        ((Number) list.get(2)).doubleValue()
                );
            }
            if (from instanceof Map<?, ?> map) {
                return new Vec3(
                        ((Number) map.get("x")).doubleValue(),
                        ((Number) map.get("y")).doubleValue(),
                        ((Number) map.get("z")).doubleValue()
                );
            }
            if (from instanceof CharSequence sequence) {
                String[] parts = sequence.toString().trim().split("[,\\s]+");
                if (parts.length >= 3) {
                    return new Vec3(
                            Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2])
                    );
                }
            }
            throw new IllegalArgumentException("Cannot convert value to Vec3: " + from);
        });
    }
}
