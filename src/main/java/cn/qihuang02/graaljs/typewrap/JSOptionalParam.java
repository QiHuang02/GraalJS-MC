package cn.qihuang02.graaljs.typewrap;

/**
 * 可选参数描述，被 JSObjectTypeInfo、JSFunctionTypeInfo、JSFixedArrayTypeInfo 共用。
 *
 * @param name     参数名
 * @param type     参数类型
 * @param optional 是否可选
 */
public record JSOptionalParam(String name, TypeInfo type, boolean optional) {

    /** 便捷工厂：必选参数 */
    public static JSOptionalParam required(String name, TypeInfo type) {
        return new JSOptionalParam(name, type, false);
    }

    /** 便捷工厂：可选参数 */
    public static JSOptionalParam optional(String name, TypeInfo type) {
        return new JSOptionalParam(name, type, true);
    }

    /** 将参数追加到 StringBuilder，格式如 "name?: Type" */
    public void append(TypeStringContext ctx, StringBuilder sb) {
        sb.append(name);
        if (optional) {
            sb.append('?');
        }
        sb.append(ctx.padSeparators() ? ": " : ":");
        type.append(ctx, sb);
    }
}
