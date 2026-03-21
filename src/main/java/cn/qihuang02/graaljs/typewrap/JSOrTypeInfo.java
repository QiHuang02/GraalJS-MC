package cn.qihuang02.graaljs.typewrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 联合类型，如 {@code string | number}。
 * 支持 {@link TypeInfo#or(TypeInfo)} 链式构建，自动展平嵌套联合。
 */
public record JSOrTypeInfo(List<TypeInfo> types) implements TypeInfo {

    public JSOrTypeInfo {
        types = List.copyOf(types);
        if (types.size() < 2) {
            throw new IllegalArgumentException("Union type requires at least 2 members, got " + types.size());
        }
    }

    /** 构建联合类型，自动展平已有的 JSOrTypeInfo */
    public static TypeInfo of(TypeInfo left, TypeInfo right) {
        List<TypeInfo> members = new ArrayList<>();
        flatten(left, members);
        flatten(right, members);
        if (members.size() == 1) {
            return members.get(0);
        }
        return new JSOrTypeInfo(members);
    }

    private static void flatten(TypeInfo info, List<TypeInfo> out) {
        if (info instanceof JSOrTypeInfo or) {
            out.addAll(or.types());
        } else {
            out.add(info);
        }
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
        String sep = ctx.padSeparators() ? " | " : "|";
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(sep);
            types.get(i).append(ctx, sb);
        }
    }
}
