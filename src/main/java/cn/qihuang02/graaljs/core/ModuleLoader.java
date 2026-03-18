package cn.qihuang02.graaljs.core;

import cn.qihuang02.graaljs.Graaljs;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * CommonJS 风格的模块加载器。
 * <p>
 * 支持 {@code require('./relative')} 和 {@code require('shared')} 两种解析方式，
 * 每个模块只执行一次，后续调用返回缓存的 {@code module.exports}。
 */
public class ModuleLoader {
    private static final String MODULE_WRAPPER_PREFIX =
            "(function(exports, require, module, __filename, __dirname) {\n";
    private static final String MODULE_WRAPPER_SUFFIX =
            "\n});";

    private final GraaljsContext context;
    private final Path scriptRoot;
    private final Path modulesRoot;
    private final Map<Path, Value> cache;
    private final ThreadLocal<Deque<Path>> scriptPathStack;

    public ModuleLoader(GraaljsContext context, Path scriptRoot) {
        this.context = context;
        this.scriptRoot = scriptRoot.normalize();
        this.modulesRoot = this.scriptRoot.resolve("modules").normalize();
        this.cache = new HashMap<>();
        this.scriptPathStack = ThreadLocal.withInitial(ArrayDeque::new);
    }

    /**
     * 供 JS 侧 {@code require()} 调用的入口。
     */
    public Object require(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("require() argument must be a non-empty string");
        }

        Path callerDir = currentScriptDirectory();
        Path resolved = resolve(moduleId, callerDir);

        Value cachedModule = cache.get(resolved);
        if (cachedModule != null) {
            return cachedModule.getMember("exports");
        }

        return loadModule(resolved);
    }

    /**
     * 解析模块路径。
     *
     * @param moduleId  模块标识符
     * @param callerDir 调用者脚本所在目录（用于相对路径解析）
     * @return 规范化后的绝对路径
     */
    Path resolve(String moduleId, Path callerDir) {
        Path resolved;
        if (moduleId.startsWith("./") || moduleId.startsWith("../")) {
            // 相对路径：基于调用者目录解析
            if (callerDir == null) {
                callerDir = scriptRoot;
            }
            resolved = callerDir.resolve(moduleId).normalize();
        } else {
            // 共享模块：从 modules/ 目录解析
            resolved = modulesRoot.resolve(moduleId).normalize();
        }

        // 如果已经是 .js 或 .json 文件，直接使用
        String fileName = resolved.getFileName().toString();
        if (!fileName.endsWith(".js") && !fileName.endsWith(".json")) {
            // 尝试作为文件加 .js 后缀
            Path withJs = resolved.resolveSibling(fileName + ".js");
            if (Files.isRegularFile(withJs)) {
                resolved = withJs;
            } else if (Files.isDirectory(resolved)) {
                // 尝试 index.js
                Path indexJs = resolved.resolve("index.js");
                if (Files.isRegularFile(indexJs)) {
                    resolved = indexJs;
                } else {
                    // 回退到加 .js 后缀（会在 loadModule 中报错）
                    resolved = withJs;
                }
            } else {
                resolved = withJs;
            }
        }

        // 安全校验：路径不能逃逸出 scriptRoot
        if (!resolved.startsWith(scriptRoot)) {
            throw new SecurityException(
                    "Module path escapes script root: " + moduleId + " -> " + resolved);
        }

        return resolved;
    }

    /**
     * 执行模块并缓存 exports。
     */
    Value loadModule(Path modulePath) {
        if (!Files.isRegularFile(modulePath)) {
            throw new IllegalArgumentException("Module not found: " + modulePath);
        }

        String code;
        try {
            code = Files.readString(modulePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read module: " + modulePath, e);
        }

        // 创建 module 和 exports 对象
        Value moduleObj = context.getPolyglotContext().eval("js", "({ exports: {} })");

        // 在执行前先缓存 module 对象，以支持循环依赖
        cache.put(modulePath, moduleObj);

        // JSON 模块：直接解析并赋值给 module.exports
        if (modulePath.toString().endsWith(".json")) {
            Value parsed = context.getPolyglotContext().eval("js",
                    "JSON.parse(" + jsonStringLiteral(code) + ")");
            moduleObj.putMember("exports", parsed);
            return moduleObj.getMember("exports");
        }

        // 包装为 CommonJS 模块函数
        String wrapped = MODULE_WRAPPER_PREFIX + code + MODULE_WRAPPER_SUFFIX;
        Source source;
        try {
            source = Source.newBuilder("js", wrapped, modulePath.getFileName().toString()).build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build module source: " + modulePath, e);
        }

        Value exportsObj = moduleObj.getMember("exports");

        String filename = modulePath.toString().replace('\\', '/');
        String dirname = modulePath.getParent().toString().replace('\\', '/');

        // 执行包装函数
        pushScriptPath(modulePath);
        try {
            Value wrapperFn = context.getPolyglotContext().eval(source);
            wrapperFn.execute(
                    exportsObj,
                    (org.graalvm.polyglot.proxy.ProxyExecutable) args -> {
                        if (args.length == 0 || !args[0].isString()) {
                            throw new IllegalArgumentException("require() argument must be a string");
                        }
                        return require(args[0].asString());
                    },
                    moduleObj,
                    filename,
                    dirname
            );
        } finally {
            popScriptPath();
        }

        // 返回 module.exports（支持整体替换）
        return moduleObj.getMember("exports");
    }

    /**
     * 将字符串转为 JS 字符串字面量（用于 JSON.parse 参数）。
     */
    private static String jsonStringLiteral(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * 压入当前正在执行的脚本路径。
     */
    public void pushScriptPath(Path path) {
        scriptPathStack.get().push(path);
    }

    /**
     * 弹出当前脚本路径。
     */
    public void popScriptPath() {
        Deque<Path> stack = scriptPathStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * 获取当前脚本所在目录。
     */
    public Path currentScriptDirectory() {
        Deque<Path> stack = scriptPathStack.get();
        if (stack.isEmpty()) {
            return scriptRoot;
        }
        return stack.peek().getParent();
    }

    /**
     * 清空模块缓存。
     */
    public void close() {
        cache.clear();
    }

    // 仅用于测试
    Map<Path, Value> getCache() {
        return cache;
    }
}
