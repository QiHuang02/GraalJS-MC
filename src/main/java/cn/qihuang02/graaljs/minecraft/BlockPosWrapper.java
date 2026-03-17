package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

public final class BlockPosWrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(BlockPos.class)) {
            return;
        }
        wrappers.register(BlockPos.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof BlockPos blockPos) {
                return blockPos;
            }
            if (from instanceof List<?> list && list.size() >= 3) {
                return new BlockPos(
                        ((Number) list.get(0)).intValue(),
                        ((Number) list.get(1)).intValue(),
                        ((Number) list.get(2)).intValue()
                );
            }
            if (from instanceof Map<?, ?> map) {
                return new BlockPos(
                        ((Number) map.get("x")).intValue(),
                        ((Number) map.get("y")).intValue(),
                        ((Number) map.get("z")).intValue()
                );
            }
            if (from instanceof CharSequence sequence) {
                String[] parts = sequence.toString().trim().split("[,\\s]+");
                if (parts.length >= 3) {
                    return new BlockPos(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                    );
                }
            }
            throw new IllegalArgumentException("Cannot convert value to BlockPos: " + from);
        });
    }
}
