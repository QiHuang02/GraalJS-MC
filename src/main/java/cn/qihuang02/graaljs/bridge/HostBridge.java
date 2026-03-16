package cn.qihuang02.graaljs.bridge;

import cn.qihuang02.graaljs.core.GraaljsContext;

/**
 * Java 值到脚本值的桥接器。
 */
public interface HostBridge {
    boolean supports(Object value);

    Object toJs(GraaljsContext context, Object value);
}
