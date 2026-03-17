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

        Value cached = cache.get(resolved);
        if (cached != null) {
            return cached;
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

        // 自动补 .js 后缀
        if (!resolved.toString().endsWith(".js")) {
            resolved = resolved.resolveSibling(resolved.getFileName().toString() + ".js");
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

        // 包装为 CommonJS 模块函数
        String wrapped = MODULE_WRAPPER_PREFIX + code + MODULE_WRAPPER_SUFFIX;
        Source source;
        try {
            source = Source.newBuilder("js", wrapped, modulePath.getFileName().toString()).build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build module source: " + modulePath, e);
        }

        // 创建 module 和 exports 对象
        Value bindings = context.getPolyglotContext().getBindings("js");
        Value moduleObj = context.getPolyglotContext().eval("js", "({ exports: {} })");
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

        // 缓存 module.exports（支持整体替换）
        Value result = moduleObj.getMember("exports");
        cache.put(modulePath, result);
        return result;
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
