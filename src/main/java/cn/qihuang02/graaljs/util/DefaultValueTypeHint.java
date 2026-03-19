package cn.qihuang02.graaljs.util;

import java.util.Locale;

/**
 * 默认值类型提示，用于 {@code getDefaultValue()} 语义。
 * 与 Rhino 的 DefaultValueTypeHint 对齐。
 */
public enum DefaultValueTypeHint {
    STRING,
    NUMBER,
    BOOLEAN,
    FUNCTION,
    CLASS;

    public final String name;

    DefaultValueTypeHint() {
        name = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return name;
    }
}
