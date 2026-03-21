package cn.qihuang02.graaljs.typewrap;

import java.util.List;

/**
 * 对象字面量类型，如 {@code {a: string, b?: number}}。
 */
public record JSObjectTypeInfo(List<JSOptionalParam> fields) implements TypeInfo {

    public JSObjectTypeInfo {
        fields = List.copyOf(fields);
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
        sb.append('{');
        if (ctx.padSeparators()) sb.append(' ');
        String sep = ctx.padSeparators() ? ", " : ",";
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(sep);
            fields.get(i).append(ctx, sb);
        }
        if (ctx.padSeparators()) sb.append(' ');
        sb.append('}');
    }
}
