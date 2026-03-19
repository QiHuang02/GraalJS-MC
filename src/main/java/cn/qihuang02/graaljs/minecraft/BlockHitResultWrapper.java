package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 JS 值转换为 {@link BlockHitResult}。
 * <p>
 * 支持的输入格式：
 * <ul>
 *   <li>{@code BlockHitResult} — 直通</li>
 *   <li>{@code Map { blockPos: [x,y,z], direction: "north", location: [x,y,z], inside: false }} — 构造</li>
 * </ul>
 */
public final class BlockHitResultWrapper {

    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(BlockHitResult.class)) {
            return;
        }
        wrappers.register(BlockHitResult.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof BlockHitResult result) {
                return result;
            }
            if (from instanceof Map<?, ?> map) {
                return fromMap(map);
            }
            throw new IllegalArgumentException("Cannot convert value to BlockHitResult: " + from);
        });
    }

    private static BlockHitResult fromMap(Map<?, ?> map) {
        // blockPos（必需）
        Object blockPosObj = map.get("blockPos");
        if (blockPosObj == null) {
            throw new IllegalArgumentException("BlockHitResult map must contain 'blockPos'");
        }
        BlockPos blockPos = parseBlockPos(blockPosObj);

        // direction（必需）
        Object directionObj = map.get("direction");
        if (directionObj == null) {
            throw new IllegalArgumentException("BlockHitResult map must contain 'direction'");
        }
        Direction direction = parseDirection(directionObj.toString());

        // location（可选，默认为 blockPos 中心）
        Vec3 location;
        Object locationObj = map.get("location");
        if (locationObj != null) {
            location = parseVec3(locationObj);
        } else {
            location = Vec3.atCenterOf(blockPos);
        }

        // inside（可选，默认 false）
        boolean inside = false;
        Object insideObj = map.get("inside");
        if (insideObj instanceof Boolean b) {
            inside = b;
        }

        return new BlockHitResult(location, direction, blockPos, inside);
    }

    private static BlockPos parseBlockPos(Object obj) {
        if (obj instanceof BlockPos pos) {
            return pos;
        }
        if (obj instanceof List<?> list && list.size() >= 3) {
            return new BlockPos(
                    ((Number) list.get(0)).intValue(),
                    ((Number) list.get(1)).intValue(),
                    ((Number) list.get(2)).intValue()
            );
        }
        throw new IllegalArgumentException("Cannot parse BlockPos from: " + obj);
    }

    private static Vec3 parseVec3(Object obj) {
        if (obj instanceof Vec3 vec) {
            return vec;
        }
        if (obj instanceof List<?> list && list.size() >= 3) {
            return new Vec3(
                    ((Number) list.get(0)).doubleValue(),
                    ((Number) list.get(1)).doubleValue(),
                    ((Number) list.get(2)).doubleValue()
            );
        }
        throw new IllegalArgumentException("Cannot parse Vec3 from: " + obj);
    }

    private static Direction parseDirection(String name) {
        try {
            return Direction.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown direction: " + name
                    + ". Valid values: north, south, east, west, up, down");
        }
    }
}
