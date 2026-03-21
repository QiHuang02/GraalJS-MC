package cn.qihuang02.graaljs.core;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CommonJS 风格的模块加载器。
 * <p>
 * 支持 {@code require('./relative')} 和 {@code require('shared')} 两种解析方式，
 * 每个模块只执行一次，后续调用返回缓存的 {@code module.exports}。
 */
public class ModuleLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleLoader.class);
    private static final String MODULE_WRAPPER_PREFIX =
            "(function(exports, require, module, __filename, __dirname) {\n";
    private static final String MODULE_WRAPPER_SUFFIX =
            "\n});";

    private final GraaljsContext context;
    private final Path scriptRoot;
    private final Path modulesRoot;
    private final Map<Path, Value> cache;
    private final Set<Path> loading;
    private final ThreadLocal<Deque<Path>> scriptPathStack;

    public ModuleLoader(GraaljsContext context, Path scriptRoot) {
        this.context = context;
        this.scriptRoot = scriptRoot.normalize();
        this.modulesRoot = this.scriptRoot.resolve("modules").normalize();
        this.cache = new HashMap<>();
        this.loading = new HashSet<>();
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
            // 循环依赖检测：模块在缓存中但仍在加载中，返回部分初始化的 exports
            if (loading.contains(resolved)) {
                LOGGER.warn("Circular dependency detected: {} requires {} (returning partially initialized exports)",
                        currentScriptPath(), resolved);
            }
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
                // 先检查 package.json 的 "main" 字段
                Path packageJson = resolved.resolve("package.json");
                Path mainEntry = resolvePackageMain(packageJson, resolved);
                if (mainEntry != null && Files.isRegularFile(mainEntry)) {
                    resolved = mainEntry;
                } else {
                    // 回退到 index.js
                    Path indexJs = resolved.resolve("index.js");
                    if (Files.isRegularFile(indexJs)) {
                        resolved = indexJs;
                    } else {
                        // 回退到加 .js 后缀（会在 loadModule 中报错）
                        resolved = withJs;
                    }
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
        loading.add(modulePath);

        // JSON 模块：直接解析并赋值给 module.exports
        if (modulePath.toString().endsWith(".json")) {
            Value parsed = context.getPolyglotContext().eval("js",
                    "JSON.parse(" + jsonStringLiteral(code) + ")");
            moduleObj.putMember("exports", parsed);
            loading.remove(modulePath);
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
            loading.remove(modulePath);
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
     * 获取当前正在执行的脚本路径（完整路径）。
     *
     * @return 当前脚本路径，若栈为空则返回 null
     */
    public Path currentScriptPath() {
        Deque<Path> stack = scriptPathStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 清空模块缓存。
     */
    public void close() {
        cache.clear();
        loading.clear();
    }

    /**
     * 从 package.json 中解析 "main" 字段指定的入口文件。
     *
     * @param packageJsonPath package.json 的路径
     * @param packageDir      包目录
     * @return 解析后的入口文件路径，若不存在或无 main 字段则返回 null
     */
    private Path resolvePackageMain(Path packageJsonPath, Path packageDir) {
        if (!Files.isRegularFile(packageJsonPath)) {
            return null;
        }
        try {
            String content = Files.readString(packageJsonPath);
            // 简单解析 "main" 字段，避免引入 JSON 库依赖
            String mainValue = extractJsonStringField(content, "main");
            if (mainValue == null || mainValue.isBlank()) {
                return null;
            }
            Path mainPath = packageDir.resolve(mainValue).normalize();
            // 如果 main 指向的路径没有扩展名，尝试加 .js
            if (!mainPath.getFileName().toString().contains(".")) {
                Path withJs = mainPath.resolveSibling(mainPath.getFileName().toString() + ".js");
                if (Files.isRegularFile(withJs)) {
                    return withJs;
                }
            }
            return mainPath;
        } catch (IOException e) {
            LOGGER.warn("Failed to read package.json: {}", packageJsonPath, e);
            return null;
        }
    }

    /**
     * 从 JSON 字符串中提取指定字段的字符串值（简单实现，不依赖 JSON 库）。
     */
    static String extractJsonStringField(String json, String fieldName) {
        // 匹配 "fieldName" : "value" 或 "fieldName": "value"
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) {
            return null;
        }
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) {
            return null;
        }
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return null;
        }
        return json.substring(quoteStart + 1, quoteEnd);
    }

    // 仅用于测试
    Map<Path, Value> getCache() {
        return cache;
    }
}
