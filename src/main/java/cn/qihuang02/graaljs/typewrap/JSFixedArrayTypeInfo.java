package cn.qihuang02.graaljs.typewrap;

import java.util.List;

/**
 * 元组类型（固定长度数组），如 {@code [string, number]}。
 */
public record JSFixedArrayTypeInfo(List<JSOptionalParam> elements) implements TypeInfo {

    public JSFixedArrayTypeInfo {
        elements = List.copyOf(elements);
    }

    @Override
    public Class<?> rawType() {
        return Object.class;
    }

    @Override
    public boolean isAssignableFrom(Object value) {
        return false;
    }

    @Override
    public boolean shouldConvert() {
        return false;
    }

    @Override
    public String describe() {
        return toString(TypeStringContext.DEFAULT);
    }

    @Override
    public void append(TypeStringContext ctx, StringBuilder sb) {
        sb.append('[');
        String sep = ctx.padSeparators() ? ", " : ",";
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) sb.append(sep);
            elements.get(i).append(ctx, sb);
        }
        sb.append(']');
    }
}
