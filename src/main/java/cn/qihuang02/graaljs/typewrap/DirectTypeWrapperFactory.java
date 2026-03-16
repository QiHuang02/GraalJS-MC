package cn.qihuang02.graaljs.typewrap;

import cn.qihuang02.graaljs.core.GraaljsContext;

@FunctionalInterface
public interface DirectTypeWrapperFactory<T> extends TypeWrapperFactory<T> {
    T wrapDirect(Object from, Class<?> target);

    @Override
    default T wrap(GraaljsContext cx, Object from, Class<?> target) {
        return wrapDirect(from, target);
    }
}
