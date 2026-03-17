package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

public final class ItemStackWrapper {
    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(ItemStack.class)) {
            return;
        }
        wrappers.register(ItemStack.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof ItemStack stack) {
                return stack;
            }
            if (from instanceof Item item) {
                return new ItemStack(item);
            }
            if (from instanceof CharSequence sequence) {
                Item item = resolveItem(sequence.toString());
                if (item != null) {
                    return new ItemStack(item);
                }
            }
            if (from instanceof Map<?, ?> map) {
                Object itemValue = map.get("item");
                Object countValue = map.get("count");
                ItemStack base = context.jsToJava(itemValue, ItemStack.class);
                int count = countValue instanceof Number number ? number.intValue() : 1;
                ItemStack copy = base.copy();
                copy.setCount(count);
                return copy;
            }
            throw new IllegalArgumentException("Cannot convert value to ItemStack: " + from);
        });
    }

    static Item resolveItem(String rawId) {
        ensureMinecraftBootstrap();
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return null;
        }

        Item vanillaItem = resolveVanillaItem(id);
        if (vanillaItem != null) {
            return vanillaItem;
        }

        try {
            Item registryItem = BuiltInRegistries.ITEM.get(id);
            return registryItem != null && registryItem != Items.AIR ? registryItem : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Item resolveVanillaItem(ResourceLocation id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }

        String fieldName = id.getPath().toUpperCase(Locale.ROOT).replace('/', '_');
        try {
            Field field = Items.class.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Item item ? item : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
            // 游戏运行期通常已经完成引导；测试环境缺失时尽量补齐即可。
        }
    }
}
