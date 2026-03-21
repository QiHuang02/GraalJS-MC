package cn.qihuang02.graaljs.typewrap;

/**
 * 控制类型信息如何序列化为字符串。
 * 所有 JS* TypeInfo 的 {@code append()} 都依赖此上下文来决定格式细节。
 */
public interface TypeStringContext {

    /** 获取 Java 类在 TS 侧的类型名 */
    String getTypeName(Class<?> clazz);

    /** 分隔符前后是否加空格（如 " | " vs "|"） */
    boolean padSeparators();

    /** 默认上下文：使用简单类名，带空格 */
    TypeStringContext DEFAULT = new TypeStringContext() {
        @Override
        public String getTypeName(Class<?> clazz) {
            return clazz.getSimpleName();
        }

        @Override
        public boolean padSeparators() {
            return true;
        }
    };
}
