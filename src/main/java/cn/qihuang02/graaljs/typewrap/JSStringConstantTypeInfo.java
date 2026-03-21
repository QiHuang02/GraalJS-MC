package cn.qihuang02.graaljs.typewrap;

/**
 * 字符串常量类型，如 {@code "abc"}。
 */
public record JSStringConstantTypeInfo(String value) implements TypeInfo {

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
        return "\"" + value + "\"";
    }

    @Override
    public void append(TypeStringContext ctx, StringBuilder sb) {
        sb.append('"').append(value).append('"');
    }
}
