package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;
import org.jetbrains.annotations.Nullable;

/**
 * 实现此接口的 Java 对象在桥接层的相等性比较中使用自定义逻辑。
 * 当 {@link AbstractReflectiveProxyObject} 进行 equals 比较时，
 * 如果目标对象实现了此接口，会优先调用 {@link #specialEquals(GraaljsContext, Object, boolean)}。
 */
public interface SpecialEquality {
    /**
     * 自定义相等性比较。
     *
     * @param cx      当前 GraalJS 上下文，可能为 null
     * @param other   要比较的对象（已解包的 Java 对象）
     * @param shallow 是否为浅比较（{@code ==} 而非 {@code ===}）
     * @return 如果认为相等则返回 true
     */
    boolean specialEquals(@Nullable GraaljsContext cx, Object other, boolean shallow);

    /**
     * 统一的相等性检查分派。
     * <p>优先使用 {@link SpecialEquality} 接口，其次对枚举类型提供默认的
     * 字符串（忽略大小写）和数字（ordinal）相等性比较。
     *
     * @param cx      当前上下文，可能为 null
     * @param self    自身对象
     * @param other   比较目标
     * @param shallow 是否为浅比较
     * @return 比较结果；null 表示无法通过特殊逻辑判断，应回退到 Object.equals()
     */
    @Nullable
    static Boolean checkSpecialEquality(@Nullable GraaljsContext cx, Object self, Object other, boolean shallow) {
        if (self instanceof SpecialEquality se) {
            return se.specialEquals(cx, other, shallow);
        }
        // 枚举默认支持：与字符串（忽略大小写）和数字（ordinal）的相等性比较
        if (self instanceof Enum<?> enumSelf) {
            if (other instanceof CharSequence cs) {
                return enumSelf.name().equalsIgnoreCase(cs.toString());
            }
            if (other instanceof Number num) {
                return enumSelf.ordinal() == num.intValue();
            }
        }
        return null;
    }
}
