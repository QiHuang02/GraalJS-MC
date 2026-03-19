package cn.qihuang02.graaljs.error;

/**
 * JS 脚本执行错误。
 * 包装 GraalVM PolyglotException 或其他脚本层面的错误。
 */
public class ScriptException extends GraaljsException {
    private final String sourceName;
    private final int line;
    private final int column;

    public ScriptException(String message) {
        this(message, null, null, -1, -1);
    }

    public ScriptException(String message, Throwable cause) {
        this(message, cause, null, -1, -1);
    }

    public ScriptException(String message, Throwable cause, String sourceName, int line, int column) {
        super(message, cause);
        this.sourceName = sourceName;
        this.line = line;
        this.column = column;
    }

    public String getSourceName() {
        return sourceName;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getMessage());
        if (sourceName != null) {
            sb.append(" @ ").append(sourceName);
            if (line > 0) {
                sb.append(':').append(line);
                if (column > 0) {
                    sb.append(':').append(column);
                }
            }
        }
        return sb.toString();
    }
}
