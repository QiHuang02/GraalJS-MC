package cn.qihuang02.graaljs.typewrap;

public record TypeWrapper<T>(
        Class<T> target,
        TypeWrapperValidator validator,
        TypeWrapperFactory<T> factory
) {
}
