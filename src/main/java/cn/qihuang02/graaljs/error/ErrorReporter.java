package cn.qihuang02.graaljs.error;

import cn.qihuang02.graaljs.core.GraaljsContext;

/**
 * 统一错误报告接口。
 */
public interface ErrorReporter {
    void warning(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset);

    void error(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset, Throwable cause);

    RuntimeException runtimeError(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset, Throwable cause);
}
