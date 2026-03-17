package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;

public final class CompoundTagWrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(CompoundTag.class)) {
            return;
        }
        wrappers.register(CompoundTag.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof CompoundTag tag) {
                return tag;
            }
            if (from instanceof Map<?, ?> map) {
                return buildCompoundTag(map);
            }
            throw new IllegalArgumentException("Cannot convert value to CompoundTag: " + from);
        });
    }

    static CompoundTag buildCompoundTag(Map<?, ?> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Tag value = toTag(entry.getValue());
            tag.put(key, value);
        }
        return tag;
    }

    static Tag toTag(Object value) {
        if (value instanceof Tag tag) {
            return tag;
        }
        if (value instanceof Boolean bool) {
            return ByteTag.valueOf(bool ? (byte) 1 : (byte) 0);
        }
        if (value instanceof Byte b) {
            return ByteTag.valueOf(b);
        }
        if (value instanceof Short s) {
            return ShortTag.valueOf(s);
        }
        if (value instanceof Integer i) {
            return IntTag.valueOf(i);
        }
        if (value instanceof Long l) {
            return LongTag.valueOf(l);
        }
        if (value instanceof Float f) {
            return FloatTag.valueOf(f);
        }
        if (value instanceof Double d) {
            return DoubleTag.valueOf(d);
        }
        if (value instanceof Number number) {
            return DoubleTag.valueOf(number.doubleValue());
        }
        if (value instanceof String str) {
            return StringTag.valueOf(str);
        }
        if (value instanceof CharSequence sequence) {
            return StringTag.valueOf(sequence.toString());
        }
        if (value instanceof Map<?, ?> map) {
            return buildCompoundTag(map);
        }
        if (value instanceof List<?> list) {
            ListTag listTag = new ListTag();
            for (Object element : list) {
                listTag.add(toTag(element));
            }
            return listTag;
        }
        throw new IllegalArgumentException("Cannot convert value to NBT Tag: " + value);
    }
}
