package cn.qihuang02.graaljs.core;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraaljsModuleTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldRequireRelativeModule() throws IOException {
        writeScript("startup_scripts/main.js", """
                var utils = require('./utils');
                result = utils.greet('world');
                """);
        writeScript("startup_scripts/utils.js", """
                exports.greet = function(name) {
                    return 'hello ' + name;
                };
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("hello world", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldRequireSharedModule() throws IOException {
        writeScript("startup_scripts/main.js", """
                var api = require('shared/api');
                result = api.version();
                """);
        writeScript("modules/shared/api.js", """
                exports.version = function() {
                    return '1.0.0';
                };
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("1.0.0", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldCacheModuleOnRepeatedRequire() throws IOException {
        writeScript("startup_scripts/main.js", """
                var a = require('./counter');
                a.increment();
                a.increment();
                var b = require('./counter');
                result = b.count();
                """);
        writeScript("startup_scripts/counter.js", """
                var count = 0;
                exports.increment = function() { count++; };
                exports.count = function() { return count; };
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals(2, bindings.getMember("result").asInt());
        context.close();
    }

    @Test
    void shouldSupportNestedRequire() throws IOException {
        writeScript("startup_scripts/main.js", """
                var facade = require('./facade');
                result = facade.run();
                """);
        writeScript("startup_scripts/facade.js", """
                var helper = require('./lib/helper');
                exports.run = function() {
                    return 'facade:' + helper.help();
                };
                """);
        writeScript("startup_scripts/lib/helper.js", """
                exports.help = function() {
                    return 'helped';
                };
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("facade:helped", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldAutoAppendJsExtension() throws IOException {
        writeScript("startup_scripts/main.js", """
                var mod = require('./noext');
                result = mod.value;
                """);
        writeScript("startup_scripts/noext.js", """
                exports.value = 'auto-appended';
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("auto-appended", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldExposeFilenameAndDirname() throws IOException {
        writeScript("startup_scripts/main.js", """
                var info = require('./info');
                resultFilename = info.filename;
                resultDirname = info.dirname;
                """);
        writeScript("startup_scripts/info.js", """
                exports.filename = __filename;
                exports.dirname = __dirname;
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        String filename = bindings.getMember("resultFilename").asString();
        String dirname = bindings.getMember("resultDirname").asString();
        assertTrue(filename.endsWith("info.js"), "filename should end with info.js, got: " + filename);
        assertTrue(dirname.contains("startup_scripts"), "dirname should contain startup_scripts, got: " + dirname);
        context.close();
    }

    @Test
    void shouldRejectPathTraversal() throws IOException {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        assertThrows(RuntimeException.class, () ->
                context.eval("traversal.js", "require('../../etc/passwd');")
        );
        context.close();
    }

    @Test
    void shouldThrowOnMissingModule() throws IOException {
        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.create(ScriptType.STARTUP);

        assertThrows(RuntimeException.class, () ->
                context.eval("missing.js", "require('./nonexistent');")
        );
        context.close();
    }

    @Test
    void shouldSupportModuleExportsReassignment() throws IOException {
        writeScript("startup_scripts/main.js", """
                var MyClass = require('./myclass');
                result = MyClass('test');
                """);
        writeScript("startup_scripts/myclass.js", """
                module.exports = function(name) {
                    return 'constructed:' + name;
                };
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("constructed:test", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldClearCacheOnContextClose() throws IOException {
        writeScript("startup_scripts/main.js", """
                var mod = require('./cached');
                result = mod.value;
                """);
        writeScript("startup_scripts/cached.js", """
                exports.value = 'cached-value';
                """);

        TestFactory factory = new TestFactory(tempDir);
        GraaljsContext context = factory.createAndLoad(ScriptType.STARTUP);
        assertNotNull(context.getModuleLoader());
        assertTrue(context.getModuleLoader().getCache().size() > 0);

        context.close();
        assertEquals(0, context.getModuleLoader().getCache().size());
    }

    @Test
    void shouldHandleCircularDependency() throws IOException {
        writeScript("startup_scripts/main.js", """
                var a = require('./a');
                resultA = a.name;
                resultFromB = a.fromB;
                """);
        writeScript("startup_scripts/a.js", """
                exports.name = 'moduleA';
                var b = require('./b');
                exports.fromB = b.name;
                """);
        writeScript("startup_scripts/b.js", """
                var a = require('./a');
                exports.name = 'moduleB';
                exports.fromA = a.name;
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("moduleA", bindings.getMember("resultA").asString());
        assertEquals("moduleB", bindings.getMember("resultFromB").asString());
        context.close();
    }

    @Test
    void shouldResolveIndexJs() throws IOException {
        writeScript("startup_scripts/main.js", """
                var lib = require('./lib');
                result = lib.value;
                """);
        writeScript("startup_scripts/lib/index.js", """
                exports.value = 'from-index';
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("from-index", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldRequireJsonModule() throws IOException {
        writeScript("startup_scripts/main.js", """
                var config = require('./config.json');
                resultName = config.name;
                resultVersion = config.version;
                """);
        writeScript("startup_scripts/config.json", """
                {"name": "test-mod", "version": "1.0.0"}
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("test-mod", bindings.getMember("resultName").asString());
        assertEquals("1.0.0", bindings.getMember("resultVersion").asString());
        context.close();
    }

    @Test
    void shouldResolvePackageJsonMainField() throws IOException {
        writeScript("startup_scripts/main.js", """
                var lib = require('./mylib');
                result = lib.value;
                """);
        writeScript("startup_scripts/mylib/package.json", """
                {"name": "mylib", "main": "entry.js"}
                """);
        writeScript("startup_scripts/mylib/entry.js", """
                exports.value = 'from-package-main';
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("from-package-main", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldFallbackToIndexJsWhenPackageJsonHasNoMain() throws IOException {
        writeScript("startup_scripts/main.js", """
                var lib = require('./mylib');
                result = lib.value;
                """);
        writeScript("startup_scripts/mylib/package.json", """
                {"name": "mylib", "version": "1.0.0"}
                """);
        writeScript("startup_scripts/mylib/index.js", """
                exports.value = 'from-index-fallback';
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("from-index-fallback", bindings.getMember("result").asString());
        context.close();
    }

    @Test
    void shouldHandleCircularDependencyWithPartialExports() throws IOException {
        writeScript("startup_scripts/main.js", """
                var b = require('./b');
                resultBFromA = b.fromA;
                """);
        writeScript("startup_scripts/a.js", """
                exports.earlyExport = 'early';
                var b = require('./b');
                exports.lateExport = 'late';
                """);
        writeScript("startup_scripts/b.js", """
                var a = require('./a');
                exports.fromA = a.earlyExport;
                """);

        GraaljsContext context = createAndLoad();
        Value bindings = context.getPolyglotContext().getBindings("js");
        assertEquals("early", bindings.getMember("resultBFromA").asString());
        context.close();
    }

    @Test
    void shouldExtractJsonStringField() {
        assertEquals("lib/main.js", ModuleLoader.extractJsonStringField(
                "{\"name\": \"mylib\", \"main\": \"lib/main.js\", \"version\": \"1.0\"}", "main"));
        assertEquals("mylib", ModuleLoader.extractJsonStringField(
                "{\"name\": \"mylib\"}", "name"));
        assertNull(ModuleLoader.extractJsonStringField(
                "{\"name\": \"mylib\"}", "main"));
        assertNull(ModuleLoader.extractJsonStringField(
                "{}", "main"));
    }

    private GraaljsContext createAndLoad() {
        TestFactory factory = new TestFactory(tempDir);
        return factory.createAndLoad(ScriptType.STARTUP);
    }

    private void writeScript(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static class TestFactory extends GraaljsContextFactory {
        TestFactory(Path scriptRoot) {
            super(scriptRoot);
        }

        @Override
        protected void configureBindings(ScriptType type, Map<String, Object> bindings) {
            super.configureBindings(type, bindings);
        }
    }
}
