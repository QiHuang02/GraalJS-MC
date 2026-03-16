package cn.qihuang02.graaljs.typewrap;

import cn.qihuang02.graaljs.core.GraaljsContext;

@FunctionalInterface
public interface TypeWrapperFactory<T> {
    T wrap(GraaljsContext cx, Object from, Class<?> target);
}
