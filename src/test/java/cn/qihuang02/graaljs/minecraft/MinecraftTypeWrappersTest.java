package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftTypeWrappersTest {

    // ── ResourceLocationWrapper ──

    @Test
    void resourceLocation_fromString() {
        TypeWrappers wrappers = new TypeWrappers();
        ResourceLocationWrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(ResourceLocation.class, "minecraft:stone");
        assertNotNull(factory);
        ResourceLocation result = factory.wrap(null, "minecraft:stone", ResourceLocation.class);
        assertEquals("minecraft", result.getNamespace());
        assertEquals("stone", result.getPath());
    }

    @Test
    void resourceLocation_passthrough() {
        TypeWrappers wrappers = new TypeWrappers();
        ResourceLocationWrapper.register(wrappers);

        ResourceLocation original = new ResourceLocation("minecraft", "dirt");
        var factory = wrappers.getWrapperFactory(ResourceLocation.class, original);
        assertSame(original, factory.wrap(null, original, ResourceLocation.class));
    }

    @Test
    void resourceLocation_invalidThrows() {
        TypeWrappers wrappers = new TypeWrappers();
        ResourceLocationWrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(ResourceLocation.class, 123);
        assertThrows(IllegalArgumentException.class, () -> factory.wrap(null, 123, ResourceLocation.class));
    }

    // ── BlockPosWrapper ──

    @Test
    void blockPos_fromList() {
        TypeWrappers wrappers = new TypeWrappers();
        BlockPosWrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(BlockPos.class, List.of(1, 2, 3));
        BlockPos result = factory.wrap(null, List.of(1, 2, 3), BlockPos.class);
        assertEquals(new BlockPos(1, 2, 3), result);
    }

    @Test
    void blockPos_fromMap() {
        TypeWrappers wrappers = new TypeWrappers();
        BlockPosWrapper.register(wrappers);

        Map<String, Integer> map = Map.of("x", 4, "y", 5, "z", 6);
        var factory = wrappers.getWrapperFactory(BlockPos.class, map);
        assertEquals(new BlockPos(4, 5, 6), factory.wrap(null, map, BlockPos.class));
    }

    @Test
    void blockPos_fromString() {
        TypeWrappers wrappers = new TypeWrappers();
        BlockPosWrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(BlockPos.class, "7 8 9");
        assertEquals(new BlockPos(7, 8, 9), factory.wrap(null, "7 8 9", BlockPos.class));
    }

    @Test
    void blockPos_fromCommaString() {
        TypeWrappers wrappers = new TypeWrappers();
        BlockPosWrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(BlockPos.class, "10,11,12");
        assertEquals(new BlockPos(10, 11, 12), factory.wrap(null, "10,11,12", BlockPos.class));
    }

    @Test
    void blockPos_passthrough() {
        TypeWrappers wrappers = new TypeWrappers();
        BlockPosWrapper.register(wrappers);

        BlockPos original = new BlockPos(1, 2, 3);
        var factory = wrappers.getWrapperFactory(BlockPos.class, original);
        assertSame(original, factory.wrap(null, original, BlockPos.class));
    }

    // ── Vec3Wrapper ──

    @Test
    void vec3_fromList() {
        TypeWrappers wrappers = new TypeWrappers();
        Vec3Wrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(Vec3.class, List.of(1.5, 2.5, 3.5));
        assertEquals(new Vec3(1.5, 2.5, 3.5), factory.wrap(null, List.of(1.5, 2.5, 3.5), Vec3.class));
    }

    @Test
    void vec3_fromMap() {
        TypeWrappers wrappers = new TypeWrappers();
        Vec3Wrapper.register(wrappers);

        Map<String, Double> map = Map.of("x", 1.0, "y", 2.0, "z", 3.0);
        var factory = wrappers.getWrapperFactory(Vec3.class, map);
        assertEquals(new Vec3(1.0, 2.0, 3.0), factory.wrap(null, map, Vec3.class));
    }

    @Test
    void vec3_fromString() {
        TypeWrappers wrappers = new TypeWrappers();
        Vec3Wrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(Vec3.class, "1.5 2.5 3.5");
        assertEquals(new Vec3(1.5, 2.5, 3.5), factory.wrap(null, "1.5 2.5 3.5", Vec3.class));
    }

    @Test
    void vec3_passthrough() {
        TypeWrappers wrappers = new TypeWrappers();
        Vec3Wrapper.register(wrappers);

        Vec3 original = new Vec3(1, 2, 3);
        var factory = wrappers.getWrapperFactory(Vec3.class, original);
        assertSame(original, factory.wrap(null, original, Vec3.class));
    }

    // ── ComponentWrapper ──

    @Test
    void component_fromString() {
        TypeWrappers wrappers = new TypeWrappers();
        ComponentWrapper.register(wrappers);

        var factory = wrappers.getWrapperFactory(Component.class, "hello");
        Component result = factory.wrap(null, "hello", Component.class);
        assertEquals("hello", result.getString());
    }

    @Test
    void component_fromTextMap() {
        TypeWrappers wrappers = new TypeWrappers();
        ComponentWrapper.register(wrappers);

        Map<String, String> map = Map.of("text", "world");
        var factory = wrappers.getWrapperFactory(Component.class, map);
        assertEquals("world", factory.wrap(null, map, Component.class).getString());
    }

    @Test
    void component_fromTranslateMap() {
        TypeWrappers wrappers = new TypeWrappers();
        ComponentWrapper.register(wrappers);

        Map<String, Object> map = Map.of("translate", "item.minecraft.diamond");
        var factory = wrappers.getWrapperFactory(Component.class, map);
        Component result = factory.wrap(null, map, Component.class);
        assertNotNull(result);
    }

    @Test
    void component_fromTranslateWithArgs() {
        TypeWrappers wrappers = new TypeWrappers();
        ComponentWrapper.register(wrappers);

        Map<String, Object> map = Map.of("translate", "commands.give.success.single", "with", List.of(1, "Steve"));
        var factory = wrappers.getWrapperFactory(Component.class, map);
        Component result = factory.wrap(null, map, Component.class);
        assertNotNull(result);
    }

    @Test
    void component_passthrough() {
        TypeWrappers wrappers = new TypeWrappers();
        ComponentWrapper.register(wrappers);

        Component original = Component.literal("test");
        var factory = wrappers.getWrapperFactory(Component.class, original);
        assertSame(original, factory.wrap(null, original, Component.class));
    }

    // ── CompoundTagWrapper ──

    @Test
    void compoundTag_fromMap() {
        TypeWrappers wrappers = new TypeWrappers();
        CompoundTagWrapper.register(wrappers);

        Map<String, Object> map = Map.of(
                "name", "test",
                "count", 42,
                "active", true
        );
        var factory = wrappers.getWrapperFactory(CompoundTag.class, map);
        CompoundTag result = factory.wrap(null, map, CompoundTag.class);

        assertEquals("test", result.getString("name"));
        assertEquals(42, result.getInt("count"));
        assertTrue(result.getBoolean("active"));
    }

    @Test
    void compoundTag_nestedMap() {
        Map<String, Object> inner = Map.of("value", 10);
        Map<String, Object> map = Map.of("nested", inner);

        CompoundTag result = CompoundTagWrapper.buildCompoundTag(map);
        assertTrue(result.contains("nested"));
        assertEquals(10, result.getCompound("nested").getInt("value"));
    }

    @Test
    void compoundTag_withList() {
        Map<String, Object> map = Map.of("items", List.of("a", "b", "c"));

        CompoundTag result = CompoundTagWrapper.buildCompoundTag(map);
        ListTag listTag = result.getList("items", StringTag.valueOf("").getId());
        assertEquals(3, listTag.size());
        assertEquals("a", listTag.getString(0));
    }

    @Test
    void compoundTag_passthrough() {
        TypeWrappers wrappers = new TypeWrappers();
        CompoundTagWrapper.register(wrappers);

        CompoundTag original = new CompoundTag();
        original.putString("key", "value");
        var factory = wrappers.getWrapperFactory(CompoundTag.class, original);
        assertSame(original, factory.wrap(null, original, CompoundTag.class));
    }

    @Test
    void toTag_allTypes() {
        assertEquals(ByteTag.valueOf((byte) 1), CompoundTagWrapper.toTag(true));
        assertEquals(ByteTag.valueOf((byte) 0), CompoundTagWrapper.toTag(false));
        assertEquals(ByteTag.valueOf((byte) 5), CompoundTagWrapper.toTag((byte) 5));
        assertEquals(IntTag.valueOf(42), CompoundTagWrapper.toTag(42));
        assertEquals(DoubleTag.valueOf(3.14), CompoundTagWrapper.toTag(3.14));
        assertEquals(StringTag.valueOf("hello"), CompoundTagWrapper.toTag("hello"));
    }

    // ── AABBWrapper ──

    @Test
    void aabb_fromList() {
        TypeWrappers wrappers = new TypeWrappers();
        AABBWrapper.register(wrappers);

        List<Double> list = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        var factory = wrappers.getWrapperFactory(AABB.class, list);
        AABB result = factory.wrap(null, list, AABB.class);
        assertEquals(new AABB(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), result);
    }

    @Test
    void aabb_fromMinMaxMap() {
        TypeWrappers wrappers = new TypeWrappers();
        AABBWrapper.register(wrappers);

        Map<String, Object> map = Map.of(
                "min", List.of(0.0, 0.0, 0.0),
                "max", List.of(1.0, 1.0, 1.0)
        );
        var factory = wrappers.getWrapperFactory(AABB.class, map);
        assertEquals(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0), factory.wrap(null, map, AABB.class));
    }

    @Test
    void aabb_fromExplicitMap() {
        TypeWrappers wrappers = new TypeWrappers();
        AABBWrapper.register(wrappers);

        Map<String, Double> map = Map.of(
                "minX", 1.0, "minY", 2.0, "minZ", 3.0,
                "maxX", 4.0, "maxY", 5.0, "maxZ", 6.0
        );
        var factory = wrappers.getWrapperFactory(AABB.class, map);
        assertEquals(new AABB(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), factory.wrap(null, map, AABB.class));
    }

    @Test
    void aabb_passthrough() {
        TypeWrappers wrappers = new TypeWrappers();
        AABBWrapper.register(wrappers);

        AABB original = new AABB(0, 0, 0, 1, 1, 1);
        var factory = wrappers.getWrapperFactory(AABB.class, original);
        assertSame(original, factory.wrap(null, original, AABB.class));
    }

    // ── MinecraftTypeWrappers.registerAll ──

    @Test
    void registerAll_registersAllWrappers() {
        TypeWrappers wrappers = new TypeWrappers();
        MinecraftTypeWrappers.registerAll(wrappers);

        assertTrue(wrappers.contains(ResourceLocation.class));
        assertTrue(wrappers.contains(BlockPos.class));
        assertTrue(wrappers.contains(Vec3.class));
        assertTrue(wrappers.contains(Component.class));
        assertTrue(wrappers.contains(ItemStack.class));
        assertTrue(wrappers.contains(CompoundTag.class));
        assertTrue(wrappers.contains(AABB.class));
        // BlockState 需要注册表初始化，此处仅验证注册不抛异常
    }

    @Test
    void registerAll_idempotent() {
        TypeWrappers wrappers = new TypeWrappers();
        MinecraftTypeWrappers.registerAll(wrappers);
        // 第二次调用不应抛异常（contains 防重复）
        MinecraftTypeWrappers.registerAll(wrappers);
        assertTrue(wrappers.contains(ResourceLocation.class));
    }

    @Test
    void registerAll_doesNotOverrideExisting() {
        TypeWrappers wrappers = new TypeWrappers();
        // 先注册自定义的 ResourceLocation wrapper
        wrappers.register(ResourceLocation.class, (context, from, target) -> new ResourceLocation("custom", "override"));

        MinecraftTypeWrappers.registerAll(wrappers);

        // 自定义 wrapper 应保留
        var factory = wrappers.getWrapperFactory(ResourceLocation.class, "anything");
        ResourceLocation result = factory.wrap(null, "anything", ResourceLocation.class);
        assertEquals("custom", result.getNamespace());
        assertEquals("override", result.getPath());
    }
}
