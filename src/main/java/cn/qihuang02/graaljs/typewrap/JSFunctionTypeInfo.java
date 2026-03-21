package cn.qihuang02.graaljs.typewrap;

import java.util.List;

/**
 * 函数签名类型，如 {@code (a: string, b: number) => void}。
 */
public record JSFunctionTypeInfo(List<JSOptionalParam> params, TypeInfo returnType) implements TypeInfo {

    public JSFunctionTypeInfo {
        params = List.copyOf(params);
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
        sb.append('(');
        String sep = ctx.padSeparators() ? ", " : ",";
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(sep);
            params.get(i).append(ctx, sb);
        }
        sb.append(')');
        sb.append(ctx.padSeparators() ? " => " : "=>");
        returnType.append(ctx, sb);
    }
}
