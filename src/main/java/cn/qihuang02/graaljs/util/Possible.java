package cn.qihuang02.graaljs.util;

import org.jetbrains.annotations.Nullable;

/**
 * 区分"未设置"和"设置为 null"的容器，类似 Optional 但语义不同。
 * 用于类型转换和配置场景中区分缺省值。
 *
 * <ul>
 *   <li>{@link #empty()} — 未设置（缺省）</li>
 *   <li>{@link #ofNull()} — 显式设置为 null</li>
 *   <li>{@link #of(Object)} — 设置为具体值</li>
 * </ul>
 *
 * @param <T> 值类型
 */
public record Possible<T>(@Nullable T value, boolean set) {
    @SuppressWarnings("rawtypes")
    private static final Possible EMPTY = new Possible<>(null, false);
    @SuppressWarnings("rawtypes")
    private static final Possible NULL = new Possible<>(null, true);

    /**
     * 返回"未设置"的实例。
     */
    @SuppressWarnings("unchecked")
    public static <T> Possible<T> empty() {
        return (Possible<T>) EMPTY;
    }

    /**
     * 返回"显式设置为 null"的实例。
     */
    @SuppressWarnings("unchecked")
    public static <T> Possible<T> ofNull() {
        return (Possible<T>) NULL;
    }

    /**
     * 返回包含具体值的实例。
     */
    public static <T> Possible<T> of(@Nullable T value) {
        if (value == null) {
            return ofNull();
        }
        return new Possible<>(value, true);
    }

    /**
     * 是否已设置（包括设置为 null）。
     */
    public boolean isSet() {
        return set;
    }

    /**
     * 是否未设置。
     */
    public boolean isEmpty() {
        return !set;
    }

    /**
     * 将值强制转换为指定类型。
     */
    @SuppressWarnings("unchecked")
    public <R> Possible<R> cast() {
        return (Possible<R>) this;
    }

    @Override
    public String toString() {
        if (!set) {
            return "Possible.empty";
        }
        return "Possible[" + value + "]";
    }
}
