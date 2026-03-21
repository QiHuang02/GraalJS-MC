package cn.qihuang02.graaljs.typewrap;

/**
 * 基础常量类型：{@code null}、{@code undefined}、{@code true}、{@code false}。
 */
public record JSBasicConstantTypeInfo(Kind kind) implements TypeInfo {

    public enum Kind {
        NULL("null"),
        UNDEFINED("undefined"),
        TRUE("true"),
        FALSE("false");

        private final String literal;

        Kind(String literal) {
            this.literal = literal;
        }

        public String literal() {
            return literal;
        }
    }

    public static final JSBasicConstantTypeInfo NULL = new JSBasicConstantTypeInfo(Kind.NULL);
    public static final JSBasicConstantTypeInfo UNDEFINED = new JSBasicConstantTypeInfo(Kind.UNDEFINED);
    public static final JSBasicConstantTypeInfo TRUE = new JSBasicConstantTypeInfo(Kind.TRUE);
    public static final JSBasicConstantTypeInfo FALSE = new JSBasicConstantTypeInfo(Kind.FALSE);

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
        return kind.literal();
    }

    @Override
    public void append(TypeStringContext ctx, StringBuilder sb) {
        sb.append(kind.literal());
    }
}
