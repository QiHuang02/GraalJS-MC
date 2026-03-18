package cn.qihuang02.graaljs.error;

import cn.qihuang02.graaljs.Graaljs;
import cn.qihuang02.graaljs.core.GraaljsContext;
import org.slf4j.Logger;

/**
 * 默认基于日志的错误报告实现。
 */
public class LoggingErrorReporter implements ErrorReporter {
    private final Logger logger;

    public LoggingErrorReporter() {
        this(Graaljs.LOGGER);
    }

    public LoggingErrorReporter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void warning(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset) {
        logger.warn(format(context, message, sourceName, line, lineSource, lineOffset));
    }

    @Override
    public void error(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset, Throwable cause) {
        logger.error(format(context, message, sourceName, line, lineSource, lineOffset), cause);
    }

    @Override
    public RuntimeException runtimeError(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset, Throwable cause) {
        String formatted = format(context, message, sourceName, line, lineSource, lineOffset);
        return new IllegalStateException(formatted, cause);
    }

    private String format(GraaljsContext context, String message, String sourceName, int line, String lineSource, int lineOffset) {
        StringBuilder builder = new StringBuilder("[").append(context.getType().directory).append("] ").append(message);
        if (sourceName != null && !sourceName.isBlank()) {
            builder.append(" @ ").append(sourceName);
        }
        if (line > 0) {
            builder.append(':').append(line);
            if (lineOffset > 0) {
                builder.append(':').append(lineOffset);
            }
        }
        if (lineSource != null && !lineSource.isBlank()) {
            builder.append(" -> ").append(lineSource.strip());
        }
        // 附加上下文信息：当前脚本路径栈
        if (context.getModuleLoader() != null) {
            java.nio.file.Path currentScript = context.getModuleLoader().currentScriptPath();
            if (currentScript != null && (sourceName == null || !currentScript.toString().equals(sourceName))) {
                builder.append(" [currentScript=").append(currentScript).append(']');
            }
        }
        return builder.toString();
    }
}
