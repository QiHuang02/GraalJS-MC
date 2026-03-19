package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;

/**
 * 动态计算属性接口。
 * 当 {@link CustomMember} 的 value 是 CustomProperty 时，
 * 桥接层在每次访问时调用 {@link #get(GraaljsContext)} 获取最新值。
 */
public interface CustomProperty {
    Object get(GraaljsContext cx);
}
