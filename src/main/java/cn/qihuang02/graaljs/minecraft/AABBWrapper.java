package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

public final class AABBWrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(AABB.class)) {
            return;
        }
        wrappers.register(AABB.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof AABB aabb) {
                return aabb;
            }
            if (from instanceof List<?> list && list.size() >= 6) {
                return new AABB(
                        ((Number) list.get(0)).doubleValue(),
                        ((Number) list.get(1)).doubleValue(),
                        ((Number) list.get(2)).doubleValue(),
                        ((Number) list.get(3)).doubleValue(),
                        ((Number) list.get(4)).doubleValue(),
                        ((Number) list.get(5)).doubleValue()
                );
            }
            if (from instanceof Map<?, ?> map) {
                // {min: [x1,y1,z1], max: [x2,y2,z2]}
                if (map.containsKey("min") && map.containsKey("max")) {
                    List<?> min = (List<?>) map.get("min");
                    List<?> max = (List<?>) map.get("max");
                    return new AABB(
                            ((Number) min.get(0)).doubleValue(),
                            ((Number) min.get(1)).doubleValue(),
                            ((Number) min.get(2)).doubleValue(),
                            ((Number) max.get(0)).doubleValue(),
                            ((Number) max.get(1)).doubleValue(),
                            ((Number) max.get(2)).doubleValue()
                    );
                }
                // {minX, minY, minZ, maxX, maxY, maxZ}
                return new AABB(
                        ((Number) map.get("minX")).doubleValue(),
                        ((Number) map.get("minY")).doubleValue(),
                        ((Number) map.get("minZ")).doubleValue(),
                        ((Number) map.get("maxX")).doubleValue(),
                        ((Number) map.get("maxY")).doubleValue(),
                        ((Number) map.get("maxZ")).doubleValue()
                );
            }
            throw new IllegalArgumentException("Cannot convert value to AABB: " + from);
        });
    }
}
