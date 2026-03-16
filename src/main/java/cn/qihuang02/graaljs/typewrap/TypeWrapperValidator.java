package cn.qihuang02.graaljs.typewrap;

@FunctionalInterface
public interface TypeWrapperValidator {
    TypeWrapperValidator ALWAYS = from -> true;
    TypeWrapperValidator NOT_NULL = from -> from != null;

    boolean test(Object from);
}
