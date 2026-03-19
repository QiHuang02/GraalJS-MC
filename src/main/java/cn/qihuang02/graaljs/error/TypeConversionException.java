package cn.qihuang02.graaljs.error;

/**
 * 类型转换失败异常。
 * 当 JS 值无法转换为目标 Java 类型时抛出。
 */
public class TypeConversionException extends HostBridgeException {
    private final Class<?> targetType;
    private final Object sourceValue;

    public TypeConversionException(String message, Class<?> targetType, Object sourceValue) {
        super(message);
        this.targetType = targetType;
        this.sourceValue = sourceValue;
    }

    public TypeConversionException(String message, Class<?> targetType, Object sourceValue, Throwable cause) {
        super(message, cause);
        this.targetType = targetType;
        this.sourceValue = sourceValue;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public Object getSourceValue() {
        return sourceValue;
    }
}
