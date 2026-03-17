package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Optional;

public final class BlockStateWrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(BlockState.class)) {
            return;
        }
        wrappers.register(BlockState.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof BlockState state) {
                return state;
            }
            if (from instanceof CharSequence sequence) {
                return parseBlockState(sequence.toString());
            }
            throw new IllegalArgumentException("Cannot convert value to BlockState: " + from);
        });
    }

    static BlockState parseBlockState(String input) {
        String blockId;
        String propertiesPart = null;

        int bracketStart = input.indexOf('[');
        if (bracketStart >= 0) {
            int bracketEnd = input.indexOf(']', bracketStart);
            if (bracketEnd < 0) {
                throw new IllegalArgumentException("Malformed BlockState string (missing ']'): " + input);
            }
            blockId = input.substring(0, bracketStart);
            propertiesPart = input.substring(bracketStart + 1, bracketEnd);
        } else {
            blockId = input;
        }

        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid block id: " + blockId);
        }

        Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(id);
        if (blockOpt.isEmpty()) {
            throw new IllegalArgumentException("Unknown block: " + id);
        }

        BlockState state = blockOpt.get().defaultBlockState();

        if (propertiesPart != null && !propertiesPart.isEmpty()) {
            state = applyProperties(state, propertiesPart);
        }

        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperties(BlockState state, String propertiesPart) {
        String[] pairs = propertiesPart.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) {
                throw new IllegalArgumentException("Malformed property pair: " + pair);
            }
            String propName = kv[0].trim();
            String propValue = kv[1].trim();

            Property<?> property = state.getBlock().getStateDefinition().getProperty(propName);
            if (property == null) {
                throw new IllegalArgumentException("Unknown property '" + propName + "' for block " + state.getBlock());
            }

            Optional<?> parsedValue = property.getValue(propValue);
            if (parsedValue.isEmpty()) {
                throw new IllegalArgumentException("Invalid value '" + propValue + "' for property '" + propName + "'");
            }

            state = state.setValue((Property) property, (Comparable) parsedValue.get());
        }
        return state;
    }
}
