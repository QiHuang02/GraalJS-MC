package cn.qihuang02.graaljs.typewrap;

/**
 * 数字常量类型，如 {@code 42}。
 */
public record JSNumberConstantTypeInfo(double value) implements TypeInfo {

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
        // 整数不显示小数点
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    @Override
    public void append(TypeStringContext ctx, StringBuilder sb) {
        sb.append(describe());
    }
}
