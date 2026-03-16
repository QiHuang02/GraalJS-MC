package cn.qihuang02.graaljs.binding;

import com.mojang.logging.LogUtils;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ConsoleAPI {
    private final Logger logger;

    public ConsoleAPI() {
        this.logger = LogUtils.getLogger();
    }

    public ConsoleAPI(Logger logger) {
        this.logger = logger;
    }

    @HostAccess.Export
    public void log(Object... args) {
        logger.info(formatArgs(args));
    }

    @HostAccess.Export
    public void info(Object... args) {
        logger.info(formatArgs(args));
    }

    @HostAccess.Export
    public void warn(Object... args) {
        logger.warn(formatArgs(args));
    }

    @HostAccess.Export
    public void error(Object... args) {
        logger.error(formatArgs(args));
    }

    @HostAccess.Export
    public void debug(Object... args) {
        logger.debug(formatArgs(args));
    }

    private String formatArgs(Object... args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
                .map(arg -> arg == null ? "null" : arg.toString())
                .collect(Collectors.joining(" "));
    }
}
