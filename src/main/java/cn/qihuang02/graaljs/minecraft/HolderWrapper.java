package cn.qihuang02.graaljs.minecraft;

import cn.qihuang02.graaljs.typewrap.TypeWrapperValidator;
import cn.qihuang02.graaljs.typewrap.TypeWrappers;
import net.minecraft.core.Holder;

/**
 * 将 JS 值转换为 {@link Holder}。
 * <p>
 * {@code Holder<T>} 是注册表条目的引用包装。由于 Holder 是泛型的且需要注册表上下文，
 * 这里主要做 passthrough 和 unwrap 支持。
 * <p>
 * 支持的输入格式：
 * <ul>
 *   <li>{@code Holder} — 直通</li>
 * </ul>
 * <p>
 * 更复杂的 Holder 查找（如从 ResourceLocation 字符串通过注册表查找）
 * 需要运行时注册表上下文，应在具体使用场景中处理。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class HolderWrapper {

    public static void register(TypeWrappers wrappers) {
        if (wrappers.contains(Holder.class)) {
            return;
        }
        wrappers.register((Class) Holder.class, TypeWrapperValidator.NOT_NULL, (context, from, target) -> {
            if (from instanceof Holder<?> holder) {
                return holder;
            }
            // Holder.direct() 可以包装任意值为 Holder
            // 但由于泛型擦除，这里无法安全地做类型检查
            // 所以只支持 passthrough
            throw new IllegalArgumentException("Cannot convert value to Holder: " + from
                    + ". Holder requires registry context; pass a Holder instance directly.");
        });
    }
}
